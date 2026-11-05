package com.tianshu.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.backend.security.CurrentUserPrincipal;
import com.tianshu.backend.settings.SettingsModels.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserSettingsIT {
    static final MySqlTestDatabase DB=new MySqlTestDatabase();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",()->DB.url); r.add("spring.datasource.username",()->DB.username); r.add("spring.datasource.password",()->DB.password);
    }
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
    @BeforeAll void migrate() {
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,username VARCHAR(64) NOT NULL,password_hash VARCHAR(255) NOT NULL) ENGINE=InnoDB");
        var flyway=Flyway.configure().dataSource(DB.url,DB.username,DB.password).baselineOnMigrate(true).baselineVersion("1").load();
        assertEquals(3,flyway.migrate().migrationsExecuted); assertEquals(0,flyway.migrate().migrationsExecuted);
    }
    @BeforeEach void clear() {
        for(String table:List.of("sync_receipts","sync_changes","sync_user_sequences","user_settings","flight_logs","drones","users")) jdbc.update("DELETE FROM "+table);
        jdbc.update("INSERT INTO users(id,username,password_hash) VALUES(1,'alice','fixture'),(2,'bob','fixture')");
    }
    @AfterAll void close() { DB.close(); }
    private UsernamePasswordAuthenticationToken identity(String id) { return new UsernamePasswordAuthenticationToken(new CurrentUserPrincipal(id),null,List.of()); }
    private UpdateRequest request(Values values,long version) { return new UpdateRequest(values,version,UUID.randomUUID().toString()); }
    private SettingsResponse save(String owner,UpdateRequest body) throws Exception {
        return json.readValue(mvc.perform(put("/api/v1/settings").with(authentication(identity(owner))).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),SettingsResponse.class);
    }
    private Values height(String value) { return new Values("1","1",value,"0","0",5,"1080p","high","0"); }

    @Test void anonymousRequestsAndForgedOwnersAreRejected() throws Exception {
        mvc.perform(get("/api/v1/settings")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/settings").contentType(MediaType.APPLICATION_JSON).content("{}")) .andExpect(status().isUnauthorized());
        String body=json.writeValueAsString(request(Values.defaults(),0)).replace("\"settings\":","\"userId\":\"2\",\"settings\":");
        mvc.perform(put("/api/v1/settings").with(authentication(identity("1"))).contentType(MediaType.APPLICATION_JSON).content(body)) .andExpect(status().isBadRequest());
    }
    @Test void defaultsAreReadOnlyAndDeviceApplicationRemainsUnknown() throws Exception {
        for(String owner:List.of("1","2")) {
            var response=mvc.perform(get("/api/v1/settings").with(authentication(identity(owner)))) .andExpect(status().isOk())
                    .andExpect(jsonPath("$.version").value(0)).andExpect(jsonPath("$.cloudSaved").value(false))
                    .andExpect(jsonPath("$.settings.returnAltitudeM").value("100.00")).andReturn().getResponse();
            var tree=json.readTree(response.getContentAsString());
            assertTrue(tree.has("deviceApplied")); assertTrue(tree.get("deviceApplied").isNull());
            assertTrue(tree.get("id").isNull()); assertTrue(tree.get("updatedAt").isNull());
        }
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM user_settings",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sync_changes",Integer.class));
    }
    @Test void firstSaveIsIdempotentAndNormalizesDecimalStrings() throws Exception {
        var request=request(height("100.2"),0); var response=save("1",request);
        assertEquals("100.20",response.settings().returnAltitudeM()); assertEquals(1,response.version());
        assertTrue(response.cloudSaved()); assertNull(response.deviceApplied()); assertNotNull(response.id());
        assertEquals(response,save("1",request));
        assertEquals(response,save("1",new UpdateRequest(height("100.20"),0L,request.idempotencyKey())));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM user_settings",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sync_changes WHERE entity_type='USER_SETTING'",Integer.class));
    }
    @Test void explicitNullClearsHeightWithoutMixingAccounts() throws Exception {
        save("1",request(Values.defaults(),0)); var result=save("1",request(height(null),1));
        assertEquals(2,result.version()); assertNull(result.settings().returnAltitudeM());
        mvc.perform(get("/api/v1/settings").with(authentication(identity("2")))) .andExpect(jsonPath("$.cloudSaved").value(false)).andExpect(jsonPath("$.version").value(0));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM user_settings",Integer.class));
    }
    @Test void invalidValuesAndNumericHeightAreRejected() throws Exception {
        for(String invalid:List.of("19.99","400.01","100.001","1e2","NaN","-20")) {
            mvc.perform(put("/api/v1/settings").with(authentication(identity("1"))).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request(height(invalid),0)))) .andExpect(status().isBadRequest());
        }
        String valid=json.writeValueAsString(request(Values.defaults(),0));
        for(String body:List.of(valid.replace("\"100.00\"","100.00"),valid.replace("\"autoSyncRecords\":\"1\"","\"autoSyncRecords\":1"),
                valid.replace("\"1080p\"","\"4k\""),valid.replace("\"controlSensitivity\":5","\"controlSensitivity\":11"),
                valid.replace("\"controlSensitivity\":5","\"controlSensitivity\":\"5\""),
                valid.replace("\"expectedVersion\":0","\"expectedVersion\":\"0\""),
                valid.replace("\"controlSensitivity\":5","\"controlSensitivity\":5.0"),
                valid.replace("\"beginnerMode\":\"0\"","\"beginnerMode\":\"1\"")))
            mvc.perform(put("/api/v1/settings").with(authentication(identity("1"))).contentType(MediaType.APPLICATION_JSON).content(body)) .andExpect(status().isBadRequest());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM user_settings",Integer.class));
    }
    @Test void requiredNullableHeightCannotBeOmittedAndDeviceStateCannotBeForged() throws Exception {
        String valid=json.writeValueAsString(request(Values.defaults(),0));
        for(String body:List.of(valid.replace("\"returnAltitudeM\":\"100.00\",",""),
                valid.replace("\"autoSyncRecords\":\"1\"","\"autoSyncRecords\":null"),
                valid.replace("\"settings\":","\"deviceApplied\":true,\"settings\":")))
            mvc.perform(put("/api/v1/settings").with(authentication(identity("1"))).contentType(MediaType.APPLICATION_JSON).content(body)) .andExpect(status().isBadRequest());
    }
    @Test void staleVersionsAndKeyReuseDoNotOverwriteValues() throws Exception {
        var first=request(Values.defaults(),0); save("1",first);
        var changed=new UpdateRequest(height("80.00"),1L,first.idempotencyKey());
        mvc.perform(put("/api/v1/settings").with(authentication(identity("1"))).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(changed)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        mvc.perform(put("/api/v1/settings").with(authentication(identity("1"))).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request(height("80.00"),0))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        assertEquals("100.00",jdbc.queryForObject("SELECT return_altitude_m FROM user_settings",java.math.BigDecimal.class).toPlainString());
    }
    @Test void receiptFailureRollsBackPreferencesAndChanges() throws Exception {
        jdbc.execute("ALTER TABLE sync_receipts ADD CONSTRAINT test_reject_settings CHECK(user_id<0)");
        try {
            mvc.perform(put("/api/v1/settings").with(authentication(identity("1"))).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request(Values.defaults(),0)))) .andExpect(status().isServiceUnavailable());
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM user_settings",Integer.class));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sync_changes",Integer.class));
        } finally { jdbc.execute("ALTER TABLE sync_receipts DROP CHECK test_reject_settings"); }
    }
    @Test void concurrentSameVersionSavesHaveOneWinner() throws Exception {
        save("1",request(Values.defaults(),0)); var pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            var futures=List.of("80.00","90.00").stream().map(height->pool.submit(()->{
                start.await(); return mvc.perform(put("/api/v1/settings").with(authentication(identity("1"))).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request(height(height),1)))).andReturn().getResponse().getStatus();
            })).toList(); start.countDown();
            var statuses=List.of(futures.get(0).get(10,TimeUnit.SECONDS),futures.get(1).get(10,TimeUnit.SECONDS));
            assertTrue(statuses.contains(200)); assertTrue(statuses.contains(409));
            assertEquals(2,jdbc.queryForObject("SELECT version FROM user_settings",Integer.class));
            assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM sync_changes",Integer.class));
        } finally { pool.shutdownNow(); }
    }
    @Test void replayReturnsOriginalReceiptEvenAfterNewerUpdates() throws Exception {
        var first=request(Values.defaults(),0); var one=save("1",first);
        save("1",request(height("80.25"),1));
        assertEquals(one,save("1",first));
        mvc.perform(get("/api/v1/settings").with(authentication(identity("1"))))
                .andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.settings.returnAltitudeM").value("80.25"));
    }
}
