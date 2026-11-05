package com.tianshu.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.backend.common.ApiFailure;
import com.tianshu.backend.device.DeviceService;
import com.tianshu.backend.security.CurrentUserPrincipal;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import static com.tianshu.backend.device.DeviceModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProfileDeviceIT {
    static final MySqlTestDatabase DB = new MySqlTestDatabase();
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DB.url);
        registry.add("spring.datasource.username", () -> DB.username);
        registry.add("spring.datasource.password", () -> DB.password);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired DeviceService devices;
    @Autowired com.tianshu.backend.system.BackendSchemaHealthIndicator schemaHealth;

    @BeforeAll void migrate() {
        // T03 boundary fixture only. Not a production account migration or authentication implementation.
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,username VARCHAR(64) NOT NULL UNIQUE,"
                + "password_hash VARCHAR(255) NOT NULL) ENGINE=InnoDB");
        Flyway flyway = Flyway.configure().dataSource(DB.url, DB.username, DB.password)
                .baselineOnMigrate(true).baselineVersion("1").load();
        assertEquals(3, flyway.migrate().migrationsExecuted);
        assertEquals(0, flyway.migrate().migrationsExecuted);
    }

    @BeforeEach void clear() {
        for (String table : List.of("sync_receipts", "sync_changes", "sync_user_sequences", "user_settings", "flight_logs", "drones", "users")) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("INSERT INTO users(id,username,nickname,password_hash) VALUES(1,'alice','Alice','fixture'),(2,'bob','Bob','fixture')");
    }
    @AfterAll void stop() { DB.close(); }

    private UsernamePasswordAuthenticationToken identity(String id) {
        return new UsernamePasswordAuthenticationToken(new CurrentUserPrincipal(id), null, List.of());
    }
    private CreateDeviceRequest createRequest(String clientId, String serial) {
        return new CreateDeviceRequest(clientId, "巡检设备", "M30", serial, "1.0", "DJI_SDK", 0L, UUID.randomUUID().toString());
    }
    private DeviceResponse createAs(String user, CreateDeviceRequest request) throws Exception {
        return json.readValue(mvc.perform(post("/api/v1/devices").with(authentication(identity(user)))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), DeviceResponse.class);
    }

    @Test void protectedEndpointsRequireAuthentication() throws Exception {
        for (String path : List.of("/api/v1/me", "/api/v1/devices")) {
            var response = mvc.perform(get(path)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED")).andReturn().getResponse();
            assertEquals(response.getHeader("X-Request-Id"), json.readTree(response.getContentAsString()).get("requestId").asText());
        }
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer invented-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test void profileUpdateUsesCurrentUserAndRejectsStaleVersion() throws Exception {
        String body = "{\"nickname\":\"  测试昵称  \",\"email\":\"a@example.com\",\"organization\":\"巡检组\",\"expectedVersion\":1}";
        mvc.perform(patch("/api/v1/me").with(authentication(identity("1"))).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value("1"))
                .andExpect(jsonPath("$.nickname").value("测试昵称")).andExpect(jsonPath("$.version").value(2));
        mvc.perform(patch("/api/v1/me").with(authentication(identity("1"))).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        mvc.perform(get("/api/v1/me").with(authentication(identity("2"))))
                .andExpect(jsonPath("$.nickname").value("Bob")).andExpect(jsonPath("$.email").value(""));
    }

    @Test void invalidProfilesAndOwnerInjectionAreRejected() throws Exception {
        for (String body : List.of(
                "{\"nickname\":\"\",\"email\":\"\",\"organization\":\"\",\"expectedVersion\":1}",
                "{\"nickname\":\"A\",\"email\":\"bad\",\"organization\":\"\",\"expectedVersion\":1}",
                "{\"nickname\":\"A\",\"email\":\"\",\"organization\":\"\",\"expectedVersion\":1,\"userId\":\"2\"}",
                "{\"nickname\":null,\"email\":\"\",\"organization\":\"\",\"expectedVersion\":1}")) {
            mvc.perform(patch("/api/v1/me").with(authentication(identity("1"))).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    @Test void duplicateCreateReplaysReceiptAndDifferentPayloadConflicts() throws Exception {
        var request = createRequest("client-a", "SN-A");
        var saved = createAs("1", request);
        assertEquals(saved, createAs("1", request));
        var changed = new CreateDeviceRequest("client-b", request.droneName(), request.model(), "SN-B",
                request.firmwareVersion(), "DJI_SDK", 0L, request.idempotencyKey());
        mvc.perform(post("/api/v1/devices").with(authentication(identity("1"))).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(changed))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM drones", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sync_changes", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sync_receipts", Integer.class));
    }

    @Test void serialDeduplicationIsPerUserAndOtherOwnersAreHidden() throws Exception {
        var saved = createAs("1", createRequest("a", "shared-sn"));
        createAs("2", createRequest("b", "shared-sn"));
        mvc.perform(post("/api/v1/devices").with(authentication(identity("1"))).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(createRequest("c", "shared-sn"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEVICE_ALREADY_SAVED"));
        mvc.perform(get("/api/v1/devices/" + saved.id()).with(authentication(identity("2")))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/devices/" + saved.id()).with(authentication(identity("2")))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new DeleteDeviceRequest(1L, UUID.randomUUID().toString()))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/devices").with(authentication(identity("1"))))
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].id").isString());
    }

    @Test void updateDeleteAndDeleteRetryPreserveVersionsAndTombstones() throws Exception {
        var saved = createAs("1", createRequest("a", "SN-A"));
        var update = new UpdateDeviceRequest("新名称", "M30", "2.0", 1L, UUID.randomUUID().toString());
        var updated = mvc.perform(put("/api/v1/devices/" + saved.id()).with(authentication(identity("1")))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(update)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2)).andReturn();
        String deleteBody = json.writeValueAsString(new DeleteDeviceRequest(2L, UUID.randomUUID().toString()));
        String tombstone = mvc.perform(delete("/api/v1/devices/" + saved.id()).with(authentication(identity("1")))
                .contentType(MediaType.APPLICATION_JSON).content(deleteBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.deleted").value(true))
                .andExpect(jsonPath("$.version").value(3)).andReturn().getResponse().getContentAsString();
        assertEquals(json.readTree(tombstone), json.readTree(mvc.perform(delete("/api/v1/devices/" + saved.id())
                .with(authentication(identity("1"))).contentType(MediaType.APPLICATION_JSON).content(deleteBody))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()));
        mvc.perform(get("/api/v1/devices").with(authentication(identity("1")))).andExpect(jsonPath("$.total").value(0));
        createAs("1", createRequest("new-client", "SN-A")); // Removed record does not permanently reserve the SN.
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sync_changes WHERE operation='DELETE' AND payload_json IS NULL", Integer.class));
    }

    @Test void demoSourcesInvalidIdsAndPaginationAreRejected() throws Exception {
        var demo = new CreateDeviceRequest("demo", "演示", "", "SIM", "", "LOCAL_SIMULATION", 0L, UUID.randomUUID().toString());
        mvc.perform(post("/api/v1/devices").with(authentication(identity("1"))).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(demo))).andExpect(status().isBadRequest());
        for (String path : List.of("/api/v1/devices?page=-1", "/api/v1/devices?size=101", "/api/v1/devices?size=bad", "/api/v1/devices/1abc")) {
            mvc.perform(get(path).with(authentication(identity("1")))).andExpect(status().isBadRequest());
        }
    }

    @Test void fractionalVersionsAreRejectedAndMissingSchemaIsNotReady() throws Exception {
        mvc.perform(patch("/api/v1/me").with(authentication(identity("1"))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"Alice\",\"email\":\"\",\"organization\":\"\",\"expectedVersion\":1.5}"))
                .andExpect(status().isBadRequest());
        assertEquals("UP", schemaHealth.health().getStatus().getCode());
        jdbc.execute("RENAME TABLE sync_changes TO sync_changes_test_hidden");
        try { assertEquals("DOWN", schemaHealth.health().getStatus().getCode()); }
        finally { jdbc.execute("RENAME TABLE sync_changes_test_hidden TO sync_changes"); }
    }

    @Test void receiptFailureRollsBackBusinessWriteAndCursor() throws Exception {
        jdbc.execute("ALTER TABLE sync_receipts ADD CONSTRAINT test_reject_receipt CHECK(user_id < 0)");
        try {
            mvc.perform(post("/api/v1/devices").with(authentication(identity("1"))).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(createRequest("a", "SN-A")))).andExpect(status().isServiceUnavailable());
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM drones", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sync_changes", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sync_user_sequences", Integer.class));
        } finally { jdbc.execute("ALTER TABLE sync_receipts DROP CHECK test_reject_receipt"); }
    }

    @Test void concurrentSameVersionUpdatesHaveExactlyOneWinner() throws Exception {
        var saved = createAs("1", createRequest("a", "SN-A"));
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var results = List.of(1, 2).stream().map(index -> pool.submit(() -> {
                SecurityContextHolder.getContext().setAuthentication(identity("1"));
                try {
                    start.await();
                    devices.update(saved.id(), new UpdateDeviceRequest("name-" + index, "M30", "1", 1L, UUID.randomUUID().toString()));
                    return "OK";
                } catch (ApiFailure failure) { return failure.code(); }
                finally { SecurityContextHolder.clearContext(); }
            })).toList();
            start.countDown();
            var values = List.of(results.get(0).get(10, TimeUnit.SECONDS), results.get(1).get(10, TimeUnit.SECONDS));
            assertEquals(1, values.stream().filter("OK"::equals).count());
            assertEquals(1, values.stream().filter("VERSION_CONFLICT"::equals).count());
            assertEquals(2, jdbc.queryForObject("SELECT MAX(change_cursor) FROM sync_changes WHERE user_id=1", Integer.class));
        } finally { pool.shutdownNow(); }
    }
}
