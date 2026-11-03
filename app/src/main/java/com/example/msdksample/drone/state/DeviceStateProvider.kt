package com.example.msdksample.drone.state

import java.util.ArrayDeque

enum class DeviceMode { OFFLINE, DEVICE, DJI_SIMULATOR, LOCAL_DEMO }
enum class SdkRegistrationState { NOT_INITIALIZED, INITIALIZING, REGISTERING, REGISTERED, REGISTER_FAILED }
enum class LinkState { DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING, ERROR }
enum class Readiness { UNAVAILABLE, CONNECTING, READY, DEGRADED, ERROR }
enum class TelemetrySource { DEVICE, DJI_SIMULATOR, LOCAL_DEMO }
enum class DeviceAction { PHOTO, VIDEO, TAKEOFF, LANDING, RETURN_HOME, HOVER }
enum class TelemetryField { LATITUDE, LONGITUDE, ALTITUDE, DISTANCE, HORIZONTAL_SPEED, HEADING, BATTERY, SIGNAL, FLYING }

data class DeviceIdentity(val productId: Int, val productType: String?, val firmwareVersion: String?, val serialNumber: String?)
data class TelemetryValue<T>(val value: T? = null, val source: TelemetrySource? = null,
    val observedAtEpochMs: Long? = null, val observedAtMonotonicMs: Long? = null,
    val valid: Boolean = false, val unavailableReason: String? = "NOT_OBSERVED")
data class TelemetrySnapshot(val generation: Long, val receivedAtEpochMs: Long, val receivedAtMonotonicMs: Long,
    val latitudeDeg: TelemetryValue<Double> = TelemetryValue(),
    val longitudeDeg: TelemetryValue<Double> = TelemetryValue(),
    val altitudeMeters: TelemetryValue<Double> = TelemetryValue(),
    val distanceMeters: TelemetryValue<Double> = TelemetryValue(),
    val horizontalSpeedMetersPerSecond: TelemetryValue<Double> = TelemetryValue(),
    val headingDegrees: TelemetryValue<Double> = TelemetryValue(),
    val batteryPercent: TelemetryValue<Int> = TelemetryValue(),
    val signalPercent: TelemetryValue<Int> = TelemetryValue(),
    val flying: TelemetryValue<Boolean> = TelemetryValue(),
    val altitudeReference: String? = null, val distanceReference: String? = null) {
    fun field(field: TelemetryField): TelemetryValue<*> = when (field) {
        TelemetryField.LATITUDE -> latitudeDeg; TelemetryField.LONGITUDE -> longitudeDeg
        TelemetryField.ALTITUDE -> altitudeMeters; TelemetryField.DISTANCE -> distanceMeters
        TelemetryField.HORIZONTAL_SPEED -> horizontalSpeedMetersPerSecond; TelemetryField.HEADING -> headingDegrees
        TelemetryField.BATTERY -> batteryPercent; TelemetryField.SIGNAL -> signalPercent; TelemetryField.FLYING -> flying
    }
}
data class DeviceCapabilities(val generation: Long, val observedAtMonotonicMs: Long,
    val photo: Boolean? = null, val video: Boolean? = null, val takeoff: Boolean? = null,
    val landing: Boolean? = null, val returnHome: Boolean? = null, val hover: Boolean? = null) {
    fun supports(action: DeviceAction): Boolean? = when (action) {
        DeviceAction.PHOTO -> photo; DeviceAction.VIDEO -> video; DeviceAction.TAKEOFF -> takeoff
        DeviceAction.LANDING -> landing; DeviceAction.RETURN_HOME -> returnHome; DeviceAction.HOVER -> hover
    }
}
data class DeviceStateSnapshot(val generation: Long = 0, val version: Long = 0,
    val mode: DeviceMode = DeviceMode.OFFLINE,
    val sdkRegistration: SdkRegistrationState = SdkRegistrationState.NOT_INITIALIZED,
    val linkState: LinkState = LinkState.DISCONNECTED, val readiness: Readiness = Readiness.UNAVAILABLE,
    val device: DeviceIdentity? = null, val telemetry: TelemetrySnapshot? = null,
    val capabilities: DeviceCapabilities? = null, val changedAtEpochMs: Long = 0,
    val changedAtMonotonicMs: Long = 0, val errorCode: String? = null, val userMessage: String? = null)

interface DeviceStateProvider {
    fun currentSnapshot(): DeviceStateSnapshot
    fun subscribe(listener: DeviceStateListener): DeviceStateSubscription
}
fun interface DeviceStateListener { fun onSnapshot(snapshot: DeviceStateSnapshot) }
fun interface DeviceStateSubscription { fun cancel() }

