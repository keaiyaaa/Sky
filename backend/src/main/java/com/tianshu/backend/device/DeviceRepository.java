package com.tianshu.backend.device;

import com.tianshu.backend.common.ApiFailure;
import com.tianshu.backend.common.PageResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import static com.tianshu.backend.device.DeviceModels.*;
import static com.tianshu.backend.profile.ProfileRepository.utc;

@Repository
public class DeviceRepository {
    private final JdbcTemplate jdbc;
    private final RowMapper<DeviceResponse> mapper = (rs, row) -> new DeviceResponse(rs.getString("id"),
            rs.getString("client_id"), rs.getString("drone_name"), rs.getString("model"),
            rs.getString("serial_no"), rs.getString("firmware_version"), rs.getString("source"),
            rs.getLong("version"), rs.getTimestamp("created_at", utc()).toInstant(),
            rs.getTimestamp("updated_at", utc()).toInstant(), rs.getTimestamp("deleted_at") != null);

    public DeviceRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public PageResponse<DeviceResponse> list(String userId, int page, int size) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM drones WHERE user_id=? AND deleted_at IS NULL",
                Long.class, userId);
        var items = jdbc.query("SELECT * FROM drones WHERE user_id=? AND deleted_at IS NULL "
                + "ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?", mapper, userId, size, (long) page * size);
        return new PageResponse<>(items, page, size, total == null ? 0 : total);
    }

    public DeviceResponse find(String userId, String id, boolean includeDeleted) {
        return jdbc.query("SELECT * FROM drones WHERE user_id=? AND id=?"
                        + (includeDeleted ? "" : " AND deleted_at IS NULL"), mapper, userId, id)
                .stream().findFirst().orElseThrow(ApiFailure::notFound);
    }

    public String insert(String userId, CreateDeviceRequest request, Instant at) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var ps = connection.prepareStatement("""
                    INSERT INTO drones(user_id,client_id,drone_name,model,serial_no,firmware_version,
                                       source,version,created_at,updated_at) VALUES(?,?,?,?,?,?,?,1,?,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, userId); ps.setString(2, request.clientId()); ps.setString(3, request.droneName());
            ps.setString(4, request.model()); ps.setString(5, request.serialNo());
            ps.setString(6, request.firmwareVersion()); ps.setString(7, request.source());
            ps.setTimestamp(8, Timestamp.from(at), utc()); ps.setTimestamp(9, Timestamp.from(at), utc());
            return ps;
        }, key);
        return java.util.Objects.requireNonNull(key.getKey()).toString();
    }

    public boolean update(String userId, String id, UpdateDeviceRequest request, Instant at) {
        return jdbc.update("""
                UPDATE drones SET drone_name=?,model=?,firmware_version=?,version=version+1,updated_at=?
                WHERE user_id=? AND id=? AND version=? AND deleted_at IS NULL
                """, ps -> {
            ps.setString(1, request.droneName()); ps.setString(2, request.model());
            ps.setString(3, request.firmwareVersion()); ps.setTimestamp(4, Timestamp.from(at), utc());
            ps.setString(5, userId); ps.setString(6, id); ps.setLong(7, request.expectedVersion());
        }) == 1;
    }

    public boolean delete(String userId, String id, long version, Instant at) {
        return jdbc.update("""
                UPDATE drones SET deleted_at=?,updated_at=?,version=version+1
                WHERE user_id=? AND id=? AND version=? AND deleted_at IS NULL
                """, ps -> {
            ps.setTimestamp(1, Timestamp.from(at), utc()); ps.setTimestamp(2, Timestamp.from(at), utc());
            ps.setString(3, userId); ps.setString(4, id); ps.setLong(5, version);
        }) == 1;
    }
}
