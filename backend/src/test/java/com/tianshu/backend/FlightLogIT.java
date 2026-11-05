package com.tianshu.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.backend.audit.FlightLogModels.*;
import com.tianshu.backend.security.CurrentUserPrincipal;
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
class FlightLogIT {
    static final MySqlTestDatabase DB=new MySqlTestDatabase();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",()->DB.url); r.add("spring.datasource.username",()->DB.username); r.add("spring.datasource.password",()->DB.password);
    }
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper json;
    @Autowired com.tianshu.backend.system.BackendSchemaHealthIndicator schemaHealth;
    @BeforeAll void migrate() {
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,username VARCHAR(64) NOT NULL,password_hash VARCHAR(255) NOT NULL) ENGINE=InnoDB");
        assertEquals(3,Flyway.configure().dataSource(DB.url,DB.username,DB.password).baselineOnMigrate(true).baselineVersion("1").load().migrate().migrationsExecuted);
    }
    @BeforeEach void clear() {
        for(String table:List.of("sync_receipts","sync_changes","sync_user_sequences","user_settings","flight_logs","drones","users")) jdbc.update("DELETE FROM "+table);
        jdbc.update("INSERT INTO users(id,username,password_hash) VALUES(1,'alice','fixture'),(2,'bob','fixture')");
    }
    @AfterAll void close() { DB.close(); }
    private UsernamePasswordAuthenticationToken auth(String user) { return new UsernamePasswordAuthenticationToken(new CurrentUserPrincipal(user),null,List.of()); }
    private CreateLogRequest request(String client,String detail) { return new CreateLogRequest(client,"CONNECTION","连接失败","FAILED","DEVICE",detail,"run-a","2026-11-04T02:00:00Z",0L,UUID.randomUUID().toString()); }
    private LogEntry create(String user,CreateLogRequest req) throws Exception {
        return json.readValue(mvc.perform(post("/api/v1/flight-logs").with(authentication(auth(user))).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(req)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),LogEntry.class);
    }
    @Test void authenticationAndOwnerIsolation() throws Exception {
        mvc.perform(get("/api/v1/flight-logs")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/flight-logs").contentType(MediaType.APPLICATION_JSON).content("{}")) .andExpect(status().isUnauthorized());
        create("1",request("shared","Alice event")); create("2",request("shared","Bob event"));
        mvc.perform(get("/api/v1/flight-logs").param("userId","2").with(authentication(auth("1"))))
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].detail").value("Alice event"))
                .andExpect(jsonPath("$.missionEventsAvailable").value(false));
        String injected=json.writeValueAsString(request("new","x")).replace("\"clientId\":","\"userId\":\"2\",\"clientId\":");
        mvc.perform(post("/api/v1/flight-logs").with(authentication(auth("1"))).contentType(MediaType.APPLICATION_JSON).content(injected)) .andExpect(status().isBadRequest());
    }
    @Test void literalSearchSourceCategoryAndUtcRange() throws Exception {
        create("1",request("a","错误100%_\\路径")); create("1",request("b","普通记录"));
        for(String q:List.of("%","_","\\")) mvc.perform(get("/api/v1/flight-logs").with(authentication(auth("1"))).param("keyword",q)
                .param("source","DEVICE").param("category","CONNECTION").param("from","2026-11-04T02:00:00Z").param("to","2026-11-04T02:00:01Z"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/v1/flight-logs").with(authentication(auth("1"))).param("keyword","' OR 1=1 --")) .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/v1/flight-logs").with(authentication(auth("1"))).param("to","2026-11-04T02:00:00Z")) .andExpect(jsonPath("$.total").value(0));
    }
    @Test void exactRetryAndStableEventIdDoNotDuplicate() throws Exception {
        var req=request("stable-event","same"); var saved=create("1",req);
        assertEquals(saved,create("1",req));
        assertEquals(saved,create("1",new CreateLogRequest(req.clientId(),req.category(),req.action(),req.result(),req.source(),req.detail(),req.taskId(),req.occurredAt(),0L,UUID.randomUUID().toString())));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM flight_logs",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sync_changes WHERE entity_type='FLIGHT_LOG'",Integer.class));
    }
    @Test void reusedKeyAndChangedEventContentsConflict() throws Exception {
        var req=request("stable-event","one"); create("1",req);
        var changed=new CreateLogRequest(req.clientId(),req.category(),req.action(),req.result(),req.source(),"two",req.taskId(),req.occurredAt(),0L,req.idempotencyKey());
        mvc.perform(post("/api/v1/flight-logs").with(authentication(auth("1"))).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(changed)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        mvc.perform(post("/api/v1/flight-logs").with(authentication(auth("1"))).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request("stable-event","two"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EVENT_ID_REUSED"));
    }
    @Test void sensitiveValuesAreRemovedFromEventsAndReceipts() throws Exception {
        var saved=create("1",request("a","Bearer secret-token password=\"hidden spaces\" a@example.com 13800138000"));
        for(String secret:List.of("secret-token","hidden","a@example.com","13800138000")) {
            assertFalse(saved.detail().contains(secret));
            assertFalse(jdbc.queryForObject("SELECT response_json FROM sync_receipts LIMIT 1",String.class).contains(secret));
        }
        assertTrue(saved.detail().contains("REDACTED"));
    }
    @Test void invalidInputsAndMissionCopiesAreRejected() throws Exception {
        for(String path:List.of("?page=-1","?size=101","?source=FAKE","?category=BAD","?from=2026-11-04T10:00:00+08:00","?from=2026-11-05T00:00:00Z&to=2026-11-04T00:00:00Z"))
            mvc.perform(get("/api/v1/flight-logs"+path).with(authentication(auth("1")))) .andExpect(status().isBadRequest());
        for(String body:List.of(json.writeValueAsString(request("a","x")).replace("CONNECTION","MISSION"),
                json.writeValueAsString(request("a","x")).replace("2026-11-04T02:00:00Z","not-a-date")))
            mvc.perform(post("/api/v1/flight-logs").with(authentication(auth("1"))).contentType(MediaType.APPLICATION_JSON).content(body)) .andExpect(status().isBadRequest());
    }
    @Test void readonlyMissionViewIsMergedWithoutCopying() throws Exception {
        create("1",request("a","client"));
        jdbc.execute("CREATE VIEW mission_flight_log_view AS SELECT 'run-a:1' event_id,1 user_id,'mission-client' client_id,'巡检' action,'模拟完成' result,'LOCAL_SIMULATION' source,'run detail' detail,'run-a' task_id,CAST('2026-11-04 03:00:00' AS DATETIME) occurred_at,1 version UNION ALL SELECT 'run-b:1',2,'bob-event','巡检','模拟完成','LOCAL_SIMULATION','bob secret','run-b',CAST('2026-11-04 03:00:00' AS DATETIME),1");
        try {
            mvc.perform(get("/api/v1/flight-logs").with(authentication(auth("1")))) .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.missionEventsAvailable").value(true))
                    .andExpect(jsonPath("$.items[0].origin").value("MISSION_EVENT")).andExpect(jsonPath("$.items[0].source").value("LOCAL_SIMULATION"));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM flight_logs",Integer.class));
        } finally { jdbc.execute("DROP VIEW mission_flight_log_view"); }
    }
    @Test void brokenMissionViewIsAnErrorRatherThanAnEmptySuccess() throws Exception {
        jdbc.execute("CREATE VIEW mission_flight_log_view AS SELECT 1 broken");
        try {
            mvc.perform(get("/api/v1/flight-logs").with(authentication(auth("1")))) .andExpect(status().isServiceUnavailable());
            assertEquals("DOWN",schemaHealth.health().getStatus().getCode());
        }
        finally { jdbc.execute("DROP VIEW mission_flight_log_view"); }
        assertEquals("UP",schemaHealth.health().getStatus().getCode());
    }
    @Test void receiptFailureRollsBackEventAndCursor() throws Exception {
        jdbc.execute("ALTER TABLE sync_receipts ADD CONSTRAINT test_reject_f21 CHECK(user_id<0)");
        try {
            mvc.perform(post("/api/v1/flight-logs").with(authentication(auth("1"))).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request("a","x")))) .andExpect(status().isServiceUnavailable());
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM flight_logs",Integer.class));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sync_changes",Integer.class));
        } finally { jdbc.execute("ALTER TABLE sync_receipts DROP CHECK test_reject_f21"); }
    }
    @Test void concurrentRetriesAndPaginationAreStable() throws Exception {
        var req=request("concurrent","x"); var pool=Executors.newFixedThreadPool(2);
        try {
            Future<LogEntry> a=pool.submit(()->create("1",req)),b=pool.submit(()->create("1",req));
            assertEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
        for(int i=0;i<4;i++) create("1",request("page-"+i,"x"));
        var first=mvc.perform(get("/api/v1/flight-logs").with(authentication(auth("1"))).param("size","2")) .andExpect(jsonPath("$.total").value(5)).andReturn().getResponse().getContentAsString();
        var second=mvc.perform(get("/api/v1/flight-logs").with(authentication(auth("1"))).param("size","2").param("page","1")) .andReturn().getResponse().getContentAsString();
        assertNotEquals(json.readTree(first).get("items").get(0).get("id"),json.readTree(second).get("items").get(0).get("id"));
    }
}