/** Single immutable publication per version. Callbacks drain outside the state lock, in commit order. */
class SessionDeviceStateStore(
    private val epochMs: () -> Long = System::currentTimeMillis,
    private val monotonicMs: () -> Long = { System.nanoTime() / 1_000_000 },
    private val listenerFailure: (Throwable) -> Unit = {}
) : DeviceStateProvider {
    private val lock = Any()
    @Volatile private var snapshot = DeviceStateSnapshot(changedAtEpochMs = epochMs(), changedAtMonotonicMs = monotonicMs())
    private val subscriptions = linkedSetOf<Subscription>()
    private val events = ArrayDeque<Pair<Subscription, DeviceStateSnapshot>>()
    private var draining = false

    override fun currentSnapshot(): DeviceStateSnapshot = snapshot

    private inner class Subscription(private var listener: DeviceStateListener?) : DeviceStateSubscription {
        private var active = true
        @Synchronized fun deliver(value: DeviceStateSnapshot) {
            if (active) try { listener?.onSnapshot(value) } catch (error: Throwable) { runCatching { listenerFailure(error) } }
        }
        override fun cancel() {
            synchronized(this) { active = false; listener = null }
            synchronized(lock) {
                subscriptions.remove(this)
                events.removeAll { it.first === this }
            }
        }
    }

    override fun subscribe(listener: DeviceStateListener): DeviceStateSubscription {
        val subscription = Subscription(listener)
        val drain = synchronized(lock) {
            subscriptions.add(subscription)
            events.add(subscription to snapshot)
            startDrain()
        }
        if (drain) drain()
        return subscription
    }

    private fun startDrain(): Boolean {
        if (draining) return false
        draining = true
        return true
    }
    private fun drain() {
        while (true) {
            val event = synchronized(lock) {
                if (events.isEmpty()) { draining = false; null } else events.removeFirst()
            } ?: return
            event.first.deliver(event.second)
        }
    }

    private fun commit(expectedGeneration: Long? = null, change: (DeviceStateSnapshot) -> DeviceStateSnapshot): Boolean {
        val drain = synchronized(lock) {
            val before = snapshot
            if (expectedGeneration != null && before.generation != expectedGeneration) return false
            val next = change(before)
            if (next == before) return true
            snapshot = next.copy(version = before.version + 1, changedAtEpochMs = epochMs(), changedAtMonotonicMs = monotonicMs())
            subscriptions.forEach { events.add(it to snapshot) }
            startDrain()
        }
        if (drain) drain()
        return true
    }

    fun connection(mode: DeviceMode, registration: SdkRegistrationState, link: LinkState,
        identity: DeviceIdentity?, simulatorStateKnown: Boolean = true, forceNewSession: Boolean = false,
        expectedGeneration: Long? = null, errorCode: String? = null, message: String? = null): Boolean = commit(expectedGeneration) { before ->
        val newSession = forceNewSession || before.mode != mode || before.linkState != link
        val generation = before.generation + if (newSession) 1 else 0
        val readiness = when {
            link == LinkState.ERROR -> Readiness.ERROR
            link == LinkState.DISCONNECTED -> Readiness.UNAVAILABLE
            link != LinkState.CONNECTED -> Readiness.CONNECTING
            mode == DeviceMode.OFFLINE -> Readiness.UNAVAILABLE
            mode == DeviceMode.DEVICE && registration != SdkRegistrationState.REGISTERED -> Readiness.DEGRADED
            mode == DeviceMode.DEVICE && (!simulatorStateKnown || identity?.serialNumber.isNullOrBlank() || identity?.productType.isNullOrBlank()) -> Readiness.DEGRADED
            else -> Readiness.READY
        }
        before.copy(generation = generation, mode = mode, sdkRegistration = registration, linkState = link,
            readiness = readiness, device = if (link == LinkState.CONNECTED) identity else null,
            telemetry = if (newSession || link != LinkState.CONNECTED) null else before.telemetry,
            capabilities = if (newSession || link != LinkState.CONNECTED) null else before.capabilities,
            errorCode = errorCode ?: if (mode == DeviceMode.DEVICE && !simulatorStateKnown) "SDK_SIMULATOR_STATE_UNKNOWN" else null,
            userMessage = message)
    }

    fun telemetry(value: TelemetrySnapshot): Boolean = commit(value.generation) { before ->
        if (value.receivedAtMonotonicMs > monotonicMs() || value.receivedAtMonotonicMs <
            (before.telemetry?.receivedAtMonotonicMs ?: Long.MIN_VALUE)) return@commit before
        val expectedSource = when (before.mode) {
            DeviceMode.DEVICE -> TelemetrySource.DEVICE; DeviceMode.DJI_SIMULATOR -> TelemetrySource.DJI_SIMULATOR
            DeviceMode.LOCAL_DEMO -> TelemetrySource.LOCAL_DEMO; DeviceMode.OFFLINE -> null
        }
        if (expectedSource == null || before.linkState != LinkState.CONNECTED) return@commit before
        // Reject a whole update if it attempts to mix provenance or use invalid observations.
        val fields = TelemetryField.values().map { value.field(it) }
        if (fields.any { it.source != null && it.source != expectedSource }) return@commit before
        fun valid(field: TelemetryField): Boolean {
            val item = value.field(field)
            if (!item.valid) return item.value == null
            val time = item.observedAtMonotonicMs ?: return false
            if (item.value == null || item.source != expectedSource || item.observedAtEpochMs == null || time > monotonicMs()) return false
            val previous = before.telemetry?.field(field)?.observedAtMonotonicMs
            if (previous != null && time < previous) return false
            val number = (item.value as? Number)?.toDouble()
            if (number != null && !number.isFinite()) return false
            return when (field) {
                TelemetryField.LATITUDE -> number != null && number in -90.0..90.0
                TelemetryField.LONGITUDE -> number != null && number in -180.0..180.0
                TelemetryField.HEADING -> number != null && number >= 0 && number < 360
                TelemetryField.BATTERY, TelemetryField.SIGNAL -> number != null && number in 0.0..100.0
                TelemetryField.DISTANCE -> number != null && number >= 0 && value.distanceReference != null
                TelemetryField.HORIZONTAL_SPEED -> number != null && number >= 0
                TelemetryField.ALTITUDE -> number != null && value.altitudeReference != null
                TelemetryField.FLYING -> item.value is Boolean
            }
        }
        if (TelemetryField.values().any { !valid(it) }) before else before.copy(telemetry = value)
    }

    fun capabilities(value: DeviceCapabilities): Boolean = commit(value.generation) { before ->
        if (before.linkState != LinkState.CONNECTED || value.observedAtMonotonicMs > monotonicMs() ||
            value.observedAtMonotonicMs < (before.capabilities?.observedAtMonotonicMs ?: Long.MIN_VALUE)) before
        else before.copy(capabilities = value)
    }

    fun failure(generation: Long, code: String, message: String): Boolean = commit(generation) {
        it.copy(readiness = Readiness.ERROR, telemetry = null, capabilities = null, errorCode = code, userMessage = message)
    }
}

