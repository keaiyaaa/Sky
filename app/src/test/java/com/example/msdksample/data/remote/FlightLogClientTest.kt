package com.example.msdksample.data.remote

import com.example.msdksample.data.logs.FlightLogPolicy as Policy
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class FlightLogClientTest {
    private val json=Gson()
    private val event=CloudFlightLog("client:1","event-1","CONNECTION","连接","成功","DEVICE","","run-1","2026-11-04T02:00:00.123456Z",1,"CLIENT_EVENT")
    private fun sessions()=BackendSessionRegistry { 1L }.apply { install("1","alice","opaque-token",1000) }
    private fun api(s: BackendSessionRegistry,reply: (Request)->Pair<Int,String>)=BackendAccountApi("https://school.example/api/v1/",s,
        OkHttpClient.Builder().addInterceptor { chain -> val (code,body)=reply(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("test")
                .body(body.toResponseBody("application/json".toMediaType())).build() }.build())

    @Test fun queryKeepsLiteralSpecialCharactersAndSendsUtcBounds()=runBlocking {
        val s=sessions()
        val api=api(s) { request ->
            assertEquals("Bearer opaque-token",request.header("Authorization"))
            assertEquals("100%_&?",request.url.queryParameter("keyword"))
            assertEquals("2026-11-04T00:00:00Z",request.url.queryParameter("from"))
            assertEquals("1",request.url.queryParameter("page"))
            200 to json.toJson(FlightLogPage(listOf(event),1,20,21,false))
        }
        assertFalse(api.flightLogs(s.current()!!,1,FlightLogFilter("100%_&?","DEVICE",from="2026-11-04T00:00:00Z")).missionEventsAvailable)
    }
    @Test fun uploadRetryKeepsClientIdAndOperationKey()=runBlocking {
        val s=sessions(); val bodies=mutableListOf<String>()
        val body=json.toJson(FlightLogCreate("event-1","CONNECTION","连接","成功","DEVICE","","run-1","2026-11-04T02:00:00Z",idempotencyKey="stable-operation-key"))
        val api=api(s) { req -> val b=Buffer(); req.body!!.writeTo(b); bodies+=b.readUtf8(); 201 to json.toJson(event) }
        repeat(2) { api.uploadFlightLog(s.current()!!,body,"event-1") }
        assertEquals(listOf(body,body),bodies)
    }
    @Test fun aDifferentEventCannotAcknowledgeThisUpload()=runBlocking {
        val s=sessions(); val api=api(s) { 201 to json.toJson(event.copy(clientId="different")) }
        try { api.uploadFlightLog(s.current()!!,"{}","event-1"); fail() } catch(_:java.io.IOException) { }
    }
    @Test fun coverageAndProvenanceMustBeExplicit()=runBlocking {
        for(body in listOf("{\"items\":[],\"page\":0,\"size\":20,\"total\":0}",json.toJson(FlightLogPage(listOf(event.copy(source="REAL_FLIGHT_PROVEN")),0,20,1,true)))) {
            val s=sessions(); val api=api(s) { 200 to body }
            try { api.flightLogs(s.current()!!,0,FlightLogFilter()); fail() } catch(_:java.io.IOException) { }
        }
    }
    @Test fun accountSwitchRejectsLateFlightLogResults()=runBlocking {
        val s=sessions(); val lease=s.current()!!
        val api=api(s) { s.install("2","bob","token-b",2000); 200 to json.toJson(FlightLogPage(listOf(event),0,20,1,false)) }
        try { api.flightLogs(lease,0,FlightLogFilter()); fail() } catch(_:StaleAccountResult) { }
    }
    @Test fun errorsDoNotBecomeSuccessfulUploads()=runBlocking {
        val s=sessions(); val api=api(s) { 409 to "{\"code\":\"EVENT_ID_REUSED\"}" }
        try { api.uploadFlightLog(s.current()!!,"{}","event-1"); fail() }
        catch(e:AccountApiFailure) { assertEquals("EVENT_ID_REUSED",e.code) }
    }
    @Test fun dayFiltersUseShanghaiAndAnExclusiveEnd() {
        assertEquals("2026-11-03T16:00:00.000Z",Policy.utc(Policy.day("2026-11-04")!!))
        assertEquals("2026-11-04T16:00:00.000Z",Policy.utc(Policy.day("2026-11-04",true)!!))
        try { Policy.day("2026-02-30"); fail() } catch(_:Exception) { }
    }
    @Test fun timestampsRequireUtcAndAcceptServerMicroseconds() {
        assertEquals("2026-11-04T02:00:00.123Z",Policy.utc(Policy.parseUtc(event.occurredAt)))
        try { Policy.parseUtc("2026-11-04T10:00:00+08:00"); fail() } catch(_:Exception) { }
    }
    @Test fun credentialsAndPersonalContactsAreRedactedBeforePersisting() {
        val result=Policy.clean("Bearer secret-token password=\"hidden spaces\" a@example.com 13800138000")
        for(secret in listOf("secret-token","hidden","a@example.com","13800138000")) assertFalse(result.contains(secret))
        assertTrue(result.contains("REDACTED"))
    }
}
