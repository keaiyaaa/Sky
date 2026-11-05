package com.example.msdksample.data.remote

import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class BackendAccountApiTest {
    private val json = Gson()
    private fun registry() = BackendSessionRegistry { 1000L }.also { it.install("1", "alice", "opaque-token", 2000) }
    private fun api(registry: BackendSessionRegistry, handler: (Request) -> Pair<Int, String>): BackendAccountApi {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val (code, body) = handler(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("test")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        return BackendAccountApi("https://school.example/api/v1/", registry, client)
    }
    private val profile = CloudProfile("1", "alice", "昵称", "", "", 1, "2026-11-04T02:00:00Z")
    private val device = CloudDevice("42", "client-a", "设备", "M30", "SN-A", "1", "DJI_SDK", 1,
        "2026-11-04T02:00:00Z", "2026-11-04T02:00:00Z", false)

    @Test fun profileUsesBearerAndExpectedVersionWithoutOwnerParameters() = runBlocking {
        val sessions = registry()
        val api = api(sessions) { request ->
            assertEquals("https://school.example/api/v1/me", request.url.toString())
            assertEquals("Bearer opaque-token", request.header("Authorization"))
            assertEquals("PATCH", request.method)
            val buffer = Buffer(); request.body!!.writeTo(buffer)
            val body = json.fromJson(buffer.readUtf8(), com.google.gson.JsonObject::class.java)
            assertEquals(1, body["expectedVersion"].asInt)
            assertFalse(body.has("userId"))
            200 to json.toJson(profile.copy(version = 2))
        }
        assertEquals(2L, api.updateProfile(sessions.current()!!, ProfileUpdate("昵称", "", "", 1)).version)
    }

    @Test fun expiredOrReplacedSessionCannotAcceptLateResponse() = runBlocking {
        val sessions = registry()
        val old = sessions.current()!!
        val api = api(sessions) {
            sessions.install("2", "bob", "new-token", 3000)
            200 to json.toJson(profile)
        }
        try { api.profile(old); fail("Old result must be rejected") } catch (_: StaleAccountResult) { }
        assertEquals("2", sessions.current()!!.userId)
    }

    @Test fun unauthorizedResponseInvalidatesOnlyItsCurrentLease() = runBlocking {
        val sessions = registry()
        val lease = sessions.current()!!
        val api = api(sessions) { 401 to "{\"code\":\"UNAUTHENTICATED\"}" }
        try { api.profile(lease); fail() } catch (error: AccountApiFailure) { assertEquals(401, error.status) }
        assertNull(sessions.current())
    }

    @Test fun conflictAndDatabaseFailureAreNotReportedAsSuccess() = runBlocking {
        for ((status, code) in listOf(409 to "VERSION_CONFLICT", 503 to "SERVICE_UNAVAILABLE")) {
            val sessions = registry()
            val api = api(sessions) { status to "{\"code\":\"$code\"}" }
            try { api.profile(sessions.current()!!); fail() }
            catch (error: AccountApiFailure) { assertEquals(code, error.code) }
            assertNotNull(sessions.current())
        }
    }

    @Test fun deviceRetryKeepsTheExactIdempotencyKeyAndClientId() = runBlocking {
        val sessions = registry()
        val body = json.toJson(DeviceCreate("client-a", "设备", "M30", "SN-A", "1", idempotencyKey = "stable-key-123456"))
        val bodies = mutableListOf<String>()
        val api = api(sessions) { request ->
            val buffer = Buffer(); request.body!!.writeTo(buffer); bodies += buffer.readUtf8()
            201 to json.toJson(device)
        }
        repeat(2) { api.writeDevice(sessions.current()!!, "POST", "devices", body) }
        assertEquals(listOf(body, body), bodies)
    }

    @Test fun pagingAndDeleteUseThePublishedFieldNames() = runBlocking {
        val sessions = registry()
        val api = api(sessions) { request ->
            if (request.method == "GET") {
                assertEquals("1", request.url.queryParameter("page")); assertEquals("20", request.url.queryParameter("size"))
                200 to json.toJson(DevicePage(listOf(device), 1, 20, 21))
            } else {
                assertEquals("DELETE", request.method)
                assertEquals("/api/v1/devices/42", request.url.encodedPath)
                200 to json.toJson(device.copy(deleted = true, version = 2))
            }
        }
        assertEquals(21L, api.devices(sessions.current()!!, 1).total)
        assertTrue(api.writeDevice(sessions.current()!!, "DELETE", "devices/42", json.toJson(DeviceDelete(1, "stable-key-123456"))).deleted)
    }

    @Test fun invalidResponsesAreRejectedBeforeCaching() = runBlocking {
        val sessions = registry()
        val api = api(sessions) { 200 to json.toJson(profile.copy(userId = "other")) }
        try { api.profile(sessions.current()!!); fail() } catch (_: java.io.IOException) { }
    }

    @Test fun incompleteResponsesAndNumericIdsCannotConfirmAPendingWrite() = runBlocking {
        for (body in listOf("{\"id\":\"42\"}",
            json.toJson(device).replace("\"id\":\"42\"", "\"id\":42"))) {
            val sessions = registry()
            val api = api(sessions) { 200 to body }
            try { api.writeDevice(sessions.current()!!, "POST", "devices", "{}"); fail() }
            catch (_: java.io.IOException) { }
        }
    }

    @Test fun onlyHttpsServiceRootsAreAccepted() {
        for (url in listOf("http://school.example/api/v1", "https://school.example/api/v1?x=1",
            "https://name:secret@school.example/api/v1", "https://school.example/wrong")) {
            try { BackendAccountApi.validateBaseUrl(url); fail(url) } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun sessionExpiryAndTokenRedactionAreDeterministic() {
        var now = 1000L
        val sessions = BackendSessionRegistry { now }
        sessions.install("1", "alice", "secret", 2000)
        val lease = sessions.current()!!
        assertFalse(lease.toString().contains("secret"))
        now = 2000
        assertNull(sessions.current()); assertFalse(sessions.isCurrent(lease))
    }

    @Test fun profileLimitsCountUnicodeCharactersAndOptionalEmailCanBeCleared() {
        assertTrue(AccountFieldRules.validText("😀".repeat(20), 20, true))
        assertFalse(AccountFieldRules.validText("😀".repeat(21), 20, true))
        assertFalse(AccountFieldRules.validText("a\nb", 20, true))
        assertTrue(AccountFieldRules.validEmail("")); assertFalse(AccountFieldRules.validEmail("bad"))
    }
}