data class ActionCheck(val allowed: Boolean, val errorCode: String? = null)
object DeviceActionGate {
    /** Freshness is supplied by the action owner. No UI display timeout is reused here. */
    fun check(state: DeviceStateSnapshot, generation: Long, action: DeviceAction,
              requiredFields: Map<TelemetryField, Long>?, nowMonotonicMs: Long): ActionCheck {
        fun deny(code: String) = ActionCheck(false, code)
        if (state.generation != generation) return deny("STALE_GENERATION")
        if (state.mode != DeviceMode.DEVICE) return deny(when (state.mode) {
            DeviceMode.LOCAL_DEMO -> "LOCAL_DEMO_MODE"; DeviceMode.DJI_SIMULATOR -> "SIMULATOR_MODE"
            else -> "DEVICE_DISCONNECTED"
        })
        if (state.sdkRegistration != SdkRegistrationState.REGISTERED) return deny("SDK_NOT_REGISTERED")
        if (state.linkState != LinkState.CONNECTED || state.readiness != Readiness.READY) return deny("DEVICE_NOT_READY")
        val capabilities = state.capabilities ?: return deny("CAPABILITY_UNKNOWN")
        if (capabilities.generation != generation) return deny("STALE_GENERATION")
        when (capabilities.supports(action)) { null -> return deny("CAPABILITY_UNKNOWN"); false -> return deny("CAPABILITY_UNSUPPORTED"); true -> Unit }
        if (requiredFields == null) return deny("ACTION_POLICY_UNAVAILABLE")
        for ((field, maxAge) in requiredFields) {
            val telemetry = state.telemetry ?: return deny("TELEMETRY_UNAVAILABLE")
            if (telemetry.generation != generation) return deny("STALE_GENERATION")
            val item = telemetry.field(field)
            if (!item.valid || item.value == null || item.source != TelemetrySource.DEVICE) return deny("TELEMETRY_UNAVAILABLE")
            val observed = item.observedAtMonotonicMs ?: return deny("TELEMETRY_UNAVAILABLE")
            val age = nowMonotonicMs - observed
            if (maxAge < 0 || age < 0 || age > maxAge) return deny("TELEMETRY_STALE")
        }
        return ActionCheck(true)
    }
}
