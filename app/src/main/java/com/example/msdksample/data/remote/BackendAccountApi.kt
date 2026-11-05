package com.example.msdksample.data.remote

import android.content.Context
import com.example.msdksample.R
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AccountApiFailure(val status: Int, val code: String, val requestId: String = "") : IOException(
    when (code) {
        "UNAUTHENTICATED" -> "云端登录已过期，请重新登录"
        "VERSION_CONFLICT" -> "记录已被修改，请刷新后重新编辑；本地内容已保留"
        "DEVICE_ALREADY_SAVED" -> "该账号已保存此设备，请刷新账户设备列表"
        "IDEMPOTENCY_KEY_REUSED" -> "操作编号冲突，请保留待处理记录并联系维护者"
        "EVENT_ID_REUSED" -> "事件编号与已有内容冲突，已保留本机记录"
        "NOT_FOUND" -> "账户记录不存在或已移除"
        "VALIDATION_FAILED" -> "字段无效，请检查输入"
        "SERVICE_UNAVAILABLE" -> "数据服务暂不可用，请稍后重试"
        else -> "请求失败（HTTP $status），尚未确认云端保存"
    }
)
class StaleAccountResult : CancellationException("Account session changed")

class BackendAccountApi(
    baseUrl: String,
    private val sessions: BackendSessionRegistry = BackendSessions.registry,
    client: OkHttpClient = sharedClient
) {
    private val base = validateBaseUrl(baseUrl)
    private val http = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val json = Gson()

    suspend fun profile(lease: BackendSessionRegistry.Lease) =
        decode(perform(lease, "GET", "me"), CloudProfile::class.java).also {
            if (it.userId != lease.userId || it.version < 1) throw IOException("资料响应与当前账号不匹配")
        }
    suspend fun updateProfile(lease: BackendSessionRegistry.Lease, request: ProfileUpdate) =
        decode(perform(lease, "PATCH", "me", json.toJson(request)), CloudProfile::class.java).also {
            if (it.userId != lease.userId || it.version < 1) throw IOException("资料响应与当前账号不匹配")
        }
    suspend fun devices(lease: BackendSessionRegistry.Lease, page: Int) =
        decode(perform(lease, "GET", "devices", page = page), DevicePage::class.java).also {
            if (it.page != page || it.items.size > 20 || it.total < 0) throw IOException("设备分页响应无效")
        }
    suspend fun writeDevice(lease: BackendSessionRegistry.Lease, method: String, path: String, body: String) =
        decode(perform(lease, method, path, body), CloudDevice::class.java).also {
            if (it.id.isBlank() || it.clientId.isBlank() || it.version < 1 || it.source != "DJI_SDK") {
                throw IOException("设备响应无效，尚未确认保存")
            }
        }

    suspend fun settings(lease: BackendSessionRegistry.Lease) =
        CloudSettingsCodec.decode(perform(lease, "GET", "settings"), lease.userId)

    suspend fun writeSettings(lease: BackendSessionRegistry.Lease, body: String): CloudUserSettings {
        val sent = CloudSettingsCodec.json.fromJson(body, SettingsUpdate::class.java)
        return CloudSettingsCodec.decode(perform(lease, "PUT", "settings", body), lease.userId).also {
            if (!it.cloudSaved || it.version != sent.expectedVersion + 1 || it.settings != sent.settings)
                throw IOException("账户设置确认与待处理内容不匹配")
        }
    }

    suspend fun flightLogs(lease: BackendSessionRegistry.Lease,page: Int,filter: FlightLogFilter) =
        decode(perform(lease,"GET","flight-logs",page=page,query=mapOf("keyword" to filter.keyword,
            "source" to filter.source,"category" to filter.category,"from" to filter.from,"to" to filter.to)),FlightLogPage::class.java)
            .also { if(it.page!=page || it.size!=20 || it.items.size>20) throw IOException("日志分页响应无效") }
    suspend fun uploadFlightLog(lease: BackendSessionRegistry.Lease,body: String,clientId: String) =
        decode(perform(lease,"POST","flight-logs",body),CloudFlightLog::class.java)
            .also { if(it.clientId!=clientId || it.origin!="CLIENT_EVENT") throw IOException("日志确认响应不匹配") }

    private fun <T> decode(body: String, type: Class<T>): T = try {
        val root = com.google.gson.JsonParser.parseString(body).asJsonObject
        when (type) {
            CloudProfile::class.java -> {
                strings(root, "userId", "username", "nickname", "email", "organization", "updatedAt")
                integer(root, "version", 1)
            }
            CloudDevice::class.java -> deviceShape(root)
            DevicePage::class.java -> {
                integer(root, "page", 0); integer(root, "size", 1); integer(root, "total", 0)
                val items = root.getAsJsonArray("items") ?: throw IOException("设备列表缺失")
                items.forEach { deviceShape(it.asJsonObject) }
            }
            CloudFlightLog::class.java -> logShape(root)
            FlightLogPage::class.java -> {
                integer(root,"page",0); integer(root,"size",1); integer(root,"total",0)
                val available=root["missionEventsAvailable"]
                if(available==null || !available.isJsonPrimitive || !available.asJsonPrimitive.isBoolean) throw IOException("日志覆盖范围缺失")
                val items=root.getAsJsonArray("items") ?: throw IOException("日志列表缺失")
                items.forEach { logShape(it.asJsonObject) }
            }
        }
        json.fromJson(root, type) ?: throw IOException("响应为空")
    } catch (error: RuntimeException) { throw IOException("服务器响应格式无效", error) }

    private fun strings(root: JsonObject, vararg fields: String) {
        fields.forEach { field ->
            val value = root[field]
            if (value == null || !value.isJsonPrimitive || !value.asJsonPrimitive.isString) throw IOException("响应字段无效")
        }
    }
    private fun integer(root: JsonObject, field: String, minimum: Long) {
        val value = root[field]
        if (value == null || !value.isJsonPrimitive || !value.asJsonPrimitive.isNumber ||
            !Regex("[0-9]+").matches(value.asString) || value.asString.toLongOrNull()?.let { it >= minimum } != true) {
            throw IOException("响应版本或分页字段无效")
        }
    }
    private fun deviceShape(root: JsonObject) {
        strings(root, "id", "clientId", "droneName", "model", "serialNo", "firmwareVersion", "source", "createdAt", "updatedAt")
        integer(root, "version", 1)
        val deleted = root["deleted"]
        if (deleted == null || !deleted.isJsonPrimitive || !deleted.asJsonPrimitive.isBoolean) throw IOException("删除状态无效")
    }
    private fun logShape(root: JsonObject) {
        strings(root,"id","clientId","category","action","result","source","detail","taskId","occurredAt","origin")
        integer(root,"version",1)
        if(root["source"].asString !in com.example.msdksample.data.logs.FlightLogPolicy.sources ||
            root["category"].asString !in listOf("CONNECTION","SETTING","COMMAND","MISSION") ||
            root["origin"].asString !in listOf("CLIENT_EVENT","MISSION_EVENT") || root["id"].asString.isBlank()) throw IOException("日志来源无效")
        com.example.msdksample.data.logs.FlightLogPolicy.parseUtc(root["occurredAt"].asString)
    }

    private suspend fun perform(lease: BackendSessionRegistry.Lease, method: String, path: String,
                                body: String? = null, page: Int? = null, query: Map<String,String?> = emptyMap()): String {
        if (!sessions.isCurrent(lease)) throw StaleAccountResult()
        require(path == "me" || path == "settings" || path == "devices" || path=="flight-logs" || Regex("devices/[1-9][0-9]*").matches(path))
        val url = base.newBuilder().apply {
            path.split('/').forEach { addPathSegment(it) }
            if (page != null) { addQueryParameter("page", page.toString()); addQueryParameter("size", "20") }
            query.forEach { (key,value) -> if(value!=null) addQueryParameter(key,value) }
        }.build()
        val request = Request.Builder().url(url).header("Authorization", "Bearer ${lease.accessToken}")
            .header("Accept", "application/json").method(method,
                body?.toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        return suspendCancellableCoroutine { continuation ->
            val call = http.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(
                        if (sessions.isCurrent(lease)) e else StaleAccountResult())
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            if (!sessions.isCurrent(lease)) throw StaleAccountResult()
                            val content = response.body?.string().orEmpty()
                            if (!response.isSuccessful) {
                                val error = runCatching { json.fromJson(content, JsonObject::class.java) }.getOrNull()
                                val code = runCatching { error?.get("code")?.asString }.getOrNull()
                                    ?: if (response.code == 401) "UNAUTHENTICATED" else "HTTP_ERROR"
                                if (response.code == 401) sessions.invalidate(lease)
                                throw AccountApiFailure(response.code, code, response.header("X-Request-Id").orEmpty())
                            }
                            if (!sessions.isCurrent(lease)) throw StaleAccountResult()
                            if (continuation.isActive) continuation.resume(content)
                        } catch (error: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(error)
                        }
                    }
                }
            })
        }
    }

    companion object {
        private val sharedClient = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()
        fun validateBaseUrl(value: String): HttpUrl {
            val parsed = value.trim().trimEnd('/').plus('/').toHttpUrl()
            require(parsed.isHttps && parsed.username.isEmpty() && parsed.password.isEmpty() &&
                    parsed.query == null && parsed.fragment == null && parsed.encodedPath == "/api/v1/") {
                "服务地址必须是 HTTPS 的 /api/v1/ 入口"
            }
            return parsed
        }
    }
}

object BackendAccountRuntime {
    @Volatile private var configuredBaseUrl: String? = null
    fun configure(baseUrl: String) {
        val normalized = BackendAccountApi.validateBaseUrl(baseUrl).toString()
        if (configuredBaseUrl != normalized) BackendSessions.registry.clear()
        configuredBaseUrl = normalized
    }
    fun api(context: Context): BackendAccountApi {
        val base = configuredBaseUrl ?: context.getString(R.string.backend_api_base_url)
        check(base.isNotBlank()) { "云端服务尚未配置，请联系管理员" }
        return BackendAccountApi(base)
    }
}
