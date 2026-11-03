package com.example.msdksample.drone

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.msdksample.utils.PreferenceManager
import com.example.msdksample.data.local.LocalDataStore
import dji.sdk.keyvalue.key.DJIFlightAssistantKey
import dji.sdk.keyvalue.key.DJIKeyInfo
import dji.sdk.keyvalue.key.DJIProductKey
import dji.sdk.keyvalue.key.KeyTools
import dji.sdk.keyvalue.value.product.ProductType
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.common.register.DJISDKInitEvent
import dji.v5.manager.KeyManager
import dji.v5.manager.SDKManager
import dji.v5.manager.capability.CapabilityManager
import dji.v5.manager.interfaces.SDKManagerCallback
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import com.example.msdksample.drone.state.*
import android.os.SystemClock

/**
 * 无人机连接管理器（单例）
 *
 * 封装 DJI MSDK v5 的初始化、注册、设备自动发现与连接状态管理，
 * 并内置“模拟器模式”（仅 Debug 版可用），供无真机时开发与 UI 联调。
 *
 * 【MSDK v5 真机连接机制说明】
 * 与旧版 MSDK(V4) 不同，v5 中无人机由 SDK 自动发现：
 *   - 消费级机型（Mavic/Air/Mini 等）：手机通过 USB 连接遥控器，或直连飞机 Wi-Fi；
 *   - 行业机型（M300/M30/M350/Matrice 4 等）：通过带屏遥控器 USB / RTK / 机场自动接入。
 * SDK 注册成功后，一旦检测到产品即回调 [SDKManagerCallback.onProductConnect]，
 * 断开时回调 [SDKManagerCallback.onProductDisconnect]。因此：
 *   - [searchDevices] 等价于“查询当前 SDK 已发现的产品”，而非蓝牙式扫描；
 *   - [connectDevice] 会轮询等待产品自动上线（最长 15s），无需也无法手动配对；
 *   - [disconnectDevice] 仅作状态提示，真正断开需物理拔线/关机，断开后 SDK 自动回调。
 *
 * 【模拟器模式说明】
 * 通过 [enableSimulator] 开启后：
 *   - 即使没有真机，[isConnected] 也会返回 true，[getConnectedDevice] 返回“Simulator”；
 *   - [getBatteryPercent] / [getSignalStrength] 返回本地模拟的电量/信号值并周期性刷新；
 *   - [startTelemetry] 推送模拟高度/距离/速度，供主页 OSD 联动；
 *   - 其余对外接口行为与真机一致。
 * 本地演示不启动 DJI SDK 飞行模拟器，也不向飞机下发参数。
 *
 * 使用前需先在 [SkyPivotApplication]（Manifest 的 android:name）中调用 [initSDK]，
 * 并保证在 Application.attachBaseContext 之后才触碰任何 MSDK API。
 */
class DroneManager private constructor() {

    companion object {
        private const val TAG = "DroneManager"

        /** 自动连接等待上限（15s） */
        private const val CONNECT_WAIT_LIMIT = 30 // 30 * 500ms = 15s

        /** 本地模拟设备的 productId */
        private const val SIMULATED_PRODUCT_ID = 9999

        /** 配置/状态数据来源标注（仿 LocalNearestNeighborRouteOptimizer.PROVIDER 风格） */
        const val PROVIDER_DJI = "dji"
        const val PROVIDER_SIMULATION = "simulation"

        @Volatile
        private var instance: DroneManager? = null

        fun getInstance(): DroneManager {
            return instance ?: synchronized(this) {
                instance ?: DroneManager().also { instance = it }
            }
        }
    }

    /** SDK 生命周期状态 */
    enum class SdkState {
        /** 尚未初始化 */
        NOT_INITIALIZED,
        /** SDK 正在初始化 */
        INITIALIZING,
        /** 正在注册 App */
        REGISTERING,
        /** 注册成功（可用状态） */
        REGISTERED,
        /** 注册失败 */
        REGISTER_FAILED
    }

    /**
     * 运行时模式（决定避障等功能走哪条执行链）。
     *
     * 注意：不能直接用 [isConnected] 判断“真机已连接”——模拟器激活时
     * [isConnected] 同样返回 true。判定顺序：模拟器激活 → SIM_ONLY；
     * 真实 SDK 产品已连接 → REAL；否则 → OFFLINE。
     */
    enum class RuntimeMode {
        /** 真机已连接（走 DJI SDK 真实 Key） */
        REAL,

        /** 仅模拟器激活（走本地模拟，不下发真实 Key） */
        SIM_ONLY,

        /** 未连接（真实 Key 与模拟都不具备，仅提示） */
        OFFLINE
    }

    enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED, FAILED }
    enum class DemoScenario { NORMAL, CONNECTING, CONNECTION_FAILED, MISSING_DATA, INVALID_DATA, SYNC_FAILED, DISCONNECTED }

    /** 当前连接到的无人机信息 */
    data class DroneDevice(
        val productId: Int,
        val productType: String = "未知",
        val firmwareVersion: String = "",
        val serialNumber: String = ""
    ) {
        fun summary(): String =
            "$productType (productId=$productId${if (serialNumber.isNotBlank()) ", SN=$serialNumber" else ""})"
    }

    /** 连接状态监听接口，供 UI 及其他模块订阅 */
    interface ConnectionStateListener {
        fun onSdkStateChanged(state: SdkState, message: String?)
        fun onConnectionChanged(connected: Boolean, device: DroneDevice?)
        fun onDeviceInfoUpdated(device: DroneDevice?)

        /** 模拟器遥测（电量/信号），仅在模拟器模式周期回调（默认空实现，可不覆写） */
        fun onSimulatorTelemetry(batteryPercent: Int, signalPercent: Int) {}
        fun onConnectionStatusChanged(status: ConnectionStatus, message: String?) {}
    }

    fun interface TelemetryListener { fun onTelemetryChanged(telemetry: FlightTelemetry) }

    private val telemetryListeners = CopyOnWriteArrayList<TelemetryListener>()
    @Volatile private var telemetry = FlightTelemetry()
    @Volatile private var sdkMessage: String? = null
    @Volatile private var connectionError: String? = null
    @Volatile private var demoScenario = DemoScenario.NORMAL
    private var demoTick = 0
    private val connectionRevision = AtomicLong(0)
    private val contractState = SessionDeviceStateStore(monotonicMs = SystemClock::elapsedRealtime,
        listenerFailure = { Log.e(TAG, "状态订阅者处理失败", it) })
    val deviceStateProvider: DeviceStateProvider get() = contractState
    @Volatile private var djiSimulatorState: Boolean? = null
    private val sdkSimulatorChangePending = AtomicBoolean(false)
    fun isDjiSdkSimulatorEnabled(): Boolean? = djiSimulatorState

    private fun syncContractState(forceNewSession: Boolean = false, expectedGeneration: Long? = null): Boolean {
        val mode = when {
            simulatorActive -> DeviceMode.LOCAL_DEMO
            connected && djiSimulatorState == true -> DeviceMode.DJI_SIMULATOR
            connected -> DeviceMode.DEVICE
            else -> DeviceMode.OFFLINE
        }
        val link = when (getConnectionStatus()) {
            ConnectionStatus.CONNECTED -> LinkState.CONNECTED
            ConnectionStatus.CONNECTING -> LinkState.CONNECTING
            ConnectionStatus.FAILED -> LinkState.ERROR
            ConnectionStatus.DISCONNECTED -> LinkState.DISCONNECTED
        }
        val device = getConnectedDevice()?.let { DeviceIdentity(it.productId,
            it.productType.takeUnless { name -> name.isBlank() || name == "未知" || name.equals("UNKNOWN", true) },
            it.firmwareVersion.takeIf(String::isNotBlank), it.serialNumber.takeIf(String::isNotBlank)) }
        return contractState.connection(mode, SdkRegistrationState.valueOf(sdkState.name), link, device,
            simulatorStateKnown = djiSimulatorState != null, forceNewSession = forceNewSession,
            expectedGeneration = expectedGeneration,
            errorCode = if (link == LinkState.ERROR) {
                if (simulatorActive) "LOCAL_DEMO_CONNECTION_FAILED" else "DEVICE_CONNECTION_FAILED"
            } else null, message = getConnectionMessage())
    }

    /** No verified real capability/freshness policy has been supplied yet; fail closed. */
    fun checkDeviceAction(action: DeviceAction, generation: Long): ActionCheck = DeviceActionGate.check(
        contractState.currentSnapshot(), generation, action, null, SystemClock.elapsedRealtime())

    fun requestDjiSimulator(enabled: Boolean, callback: (Boolean, String?) -> Unit) {
        if (Looper.myLooper() != Looper.getMainLooper()) { mainHandler.post { requestDjiSimulator(enabled, callback) }; return }
        if (!connected || !registered || simulatorActive) {
            callback(false, "SDK 模拟器切换需要已注册的物理连接，且不能处于本地演示"); return
        }
        if (!sdkSimulatorChangePending.compareAndSet(false, true)) {
            callback(false, "SDK_SIMULATOR_CHANGE_PENDING"); return
        }
        djiSimulatorState = null
        syncContractState(forceNewSession = true)
        val generation = contractState.currentSnapshot().generation
        val completed = AtomicBoolean(false)
        lateinit var timeout: Runnable
        fun complete(success: Boolean, message: String?) { mainHandler.post {
            if (!completed.compareAndSet(false, true)) return@post
            mainHandler.removeCallbacks(timeout)
            sdkSimulatorChangePending.set(false)
            if (generation != contractState.currentSnapshot().generation) {
                callback(false, "STALE_GENERATION：模拟器结果属于旧会话")
            } else {
                djiSimulatorState = if (success) enabled else null
                syncContractState(forceNewSession = true)
                callback(success, message)
            }
        } }
        timeout = Runnable { complete(false, "SDK_SIMULATOR_TIMEOUT") }
        mainHandler.postDelayed(timeout, 15_000)
        DjiSimulatorAdapter.change(enabled, ::complete)
    }
    fun getConnectionRevision(): Long = connectionRevision.get()
    private var sdkReady = false
    private val initCallbacks = CopyOnWriteArrayList<(Boolean, String?) -> Unit>()

    fun getSdkMessage(): String? = sdkMessage
    fun getConnectionStatus(): ConnectionStatus = when {
        simulatorActive && demoScenario == DemoScenario.DISCONNECTED -> ConnectionStatus.DISCONNECTED
        simulatorActive && demoScenario == DemoScenario.CONNECTING -> ConnectionStatus.CONNECTING
        simulatorActive && demoScenario == DemoScenario.CONNECTION_FAILED -> ConnectionStatus.FAILED
        simulatorActive || connected -> ConnectionStatus.CONNECTED
        connectPolling.get() -> ConnectionStatus.CONNECTING
        connectionError != null -> ConnectionStatus.FAILED
        else -> ConnectionStatus.DISCONNECTED
    }

    fun getConnectionMessage(): String = when (getConnectionStatus()) {
        ConnectionStatus.CONNECTED -> when {
            simulatorActive -> "本地演示设备已连接"
            djiSimulatorState == true -> "DJI SDK 模拟器已连接"
            djiSimulatorState == null -> "设备链路已连接，SDK 模拟器状态待确认"
            else -> "设备链路已连接"
        }
        ConnectionStatus.CONNECTING -> if (simulatorActive) "演示：连接中" else "等待 SDK 发现设备（最长15秒）"
        ConnectionStatus.FAILED -> if (simulatorActive) "演示：连接失败，可重新连接" else connectionError ?: "连接失败"
        ConnectionStatus.DISCONNECTED -> "未连接设备"
    }

    fun getTelemetry(): FlightTelemetry = telemetry.displayAt(System.currentTimeMillis())
    fun addTelemetryListener(listener: TelemetryListener) {
        telemetryListeners.addIfAbsent(listener)
        listener.onTelemetryChanged(getTelemetry())
    }
    fun removeTelemetryListener(listener: TelemetryListener) { telemetryListeners.remove(listener) }

    private fun publishTelemetry(value: FlightTelemetry) {
        telemetry = value.validated()
        val state = contractState.currentSnapshot()
        if (state.mode == DeviceMode.LOCAL_DEMO && value.source == FlightTelemetry.Source.LOCAL_DEMO) {
            val epoch = value.updatedAtMillis
            val mono = if (epoch == null) null else SystemClock.elapsedRealtime()
            fun <T> observation(item: T?): TelemetryValue<T> = TelemetryValue(item, TelemetrySource.LOCAL_DEMO,
                epoch, mono, item != null && epoch != null, if (item == null) "MISSING_OR_INVALID" else null)
            contractState.telemetry(TelemetrySnapshot(state.generation, System.currentTimeMillis(), SystemClock.elapsedRealtime(),
                altitudeMeters = observation(telemetry.altitudeMeters?.toDouble()),
                distanceMeters = observation(telemetry.distanceMeters?.toDouble()),
                horizontalSpeedMetersPerSecond = observation(telemetry.speedMetersPerSecond?.toDouble()),
                batteryPercent = observation(telemetry.batteryPercent), signalPercent = observation(telemetry.signalPercent),
                altitudeReference = "LOCAL_DEMO_ONLY", distanceReference = "LOCAL_DEMO_ONLY"))
        }
        mainHandler.removeCallbacks(telemetryStaleCheck)
        if (telemetry.updatedAtMillis != null) {
            mainHandler.postDelayed(telemetryStaleCheck, FlightTelemetry.STALE_AFTER_MILLIS + 1)
        }
        telemetryListeners.forEach { listener -> runCatching { listener.onTelemetryChanged(telemetry) } }
    }

    private fun resetTelemetryForConnection() {
        publishTelemetry(FlightTelemetry(source = when {
            !isConnected() -> FlightTelemetry.Source.OFFLINE
            simulatorActive -> FlightTelemetry.Source.LOCAL_DEMO
            else -> FlightTelemetry.Source.DJI
        }))
    }

    private fun recordEvent(action: String, result: String, detail: String = "",
                            source: String = if (simulatorActive) "本地演示" else "设备连接") {
        val context = appContext ?: return
        runCatching { LocalDataStore.getInstance(context).record("连接", action, result,
            source, detail) }
            .onFailure { Log.e(TAG, "保存连接日志失败", it) }
    }

    // ── SDK / 连接状态 ──────────────────────────────────────────

    @Volatile
    private var sdkState = SdkState.NOT_INITIALIZED

    @Volatile
    private var registered = false

    /** 真实 SDK 连接标志（由 SDK 回调维护） */
    @Volatile
    private var connected = false

    @Volatile
    private var productId = -1

    @Volatile
    private var lastDevice: DroneDevice? = null

    /** 模拟器模式激活标志（开启后对外呈现已连接） */
    @Volatile
    private var simulatorActive = false

    // ── 模拟遥测数据 ────────────────────────────────────────────
    @Volatile
    private var simulatedBattery = 100
    @Volatile
    private var simulatedSignal = 100
    @Volatile
    private var simulatedAltitude = 8.0f
    @Volatile
    private var simulatedSpeed = 0.0f
    @Volatile
    private var simulatedDistance = 0.0f
    private var simTelemetryRunning = false

    /** 遥测回调（由调用方通过 startTelemetry 注册） */
    @Volatile
    private var altitudeCb: ((Float) -> Unit)? = null
    @Volatile
    private var distanceCb: ((Float) -> Unit)? = null
    @Volatile
    private var speedCb: ((Float) -> Unit)? = null

    /** 防止重复执行 SDKManager.init */
    private val sdkInitStarted = AtomicBoolean(false)

    private var appContext: Context? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val telemetryStaleCheck = Runnable {
        val visible = getTelemetry()
        if (visible != telemetry) publishTelemetry(visible)
    }
    private val initTimeout = Runnable {
        if (sdkState == SdkState.INITIALIZING || sdkState == SdkState.REGISTERING) {
            finishSdkRegistration(false, "SDK 初始化或注册超时，请检查网络与配置后重试")
        }
    }

    private val stateListeners = CopyOnWriteArrayList<ConnectionStateListener>()

    // ── 自动连接轮询 ────────────────────────────────────────────
    private val connectPolling = AtomicBoolean(false)
    private var connectPollTimes = 0
    private var pendingConnectCallback: ((Boolean, String?) -> Unit)? = null

    private val connectPollRunnable = object : Runnable {
        override fun run() {
            if (connected) {
                val cb = pendingConnectCallback
                pendingConnectCallback = null
                connectPolling.set(false)
                cb?.invoke(true, "无人机已连接 (productId=$productId)")
                return
            }
            if (connectPollTimes++ >= CONNECT_WAIT_LIMIT) {
                val cb = pendingConnectCallback
                pendingConnectCallback = null
                connectPolling.set(false)
                connectionError = "等待超时，未检测到无人机，请检查设备供电和连接方式"
                notifyConnectionChanged(false, null)
                recordEvent("等待连接", "失败", connectionError.orEmpty())
                cb?.invoke(
                    false,
                    "等待超时，未检测到无人机。\n请确认遥控器/无人机已通电，" +
                            "并通过 USB 线连接手机，或保持手机连接飞机 Wi-Fi。"
                )
                return
            }
            mainHandler.postDelayed(this, 500)
        }
    }

    // ============================================================
    //  SDK 初始化 / 注册
    // ============================================================

    /**
     * 初始化并注册 DJI SDK。
     *
     * 必须在主线程调用（Application.onCreate 已满足），同一进程只应执行一次；
     * 重复调用会被忽略并回调当前状态。
     *
     * @param context ApplicationContext
     * @param onComplete 初始化/注册最终结果回调（可能为 null）
     */
    fun initSDK(context: Context, onComplete: ((Boolean, String?) -> Unit)? = null) {
        if (Looper.myLooper() != Looper.getMainLooper()) { mainHandler.post { initSDK(context, onComplete) }; return }
        appContext = context.applicationContext
        if (!DjiSdkRuntime.supported) {
            sdkState = SdkState.REGISTER_FAILED
            notifySdkStateChanged(sdkState, DjiSdkRuntime.UNAVAILABLE_MESSAGE)
            onComplete?.invoke(false, DjiSdkRuntime.UNAVAILABLE_MESSAGE)
            return
        }
        if (sdkState == SdkState.REGISTERED) {
            onComplete?.invoke(true, "SDK 已注册成功")
            return
        }
        if (sdkState == SdkState.REGISTER_FAILED && sdkInitStarted.get() && !sdkReady) {
            onComplete?.invoke(false, sdkMessage ?: "SDK 尚未完成初始化，请稍后重试")
            return
        }
        onComplete?.let { initCallbacks.add(it) }
        if (sdkState == SdkState.REGISTER_FAILED && sdkReady) {
            sdkState = SdkState.REGISTERING
            notifySdkStateChanged(sdkState, "正在重新注册 DJI SDK...")
            mainHandler.postDelayed(initTimeout, 30_000)
            try { SDKManager.getInstance().registerApp() }
            catch (t: Throwable) { finishSdkRegistration(false, "SDK 注册异常: ${t.message}") }
            return
        }
        if (sdkInitStarted.compareAndSet(false, true)) {
            sdkState = SdkState.INITIALIZING
            notifySdkStateChanged(SdkState.INITIALIZING, "正在初始化 DJI SDK...")
            mainHandler.postDelayed(initTimeout, 30_000)
            try {
                // v5 的 App Key 从 Manifest meta-data 读取，无需传入 apiKey。
                // 注意：必须捕获 Throwable——SDK 类加载/native 库缺失/初始化器异常时
                // 会抛 UnsatisfiedLinkError、ExceptionInInitializerError 等 Error，
                // 若只捕获 Exception，Error 会逃逸到 Application.onCreate 导致启动闪退。
                SDKManager.getInstance().init(context.applicationContext, sdkCallback)
                Log.i(TAG, "SDKManager.init() 已调用，等待初始化完成")
            } catch (t: Throwable) {
                Log.e(TAG, "SDK init 异常: ${t.message}", t)
                sdkInitStarted.set(false)
                finishSdkRegistration(false, "SDK 初始化异常: ${t.message}")
            }
        } else {
            // Initialization is asynchronous: queued callers receive its final result.
            notifySdkStateChanged(sdkState, sdkMessage ?: "SDK 正在初始化/注册中")
        }
    }

    private fun finishSdkRegistration(success: Boolean, message: String) {
        mainHandler.removeCallbacks(initTimeout)
        registered = success
        sdkState = if (success) SdkState.REGISTERED else SdkState.REGISTER_FAILED
        notifySdkStateChanged(sdkState, message)
        val callbacks = initCallbacks.toList()
        initCallbacks.removeAll(callbacks.toSet())
        callbacks.forEach { callback -> runCatching { callback(success, message) } }
        recordEvent("SDK 注册", if (success) "成功" else "失败", message, "设备连接")
    }

    /** SDK 回调（真实 MSDK v5） */
    private val sdkCallback: SDKManagerCallback by lazy { object : SDKManagerCallback {
        override fun onInitProcess(event: DJISDKInitEvent?, totalProcess: Int) {
            mainHandler.post {
            Log.i(TAG, "onInitProcess: event=$event progress=$totalProcess")
            if (event == DJISDKInitEvent.INITIALIZE_COMPLETE) {
                sdkReady = true
                sdkState = SdkState.REGISTERING
                notifySdkStateChanged(SdkState.REGISTERING, "SDK 初始化完成，正在注册 App...")
                try {
                    SDKManager.getInstance().registerApp()
                } catch (t: Throwable) {
                    Log.e(TAG, "registerApp 异常: ${t.message}", t)
                    finishSdkRegistration(false, "SDK 注册异常: ${t.message}")
                }
            }
            }
        }

        override fun onRegisterSuccess() {
            mainHandler.post {
            Log.i(TAG, "DJI SDK 注册成功")
            finishSdkRegistration(true, "DJI SDK 注册成功")
            // 若注册前产品已物理连接，补齐一次信息查询
            if (connected) {
                refreshDeviceInfo()
            }
            }
        }

        override fun onRegisterFailure(error: IDJIError?) {
            mainHandler.post {
            val msg = error?.description() ?: "未知错误"
            Log.e(TAG, "DJI SDK 注册失败: $msg")
            finishSdkRegistration(false, "SDK 注册失败: $msg")
            }
        }

        override fun onProductConnect(productId: Int) {
            Log.i(TAG, "无人机已连接 productId=$productId")
            mainHandler.post { handleProductConnected(productId) }
        }

        override fun onProductDisconnect(productId: Int) {
            Log.w(TAG, "无人机已断开 productId=$productId")
            mainHandler.post { handleProductDisconnected(productId) }
        }

        override fun onProductChanged(productId: Int) {
            mainHandler.post {
            Log.i(TAG, "产品变更 productId=$productId")
            this@DroneManager.productId = productId
            lastDevice = null
            if (connected) {
                djiSimulatorState = null
                syncContractState(forceNewSession = true)
                notifyConnectionChanged(isConnected(), getConnectedDevice())
                refreshDeviceInfo()
            }
            }
        }

        override fun onDatabaseDownloadProgress(current: Long, total: Long) {
            val pct = if (total > 0) (current * 100 / total) else 0
            Log.i(TAG, "SDK 数据库下载进度: $current/$total ($pct%)")
        }
    } }

    // ============================================================
    //  对外查询接口
    // ============================================================

    /** 当前 SDK 生命周期状态 */
    fun getSdkState(): SdkState = sdkState

    /** SDK 是否已初始化（无论注册成败） */
    fun isSdkInitialized(): Boolean = sdkState != SdkState.NOT_INITIALIZED

    /** App 是否已在大疆服务器注册成功 */
    fun isRegistered(): Boolean = registered

    /** 是否已连接无人机（模拟器激活时也返回 true） */
    fun isConnected(): Boolean = getConnectionStatus() == ConnectionStatus.CONNECTED

    /** 当前连接的无人机信息（模拟器激活时返回“Simulator”，未连接时为 null） */
    fun getConnectedDevice(): DroneDevice? =
        if (!isConnected()) null else if (simulatorActive) simulatedDevice() else lastDevice

    /** 当前是否处于模拟器模式 */
    fun isSimulatorEnabled(): Boolean = simulatorActive

    /**
     * 当前运行时模式（真机 REAL / 仅模拟器 SIM_ONLY / 离线 OFFLINE）。
     * 注意：isConnected() 在模拟器激活时同样为 true，不能作为真机判据，
     * 此处以 simulatorActive 优先，其次真实 SDK 连接。
     */
    fun getRuntimeMode(): RuntimeMode = when {
        simulatorActive -> if (isConnected()) RuntimeMode.SIM_ONLY else RuntimeMode.OFFLINE
        connected && djiSimulatorState != true -> RuntimeMode.REAL
        else -> RuntimeMode.OFFLINE
    }

    /** 读取用户当前避障设置（"brake"/"bypass"），数据源 SharedPreferences */
    fun getObstacleActionSetting(): String {
        val ctx = appContext ?: return "brake"
        return PreferenceManager.getInstance(ctx).getObstacleAction()
    }

    // ============================================================
    //  模拟器控制
    // ============================================================

    /**
     * 开启模拟器（仅 Debug 版可用）。
     *
     * 仅启动本地演示，电量/信号/OSD 均来自本地，不调用 DJI 模拟器。
     *
     * @param latitude       模拟起飞纬度
     * @param longitude      模拟起飞经度
     * @param satelliteCount 模拟卫星数
     */
    fun enableSimulator(
        latitude: Double = 31.2047,
        longitude: Double = 121.5809,
        satelliteCount: Int = 10,
        callback: ((Boolean, String?) -> Unit)? = null
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) { mainHandler.post { enableSimulator(latitude, longitude, satelliteCount, callback) }; return }
        if (!isDebuggable()) {
            callback?.invoke(false, "模拟器仅可在 Debug 版中使用")
            return
        }
        if (simulatorActive) {
            callback?.invoke(true, "本地演示已开启：${getConnectionMessage()}")
            return
        }

        cancelConnectionWait()
        // 激活本地模拟连接，不触碰 DJI SDK 模拟器。
        simulatorActive = true
        demoScenario = DemoScenario.NORMAL
        demoTick = 0
        simulatedBattery = 100
        simulatedSignal = 100
        simulatedAltitude = 8.0f
        simulatedSpeed = 0.0f
        simulatedDistance = 0.0f
        startSimTelemetryLoop()
        notifyConnectionChanged(true, simulatedDevice())
        recordEvent("开启本地演示", "成功")
        notifySimulatorTelemetry()
        Log.i(TAG, "模拟器模式已激活")
        callback?.invoke(true, "本地演示已开启（非真机连接）")
    }

    /**
     * 关闭模拟器（仅 Debug 版可用），恢复为真实 SDK 连接状态。
     */
    fun disableSimulator(callback: ((Boolean, String?) -> Unit)? = null) {
        if (Looper.myLooper() != Looper.getMainLooper()) { mainHandler.post { disableSimulator(callback) }; return }
        if (!isDebuggable()) {
            callback?.invoke(false, "模拟器仅可在 Debug 版中使用")
            return
        }
        if (!simulatorActive) {
            callback?.invoke(true, "模拟器当前未开启")
            return
        }
        recordEvent("关闭本地演示", "成功")
        simulatorActive = false
        demoScenario = DemoScenario.NORMAL
        stopSimTelemetryLoop()
        altitudeCb = null
        distanceCb = null
        speedCb = null
        Log.i(TAG, "模拟器模式已关闭")
        // 通知恢复到真实连接状态
        notifyConnectionChanged(connected, lastDevice)
        callback?.invoke(true, "模拟器已关闭")
    }

    /** Reproducible offline scenarios for UI and settings verification; Debug only. */
    fun setDemoScenario(scenario: DemoScenario, callback: (Boolean, String?) -> Unit) {
        if (Looper.myLooper() != Looper.getMainLooper()) { mainHandler.post { setDemoScenario(scenario, callback) }; return }
        if (!isDebuggable() || !simulatorActive) {
            callback(false, "请先在 Debug 版开启本地演示")
            return
        }
        demoScenario = scenario
        notifyConnectionChanged(isConnected(), getConnectedDevice())
        recordEvent("演示场景", if (scenario == DemoScenario.CONNECTION_FAILED) "失败" else "成功", scenario.name)
        callback(true, "已切换演示场景")
    }

    /** 当前模拟电量百分比（模拟器未开启时返回 -1） */
    fun getBatteryPercent(): Int = getTelemetry().batteryPercent ?: -1

    /** 当前模拟信号强度百分比（模拟器未开启时返回 -1） */
    fun getSignalStrength(): Int = getTelemetry().signalPercent ?: -1

    // ============================================================
    //  设备搜索 / 连接 / 断开
    // ============================================================

    /**
     * 搜索设备。
     *
     * MSDK v5 自动发现产品，这里返回 SDK 当前已发现的无人机列表：
     * 已连接 → 返回 1 台设备（含型号/固件/序列号）；未连接 → 返回空列表并提示接线方式。
     * 模拟器模式下直接返回模拟设备。
     */
    fun searchDevices(callback: (List<DroneDevice>, String?) -> Unit) {
        if (simulatorActive) {
            if (isConnected()) callback(listOf(simulatedDevice()), null)
            else callback(emptyList(), getConnectionMessage())
            return
        }
        if (!registered) {
            callback(emptyList(), "SDK 尚未注册成功，无法搜索设备（请先点击“初始化”并等待注册成功）")
            return
        }
        if (!connected) {
            callback(
                emptyList(),
                "暂未发现已连接的无人机。请按所用机型与 DJI SDK 的连接要求完成设备连接后刷新；本页面只查询 SDK 已发现的设备。"
            )
            return
        }
        // 已连接：刷新设备信息并返回
        val generation = contractState.currentSnapshot().generation
        refreshDeviceInfo { device ->
            if (generation != contractState.currentSnapshot().generation) {
                callback(emptyList(), "STALE_GENERATION")
                return@refreshDeviceInfo
            }
            val d = device ?: DroneDevice(productId, "未知")
            callback(listOf(d), null)
        }
    }

    /**
     * 连接设备。
     *
     * 由于 v5 为自动连接，这里会轮询等待产品自动上线（最长 15s）。
     * 模拟器模式下直接返回已连接。
     *
     * @param deviceId 预留参数（v5 无手动连接 ID，仅作接口预留）
     */
    fun connectDevice(deviceId: Int? = null, onResult: (Boolean, String?) -> Unit) {
        if (Looper.myLooper() != Looper.getMainLooper()) { mainHandler.post { connectDevice(deviceId, onResult) }; return }
        if (simulatorActive) {
            demoScenario = DemoScenario.NORMAL
            notifyConnectionChanged(true, simulatedDevice())
            recordEvent("演示重新连接", "成功")
            onResult(true, "模拟器模式：Simulator 已连接")
            return
        }
        if (!registered) {
            onResult(false, "SDK 尚未注册成功，请先点击“初始化”")
            return
        }
        if (connected) {
            onResult(true, "无人机已连接 (productId=$productId)")
            return
        }
        if (connectPolling.compareAndSet(false, true)) {
            connectionError = null
            connectPollTimes = 0
            pendingConnectCallback = onResult
            mainHandler.postDelayed(connectPollRunnable, 500)
            notifyStateMessage("正在等待无人机连接（最长 15s）...")
            notifyConnectionChanged(false, null)
        } else {
            onResult(false, "已有一个连接等待正在进行，请稍候")
        }
    }

    /**
     * 断开设备（提示性质）。
     *
     * MSDK v5 不提供“软件主动断开产品”的公共 API，产品断开由物理层驱动
     * （拔掉 USB / 关闭遥控器 / 关闭飞机电源），SDK 会回调 [SDKManagerCallback.onProductDisconnect]。
     */
    fun disconnectDevice(onResult: (Boolean, String?) -> Unit) {
        if (Looper.myLooper() != Looper.getMainLooper()) { mainHandler.post { disconnectDevice(onResult) }; return }
        if (simulatorActive) { disableSimulator(onResult); return }
        if (!isConnected()) {
            onResult(true, "当前未连接无人机")
            return
        }
        onResult(
            false,
            "DJI MSDK v5 不支持软件主动断开。\n请直接拔掉 USB 线或关闭遥控器/飞机电源，SDK 会自动更新连接状态。"
        )
    }

    // ============================================================
    //  内部：状态处理
    // ============================================================

    private fun handleProductConnected(pid: Int) {
        val newSession = !connected || productId != pid
        connected = true
        if (newSession) { lastDevice = null; djiSimulatorState = null }
        connectionError = null
        productId = pid
        syncContractState(forceNewSession = newSession)
        connectPolling.set(false)
        mainHandler.removeCallbacks(connectPollRunnable)
        val callback = pendingConnectCallback
        pendingConnectCallback = null
        callback?.invoke(true, "无人机已连接")
        notifyConnectionChanged(isConnected(), getConnectedDevice())
        recordEvent("设备连接", "成功", "productId=$pid", "设备连接")
        refreshDeviceInfo()
    }

    private fun handleProductDisconnected(pid: Int) {
        if (productId != pid) return
        connected = false
        djiSimulatorState = null
        productId = -1
        lastDevice = null
        connectPolling.set(false)
        // Even a physical disconnect hidden behind LOCAL_DEMO invalidates old SDK requests.
        syncContractState(forceNewSession = true)
        mainHandler.removeCallbacks(connectPollRunnable)
        val callback = pendingConnectCallback
        pendingConnectCallback = null
        callback?.invoke(false, "等待期间设备已断开")
        notifyConnectionChanged(isConnected(), getConnectedDevice())
        recordEvent("设备断开", "成功", source = "设备连接")
    }

    fun cancelConnectionWait() {
        if (Looper.myLooper() != Looper.getMainLooper()) { mainHandler.post { cancelConnectionWait() }; return }
        mainHandler.removeCallbacks(connectPollRunnable)
        connectPolling.set(false)
        val callback = pendingConnectCallback
        pendingConnectCallback = null
        callback?.invoke(false, "已取消等待连接")
        connectionError = null
        if (simulatorActive && demoScenario == DemoScenario.CONNECTING) demoScenario = DemoScenario.DISCONNECTED
        notifyConnectionChanged(isConnected(), getConnectedDevice())
    }

    /** 模拟器模式下对外呈现的虚拟设备 */
    private fun simulatedDevice(): DroneDevice = DroneDevice(
        productId = SIMULATED_PRODUCT_ID,
        productType = "Simulator",
        firmwareVersion = "5.18.0 (sim)",
        serialNumber = "SIM-00000001"
    )

    /** 重新读取并通知设备信息 */
    private fun refreshDeviceInfo(after: ((DroneDevice?) -> Unit)? = null) {
        val generation = contractState.currentSnapshot().generation
        readDeviceInfo { device -> mainHandler.post {
            // Ignore a late response from a disconnected or replaced product.
            if (generation != contractState.currentSnapshot().generation) {
                after?.invoke(null)
                return@post
            }
            if (device != null && connected && device.productId == productId) {
                lastDevice = device
                syncContractState(expectedGeneration = generation)
                notifyDeviceInfoUpdated(device)
            }
            after?.invoke(device)
        } }
    }

    /**
     * 通过 KeyManager 异步读取产品类型 / 固件版本 / 序列号。
     * 任一环节失败均容忍，保证不因 SDK 状态问题崩溃。
     */
    private fun readDeviceInfo(onFinished: (DroneDevice?) -> Unit) {
        if (!registered || !connected) {
            onFinished(null)
            return
        }
        val pid = productId
        try {
            val typeKey = KeyTools.createKey(DJIProductKey.KeyProductType)
            KeyManager.getInstance().getValue(typeKey,
                object : CommonCallbacks.CompletionCallbackWithParam<ProductType> {
                    override fun onSuccess(t: ProductType?) {
                        val typeName = friendlyProductType(t)
                        readStringKey(DJIProductKey.KeyFirmwareVersion) { fw ->
                            readStringKey(DJIProductKey.KeySerialNumber) { sn ->
                                onFinished(
                                    DroneDevice(pid, typeName, fw ?: "", sn ?: "")
                                )
                            }
                        }
                    }

                    override fun onFailure(error: IDJIError) {
                        Log.w(TAG, "读取产品类型失败: ${error.description()}")
                        onFinished(DroneDevice(pid, "未知", "", ""))
                    }
                })
        } catch (e: Exception) {
            Log.w(TAG, "readDeviceInfo 异常: ${e.message}")
            onFinished(DroneDevice(pid, "未知", "", ""))
        }
    }

    private fun readStringKey(
        keyInfo: DJIKeyInfo<String>,
        cb: (String?) -> Unit
    ) {
        try {
            val key = KeyTools.createKey(keyInfo)
            KeyManager.getInstance().getValue(key,
                object : CommonCallbacks.CompletionCallbackWithParam<String> {
                    override fun onSuccess(t: String?) {
                        cb(t)
                    }

                    override fun onFailure(error: IDJIError) {
                        cb(null)
                    }
                })
        } catch (e: Exception) {
            cb(null)
        }
    }

    /** 设置一个 Boolean 类型的 DJI Key；成功回调 (true, null)，失败回调 (false, 错误信息) */
    private fun setBooleanKey(
        keyInfo: DJIKeyInfo<Boolean>,
        value: Boolean,
        onResult: (Boolean, String?) -> Unit
    ) {
        val generation = contractState.currentSnapshot().generation
        fun result(success: Boolean, message: String?) { mainHandler.post {
            if (generation != contractState.currentSnapshot().generation) onResult(false, "STALE_GENERATION")
            else onResult(success, message)
        } }
        try {
            val key = KeyTools.createKey(keyInfo)
            KeyManager.getInstance().setValue(key, value,
                object : CommonCallbacks.CompletionCallback {
                    override fun onSuccess() {
                        result(true, null)
                    }

                    override fun onFailure(error: IDJIError) {
                        result(false, error.description())
                    }
                })
        } catch (e: Exception) {
            Log.w(TAG, "setBooleanKey($value) 异常: ${e.message}")
            onResult(false, e.message)
        }
    }

    /** 将 SDK ProductType 翻译为可读名称 */
    private fun friendlyProductType(type: ProductType?): String {
        if (type == null) return "未知"
        // A switch over the SDK enum would initialize a shared Kotlin WhenMappings class
        // even for local connection enums, forcing SDK classes to load in offline mode.
        return when (type.name) {
            "UNKNOWN", "UNRECOGNIZED" -> "未知"
            "DJI_MAVIC_3" -> "Mavic 3"
            "DJI_MAVIC_3_ENTERPRISE_SERIES" -> "Mavic 3 行业系列"
            "DJI_MINI_3_PRO" -> "Mini 3 Pro"
            "DJI_MINI_4_PRO" -> "Mini 4 Pro"
            "DJI_MINI_3" -> "Mini 3"
            "MAVIC_MINI" -> "Mini (初代)"
            "DJI_AIR_2S" -> "Air 2S"
            "MAVIC_AIR_2" -> "Air 2"
            "DJI_MATRICE_4_SERIES" -> "Matrice 4 系列"
            "DJI_MATRICE_4D_SERIES" -> "Matrice 4D 系列"
            "DJI_MATRICE_400" -> "Matrice 400"
            "M300_RTK" -> "Matrice 300 RTK"
            "M350_RTK" -> "Matrice 350 RTK"
            "M30_SERIES" -> "Matrice 30 系列"
            "P4P" -> "Phantom 4 Pro"
            "MAVIC_2_ENTERPRISE" -> "Mavic 2 行业版"
            else -> type.name
        }
    }

    // ============================================================
    //  状态监听（供 UI 及其他模块订阅）
    // ============================================================

    fun addStateListener(listener: ConnectionStateListener) {
        if (!stateListeners.contains(listener)) {
            stateListeners.add(listener)
        }
        listener.onSdkStateChanged(sdkState, sdkMessage)
        listener.onConnectionChanged(isConnected(), getConnectedDevice())
        listener.onConnectionStatusChanged(getConnectionStatus(), getConnectionMessage())
    }

    fun removeStateListener(listener: ConnectionStateListener) {
        stateListeners.remove(listener)
    }

    private fun notifySdkStateChanged(state: SdkState, message: String?) {
        sdkMessage = message
        syncContractState()
        for (l in stateListeners) {
            runCatching { l.onSdkStateChanged(state, message) }
        }
    }

    private fun notifyConnectionChanged(connected: Boolean, device: DroneDevice?) {
        connectionRevision.incrementAndGet()
        syncContractState()
        resetTelemetryForConnection()
        for (l in stateListeners) {
            runCatching { l.onConnectionChanged(connected, device) }
            runCatching { l.onConnectionStatusChanged(getConnectionStatus(), getConnectionMessage()) }
        }
    }

    private fun notifyDeviceInfoUpdated(device: DroneDevice?) {
        for (l in stateListeners) {
            runCatching { l.onDeviceInfoUpdated(device) }
        }
    }

    private fun notifySimulatorTelemetry() {
        for (l in stateListeners) {
            runCatching { l.onSimulatorTelemetry(simulatedBattery, simulatedSignal) }
        }
    }

    private fun notifyStateMessage(msg: String) {
        notifySdkStateChanged(sdkState, msg)
    }

    /** 供外部 SDK 回调转发使用：标记已连接（与 SDK 回调等价） */
    fun onConnected() {
        if (Looper.myLooper() != Looper.getMainLooper()) { mainHandler.post { onConnected() }; return }
        if (!connected) {
            handleProductConnected(if (productId >= 0) productId else 0)
        }
    }

    /** 供外部 SDK 回调转发使用：标记已断开 */
    fun onDisconnected() {
        if (Looper.myLooper() != Looper.getMainLooper()) { mainHandler.post { onDisconnected() }; return }
        if (connected) {
            handleProductDisconnected(productId)
        }
    }

    // ============================================================
    //  实时遥测数据
    // ============================================================

    /**
     * 启动实时遥测数据监听。
     *
     * 模拟器模式：周期推送模拟高度/距离/速度（1s 间隔）；
     * 真机模式：待接入 DJI V5 Key 后推送真实数据（当前仅缓存回调并打日志）。
     *
     * @param onAltitude  高度回调 (单位: 米)
     * @param onDistance  距离回调 (单位: 米)
     * @param onSpeed     速度回调 (单位: 米/秒)
     */
    fun startTelemetry(
        onAltitude: (Float) -> Unit,
        onDistance: (Float) -> Unit,
        onSpeed: (Float) -> Unit
    ) {
        altitudeCb = onAltitude
        distanceCb = onDistance
        speedCb = onSpeed

        if (simulatorActive) {
            if (!simTelemetryRunning) startSimTelemetryLoop()
            return
        }
        if (!connected) {
            Log.w(TAG, "无人机未连接，无法启动遥测监听")
            return
        }
        // TODO: 真机模式接入 DJI V5 Key 的 KeyManager.listen() 后在此回调真实数据
        Log.i(TAG, "遥测监听已启动 (真机模式 V5 Key 待接入)")
    }

    /**
     * 停止遥测数据监听
     */
    fun stopTelemetry() {
        altitudeCb = null
        distanceCb = null
        speedCb = null
        if (!simulatorActive) {
            stopSimTelemetryLoop()
        }
        Log.i(TAG, "遥测监听已停止")
    }

    // ============================================================
    //  内部：模拟遥测引擎
    // ============================================================

    /** 模拟遥测周期任务：刷新电量/信号/OSD 并通知监听器 */
    private val simTelemetryRunnable = object : Runnable {
        override fun run() {
            if (!simulatorActive) {
                simTelemetryRunning = false
                return
            }
            // Fixed sequence makes reconnect/missing/invalid scenarios reproducible.
            demoTick++
            simulatedBattery = (100 - demoTick / 10).coerceAtLeast(20)
            simulatedSignal = 90 + demoTick % 11
            simulatedAltitude = 8f + demoTick % 30
            simulatedSpeed = (demoTick % 8).toFloat()
            simulatedDistance = (demoTick * 4).toFloat()

            val sample = when {
                !isConnected() -> FlightTelemetry()
                demoScenario == DemoScenario.MISSING_DATA -> FlightTelemetry(FlightTelemetry.Source.LOCAL_DEMO)
                demoScenario == DemoScenario.INVALID_DATA -> FlightTelemetry(FlightTelemetry.Source.LOCAL_DEMO,
                    Float.NaN, -10f, Float.POSITIVE_INFINITY, 130, -1, System.currentTimeMillis())
                else -> FlightTelemetry(FlightTelemetry.Source.LOCAL_DEMO, simulatedAltitude,
                    simulatedDistance, simulatedSpeed, simulatedBattery, simulatedSignal, System.currentTimeMillis())
            }
            publishTelemetry(sample)

            telemetry.altitudeMeters?.let { altitudeCb?.invoke(it) }
            telemetry.speedMetersPerSecond?.let { speedCb?.invoke(it) }
            telemetry.distanceMeters?.let { distanceCb?.invoke(it) }

            notifySimulatorTelemetry()
            mainHandler.postDelayed(this, 1000)
        }
    }

    private fun startSimTelemetryLoop() {
        if (simTelemetryRunning) return
        simTelemetryRunning = true
        mainHandler.removeCallbacks(simTelemetryRunnable)
        mainHandler.postDelayed(simTelemetryRunnable, 1000)
    }

    private fun stopSimTelemetryLoop() {
        simTelemetryRunning = false
        mainHandler.removeCallbacks(simTelemetryRunnable)
    }

    /** 是否 Debug 构建（决定模拟器是否可用） */
    private fun isDebuggable(): Boolean {
        val ctx = appContext ?: return false
        return (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    // ============================================================
    //  飞控参数设置（接口预留，接入 DJI V5 Key 后生效）
    // ============================================================

    /**
     * 设置返航高度
     *
     * @param meters 返航高度（米），范围 20-400
     */
    fun setReturnAltitude(meters: Int, callback: ((Boolean, String?) -> Unit)? = null) {
        if (meters !in 20..400) { callback?.invoke(false, "返航高度须在20至400米之间"); return }
        if (handleLocalSetting(callback)) return
        if (!checkConnection(callback)) return

        // TODO: 接入 DJI V5 Key（对应 key 以正式 SDK 文档为准），例如：
        // KeyManager.getInstance().setValue(
        //     KeyTools.createKey(FlightControllerKey.xxx),
        //     meters,
        //     completionCallback("返航高度=${meters}m", callback)
        // )

        Log.i(TAG, "返航高度=${meters}m (V5 Key 待接入)")
        callback?.invoke(false, "该功能待接入 DJI V5 Key")
    }

    /**
     * 设置失控行为
     *
     * @param action "rth"=自动返航 / "hover"=悬停 / "land"=原地降落
     */
    fun setFailsafeAction(action: String, callback: ((Boolean, String?) -> Unit)? = null) {

        val desc = mapOf("rth" to "自动返航", "hover" to "悬停", "land" to "原地降落")
        if (action !in desc) {
            callback?.invoke(false, "未知的失控行为类型: $action")
            return
        }
        if (handleLocalSetting(callback) || !checkConnection(callback)) return

        Log.i(TAG, "失控行为=${desc[action]} (V5 Key 待接入)")
        callback?.invoke(false, "该功能待接入 DJI V5 Key")
    }

    /**
     * 设置新手模式
     *
     * @param enabled true=开启新手模式, false=关闭
     */
    fun setBeginnerMode(enabled: Boolean, callback: ((Boolean, String?) -> Unit)? = null) {
        if (handleLocalSetting(callback)) return
        if (!checkConnection(callback)) return

        Log.i(TAG, "新手模式=${if (enabled) "开启" else "关闭"} (V5 Key 待接入)")
        callback?.invoke(false, "该功能待接入 DJI V5 Key")
    }

    /**
     * 设置避障行为（双轨实现）。
     *
     * 依据 [getRuntimeMode] 分流：
     *  - REAL     → 向 DJI 下发真实避障开关（[DJIFlightAssistantKey]，见 [applyObstacleActionToDji]）；
     *  - SIM_ONLY → 本地模拟成功返回，标注 provider=simulation（演示用，真机 Key 不下发）；
     *  - OFFLINE  → 明确失败提示。
     *
     * 语义：brake = 遇障刹停（总避障开、主动绕行关）；
     *       bypass = 主动绕行（总避障开 + 主动避障开；不支持时明确失败）。
     *
     * @param action "brake"=刹车悬停 / "bypass"=绕行
     */
    fun setObstacleAction(action: String, callback: ((Boolean, String?) -> Unit)? = null) {
        val desc = mapOf("brake" to "刹车悬停", "bypass" to "绕行")
        if (action !in desc) {
            callback?.invoke(false, "未知的避障行为类型: $action")
            return
        }
        if (handleLocalSetting(callback)) return

        when (getRuntimeMode()) {
            RuntimeMode.SIM_ONLY -> {
                Log.i(TAG, "避障行为=${desc[action]} 已设置 (provider=simulation)")
                callback?.invoke(true, "避障设置已生效（${desc[action]}，provider=simulation）")
            }
            RuntimeMode.OFFLINE -> {
                Log.w(TAG, "未连接无人机，避障设置未下发")
                callback?.invoke(false, "无人机未连接，无法下发避障设置")
            }
            RuntimeMode.REAL -> if (checkConnection(callback)) applyObstacleActionToDji(action, callback)
        }
    }

    /** REAL 模式：向 DJI 下发真实避障开关；不把不支持的绕行请求报告为成功。 */
    private fun applyObstacleActionToDji(
        action: String,
        callback: ((Boolean, String?) -> Unit)?
    ) {
        val capability = CapabilityManager.getInstance()
        val avoidanceEnabled = DJIFlightAssistantKey.KeyObstacleAvoidanceEnabled
        val activeAvoidance = DJIFlightAssistantKey.KeyActiveObstacleAvoidanceEnabled

        if (!capability.isKeySupported(avoidanceEnabled)) {
            callback?.invoke(false, "当前机型不支持避障功能")
            return
        }
        if (action == "bypass" && !capability.isKeySupported(activeAvoidance)) {
            callback?.invoke(false, "当前机型不支持主动绕行，所选设置未下发")
            return
        }
        // 先开启总避障开关
        setBooleanKey(avoidanceEnabled, true) { ok, err ->
            if (!ok) {
                callback?.invoke(false, "开启避障失败: $err")
                return@setBooleanKey
            }
            when (action) {
                // brake：总避障开 + 主动绕行关 → 遇障刹停
                "brake" -> setBooleanKey(activeAvoidance, false) { ok2, err2 ->
                    if (ok2) {
                        Log.i(TAG, "避障模式=刹车悬停 已下发 (provider=dji)")
                        callback?.invoke(true, "避障设置已生效（刹车悬停，provider=dji）")
                    } else {
                        callback?.invoke(false, "关闭主动绕行失败: $err2")
                    }
                }
                // bypass：总避障开 + 主动绕行开（APAS）
                else -> {
                    setBooleanKey(activeAvoidance, true) { ok2, err2 ->
                        if (ok2) {
                            Log.i(TAG, "避障模式=绕行 已下发 (provider=dji)")
                            callback?.invoke(true, "避障设置已生效（绕行，provider=dji）")
                        } else {
                            callback?.invoke(false, "开启主动绕行失败: $err2")
                        }
                    }
                }
            }
        }
    }

    /**
     * 查询飞机当前是否正在主动绕障（REAL 模式读取
     * [DJIFlightAssistantKey.KeyIsActivelyAvoidingObstacle]）。
     * 非 REAL 模式或读取失败时回调 null。
     */
    fun queryObstacleAvoidingState(callback: (Boolean?) -> Unit) {
        val generation = contractState.currentSnapshot().generation
        fun result(value: Boolean?) { mainHandler.post {
            callback(if (generation == contractState.currentSnapshot().generation) value else null)
        } }
        if (getRuntimeMode() != RuntimeMode.REAL) {
            callback(null)
            return
        }
        try {
            val key = KeyTools.createKey(DJIFlightAssistantKey.KeyIsActivelyAvoidingObstacle)
            KeyManager.getInstance().getValue(key,
                object : CommonCallbacks.CompletionCallbackWithParam<Boolean> {
                    override fun onSuccess(t: Boolean?) {
                        result(t)
                    }

                    override fun onFailure(error: IDJIError) {
                        Log.w(TAG, "查询避障状态失败: ${error.description()}")
                        result(null)
                    }
                })
        } catch (e: Exception) {
            Log.w(TAG, "查询避障状态异常: ${e.message}")
            callback(null)
        }
    }

    /**
     * 设置操控灵敏度
     *
     * @param percent 灵敏度百分比，0=最迟钝, 100=最灵敏
     */
    fun setControlSensitivity(percent: Int, callback: ((Boolean, String?) -> Unit)? = null) {
        if (percent !in 0..100) { callback?.invoke(false, "灵敏度须在0至100之间"); return }
        if (handleLocalSetting(callback)) return
        if (!checkConnection(callback)) return

        val clamped = percent.coerceIn(0, 100)

        Log.i(TAG, "操控灵敏度=${clamped}% (V5 Key 待接入)")
        callback?.invoke(false, "该功能待接入 DJI V5 Key")
    }

    // ============================================================
    //  图传参数设置（接口预留，接入 DJI V5 Key 后生效）
    // ============================================================

    /**
     * 设置视频图传画质
     *
     * @param quality "smooth"=流畅 / "hd"=高清 / "ultra"=超清 / "4k"=4K
     */
    fun setVideoQuality(quality: String, callback: ((Boolean, String?) -> Unit)? = null) {

        val desc = mapOf(
            "smooth" to "流畅", "hd" to "高清",
            "ultra" to "超清", "4k" to "4K"
        )
        if (quality !in desc) {
            callback?.invoke(false, "未知视频画质: $quality")
            return
        }
        if (handleLocalSetting(callback) || !checkConnection(callback)) return

        Log.i(TAG, "视频画质=${desc[quality]} (V5 Key 待接入)")
        callback?.invoke(false, "该功能待接入 DJI V5 Key")
    }

    /**
     * 设置航拍照片画质
     *
     * @param quality "standard"=标准 / "hd"=高清 / "ultra"=超清
     */
    fun setImageQuality(quality: String, callback: ((Boolean, String?) -> Unit)? = null) {

        val desc = mapOf("standard" to "标准", "hd" to "高清", "ultra" to "超清")
        if (quality !in desc) {
            callback?.invoke(false, "未知图片画质: $quality")
            return
        }
        if (handleLocalSetting(callback) || !checkConnection(callback)) return

        Log.i(TAG, "图片画质=${desc[quality]} (V5 Key 待接入)")
        callback?.invoke(false, "该功能待接入 DJI V5 Key")
    }

    /**
     * 设置自动同步（拍摄后自动下载到手机）
     *
     * @param enabled true=开启, false=关闭
     */
    fun setAutoSyncEnabled(enabled: Boolean, callback: ((Boolean, String?) -> Unit)? = null) {
        if (handleLocalSetting(callback)) return
        if (!checkConnection(callback)) return

        Log.i(TAG, "自动同步=${if (enabled) "开启" else "关闭"} (V5 Key 待接入)")
        callback?.invoke(false, "该功能待接入 DJI V5 Key")
    }

    /**
     * 设置低电量自动返航
     *
     * @param enabled true=低电量时自动返航, false=关闭
     */
    fun setLowBatteryReturnEnabled(enabled: Boolean, callback: ((Boolean, String?) -> Unit)? = null) {
        if (handleLocalSetting(callback)) return
        if (!checkConnection(callback)) return

        Log.i(TAG, "低电量返航=${if (enabled) "开启" else "关闭"} (V5 Key 待接入)")
        callback?.invoke(false, "该功能待接入 DJI V5 Key")
    }

    // ============================================================
    //  内部工具方法
    // ============================================================

    private fun handleLocalSetting(callback: ((Boolean, String?) -> Unit)?): Boolean {
        if (!simulatorActive) return false
        val scenario = demoScenario
        val generation = contractState.currentSnapshot().generation
        mainHandler.postDelayed({
            when {
                generation != contractState.currentSnapshot().generation || !simulatorActive || !isConnected() || demoScenario != scenario ->
                    callback?.invoke(false, "演示连接已变化，请重新同步")
                scenario == DemoScenario.SYNC_FAILED -> callback?.invoke(false, "演示：设备拒绝参数")
                else -> callback?.invoke(true, "本地演示确认，未下发飞机")
            }
        }, 300)
        return true
    }

    /** 检查连接状态（模拟器激活视为已连接），未连接则回调失败 */
    private fun checkConnection(callback: ((Boolean, String?) -> Unit)?): Boolean {
        if (!isConnected()) {
            callback?.invoke(false, "无人机未连接")
            return false
        }
        if (!simulatorActive && !registered) {
            callback?.invoke(false, "SDK 尚未注册成功")
            return false
        }
        return true
    }
}
