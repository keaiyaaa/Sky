package com.tianshu.backend.sync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.tianshu.backend.common.ApiFailure;
import com.tianshu.backend.common.FieldRules;
import com.tianshu.backend.device.DeviceModels.DeviceResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.HexFormat;
import java.util.function.Supplier;
import static com.tianshu.backend.profile.ProfileRepository.utc;

/** Shared durable journal. Call inside the SAME transaction as the business write.
 * No sync HTTP controllers or other modules' adapters are supplied by F19/F20.
 */
@Component
public class SyncJournal {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    @SuppressWarnings("deprecation")
    public SyncJournal(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json.copy().enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    public <T> T execute(String userId, String key, Object normalizedRequest, Class<T> type, Supplier<T> write) {
        String normalizedKey = FieldRules.text(key, "idempotencyKey", 128, true);
        if (normalizedKey.codePointCount(0, normalizedKey.length()) < 16) throw ApiFailure.invalid("idempotencyKey", "至少16个字符");
        // Serialize every writer for this user before allocating a cursor. A slow earlier commit cannot be missed.
        if (jdbc.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE", String.class, userId).isEmpty()) {
            throw ApiFailure.notFound();
        }
        jdbc.update("INSERT INTO sync_user_sequences(user_id,last_cursor) VALUES(?,0) "
                + "ON DUPLICATE KEY UPDATE last_cursor=last_cursor", userId);
        String hash = hash(encode(normalizedRequest));
        var prior = jdbc.queryForList("SELECT request_hash,response_json FROM sync_receipts "
                + "WHERE user_id=? AND idempotency_key=?", userId, normalizedKey);
        if (!prior.isEmpty()) {
            if (!hash.equals(prior.getFirst().get("request_hash"))) {
                throw new ApiFailure(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "幂等键已用于不同请求");
            }
            try { return json.readValue(prior.getFirst().get("response_json").toString(), type); }
            catch (JsonProcessingException e) { throw new IllegalStateException("Invalid stored receipt", e); }
        }
        T result = write.get();
        jdbc.update("INSERT INTO sync_receipts(user_id,idempotency_key,request_hash,response_json,created_at) "
                + "VALUES(?,?,?,?,UTC_TIMESTAMP(6))", userId, normalizedKey, hash, encode(result));
        return result;
    }

    public void appendDevice(String userId, DeviceResponse result) {
        appendChange(userId, "DEVICE", result.id(), result.clientId(), result.deleted() ? "DELETE" : "UPSERT",
                result.version(), result.updatedAt(), result.deleted() ? null : result);
    }

    public String fingerprint(Object request) { return hash(encode(request)); }

    public void appendChange(String userId, String entityType, String serverId, String clientId,
                             String operation, long version, java.time.Instant changedAt, Object payload) {
        jdbc.update("UPDATE sync_user_sequences SET last_cursor=last_cursor+1 WHERE user_id=?", userId);
        Long cursor = jdbc.queryForObject("SELECT last_cursor FROM sync_user_sequences WHERE user_id=?",
                Long.class, userId);
        jdbc.update("""
                INSERT INTO sync_changes(user_id,change_cursor,entity_type,server_id,client_id,operation,
                                         version,changed_at,payload_json) VALUES(?,?,?,?,?,?,?,?,?)
                """, ps -> {
            ps.setString(1, userId); ps.setLong(2, java.util.Objects.requireNonNull(cursor));
            ps.setString(3, entityType); ps.setString(4, serverId); ps.setString(5, clientId);
            ps.setString(6, operation); ps.setLong(7, version);
            ps.setTimestamp(8, Timestamp.from(changedAt), utc());
            ps.setString(9, payload == null ? null : encode(payload));
        });
    }

    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot encode journal record", e); }
    }

    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
