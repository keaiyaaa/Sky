package com.example.msdksample.data.remote

/** F24 supplies verified opaque tokens here. DemoSession/phone preferences never create cloud identity.
 * Token persistence and refresh remain owned by F24. This bridge keeps no credentials on disk.
 */
class BackendSessionRegistry(private val now: () -> Long = System::currentTimeMillis) {
    class Lease internal constructor(val userId: String, val username: String, val accessToken: String,
                                     val expiresAtMillis: Long, val generation: Long) {
        override fun toString() = "BackendSession(userId=$userId, generation=$generation)"
    }
    private var generation = 0L
    private var session: Lease? = null

    @Synchronized fun install(userId: String, username: String, accessToken: String, expiresAtMillis: Long) {
        require(userId.isNotBlank() && username.isNotBlank() && accessToken.isNotBlank())
        require(accessToken.none { it.isWhitespace() || it.isISOControl() })
        require(expiresAtMillis > now())
        session = Lease(userId, username, accessToken, expiresAtMillis, ++generation)
    }
    @Synchronized fun current(): Lease? {
        if (session?.expiresAtMillis?.let { it <= now() } == true) clear()
        return session
    }
    @Synchronized fun clear() { session = null; generation++ }
    @Synchronized fun isCurrent(lease: Lease) = current()?.generation == lease.generation
    @Synchronized fun invalidate(lease: Lease) { if (isCurrent(lease)) clear() }
}

object BackendSessions { val registry = BackendSessionRegistry() }

data class CloudProfile(val userId: String, val username: String, val nickname: String, val email: String,
                        val organization: String, val version: Long, val updatedAt: String)
data class ProfileUpdate(val nickname: String, val email: String, val organization: String, val expectedVersion: Long)
data class CloudDevice(val id: String, val clientId: String, val droneName: String, val model: String,
                       val serialNo: String, val firmwareVersion: String, val source: String, val version: Long,
                       val createdAt: String, val updatedAt: String, val deleted: Boolean)
data class DevicePage(val items: List<CloudDevice>, val page: Int, val size: Int, val total: Long)
data class DeviceCreate(val clientId: String, val droneName: String, val model: String, val serialNo: String,
                        val firmwareVersion: String, val source: String = "DJI_SDK", val expectedVersion: Long = 0,
                        val idempotencyKey: String)
data class DeviceUpdate(val droneName: String, val model: String, val firmwareVersion: String,
                        val expectedVersion: Long, val idempotencyKey: String)
data class DeviceDelete(val expectedVersion: Long, val idempotencyKey: String)

object AccountFieldRules {
    fun validText(value: String, max: Int, required: Boolean = false): Boolean =
        (!required || value.isNotBlank()) && value.codePointCount(0, value.length) <= max &&
                value.none { it.isISOControl() }
    fun validEmail(value: String) = value.isEmpty() || Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(value)
}
