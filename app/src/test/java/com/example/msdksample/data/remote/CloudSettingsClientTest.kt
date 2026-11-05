package com.example.msdksample.data.remote

import com.example.msdksample.settings.UserSettings
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal

class CloudSettingsClientTest {
    private val json = CloudSettingsCodec.json
    private fun values() = AccountSettingsValues.fromLocal(UserSettings())
    private fun response(version: Long = 1) = CloudUserSettings("41", "user-settings", "1", values(), version,
        "2026-11-05T02:00:00Z", true, null)
    private fun registry() = BackendSessionRegistry { 1000 }.also { it.install("1", "alice", "fixture-token", 5000) }
    private fun api(sessions: BackendSessionRegistry, handler: (Request) -> Pair<Int, String>): BackendAccountApi {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val (status, body) = handler(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("fixture")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        return BackendAccountApi("https://school.example/api/v1", sessions, client)
    }
    @Test fun nullableHeightIsExplicitAndOtherFieldsRetainTheirWireTypes() {
        val root = JsonParser.parseString(CloudSettingsCodec.request(UserSettings(return_altitude_m = null), 0, "stable-key-000000" )).asJsonObject
        val fields = root.getAsJsonObject("settings")
        assertEquals(9, fields.size()); assertTrue(fields.has("returnAltitudeM")); assertTrue(fields["returnAltitudeM"].isJsonNull)
        assertTrue(fields["autoSyncRecords"].asJsonPrimitive.isString)
        assertTrue(fields["controlSensitivity"].asJsonPrimitive.isNumber)
        assertFalse(root.has("userId")); assertFalse(root.has("deviceApplied"))
        val decimal = CloudSettingsCodec.request(UserSettings(return_altitude_m = BigDecimal("100.2")), 1, "stable-key-000001")
        assertTrue(decimal.contains("\"returnAltitudeM\":\"100.20\""))
    }
    @Test fun defaultStateDoesNotImplyPersistenceOrAircraftApplication() {
        val defaults = response(0).copy(id = null, updatedAt = null, cloudSaved = false)
        assertEquals(defaults, CloudSettingsCodec.decode(json.toJson(defaults), "1"))
        val unset = response().copy(settings = values().copy(returnAltitudeM = null))
        assertNull(CloudSettingsCodec.decode(json.toJson(unset), "1").settings.local().return_altitude_m)
    }
    @Test fun malformedTypesMissingFieldsAndDeviceClaimsAreRejected() {
        val valid = json.toJson(response())
        val invalid = listOf(valid.replace("\"100.00\"", "100.00"), valid.replace("\"autoSyncRecords\":\"1\"", "\"autoSyncRecords\":1"),
            valid.replace("\"controlSensitivity\":5", "\"controlSensitivity\":\"5\""),
            valid.replace("\"returnAltitudeM\":\"100.00\",", ""), valid.replace("\"id\":\"41\"", "\"id\":41"),
            valid.replace("\"deviceApplied\":null", "\"deviceApplied\":true"), valid.replace("\"100.00\"", "\"100.001\""),
            valid.replace("\"1080p\"", "\"4k\""), valid.replace("\"beginnerMode\":\"0\"", "\"beginnerMode\":\"1\""))
        for (body in invalid) try { CloudSettingsCodec.decode(body, "1"); fail(body) } catch (_: IOException) { }
    }
    @Test fun mismatchedOwnerAndInconsistentVirtualVersionsAreRejected() {
        for (value in listOf(response().copy(userId = "2"), response().copy(cloudSaved = false),
            response(0), response().copy(updatedAt = null), response().copy(clientId = "other"))) {
            try { CloudSettingsCodec.decode(json.toJson(value), "1"); fail() } catch (_: IOException) { }
        }
    }
    @Test fun historicReceiptNeverDowngradesTheLastKnownVersion() {
        val newest = response(3).copy(settings = values().copy(returnAltitudeM = "80.25"))
        assertEquals(newest, CloudSettingsCodec.latest(newest, response(1)))
        assertEquals(newest, CloudSettingsCodec.latest(response(1), newest))
        try { CloudSettingsCodec.latest(newest, newest.copy(settings = values())); fail() } catch (_: IllegalArgumentException) { }
    }
    @Test fun getAndPutUseTheAuthenticatedSingletonAndRetryExactBody() = runBlocking {
        val sessions = registry(); val requestBody = CloudSettingsCodec.request(UserSettings(), 0, "stable-settings-key")
        val bodies = mutableListOf<String>()
        val client = api(sessions) { request ->
            assertEquals("/api/v1/settings", request.url.encodedPath)
            assertNull(request.url.query); assertEquals("Bearer fixture-token", request.header("Authorization"))
            if (request.method == "PUT") { val buffer = Buffer(); request.body!!.writeTo(buffer); bodies += buffer.readUtf8() }
            200 to json.toJson(response())
        }
        assertEquals(1L, client.settings(sessions.current()!!).version)
        repeat(2) { client.writeSettings(sessions.current()!!, requestBody) }
        assertEquals(listOf(requestBody, requestBody), bodies)
    }
    @Test fun wrongReceiptContentOrVersionCannotConfirmPendingSave() = runBlocking {
        for (value in listOf(response(2), response().copy(settings = values().copy(autoSyncRecords = "0")))) {
            val sessions = registry(); val client = api(sessions) { 200 to json.toJson(value) }
            try { client.writeSettings(sessions.current()!!, CloudSettingsCodec.request(UserSettings(), 0, "stable-settings-key")); fail() }
            catch (_: IOException) { }
        }
    }
    @Test fun conflictAndUnavailablePreserveSessionWhileUnauthorizedClearsIt() = runBlocking {
        for ((status, code) in listOf(409 to "VERSION_CONFLICT", 503 to "SERVICE_UNAVAILABLE", 401 to "UNAUTHENTICATED")) {
            val sessions = registry(); val client = api(sessions) { status to "{\"code\":\"$code\"}" }
            try { client.settings(sessions.current()!!); fail() } catch (error: AccountApiFailure) { assertEquals(code, error.code) }
            assertEquals(status != 401, sessions.current() != null)
        }
    }
    @Test fun replacedSessionRejectsLateSettingsResponse() = runBlocking {
        val sessions = registry(); val lease = sessions.current()!!
        val client = api(sessions) {
            sessions.install("2", "bob", "another-token", 5000)
            200 to json.toJson(response())
        }
        try { client.settings(lease); fail() } catch (_: StaleAccountResult) { }
    }
}
