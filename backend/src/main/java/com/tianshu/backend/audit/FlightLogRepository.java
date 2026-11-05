package com.tianshu.backend.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static com.tianshu.backend.audit.FlightLogModels.*;
import static com.tianshu.backend.profile.ProfileRepository.utc;

@Repository
public class FlightLogRepository {
    private final JdbcTemplate jdbc;
    private final RowMapper<LogEntry> mapper = (rs, n) -> new LogEntry(rs.getString("id"), rs.getString("client_id"),
            rs.getString("category"), EventTextSanitizer.clean(rs.getString("action")),
            EventTextSanitizer.clean(rs.getString("result")), rs.getString("source"),
            EventTextSanitizer.clean(rs.getString("detail")), EventTextSanitizer.clean(rs.getString("task_id")),
            rs.getTimestamp("occurred_at", utc()).toInstant(), rs.getLong("version"), rs.getString("origin"));
    private static final String CLIENT = "SELECT CONCAT('client:',id) id,user_id,client_id,category,action,result,source,detail,task_id,occurred_at,version,'CLIENT_EVENT' origin FROM flight_logs";
    public FlightLogRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public LogPage list(String userId, Query query) {
        boolean missions = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM information_schema.views "
                + "WHERE table_schema=DATABASE() AND table_name='mission_flight_log_view')", Boolean.class));
        String union = CLIENT + (missions ? " UNION ALL SELECT CONCAT('mission:',event_id),user_id,client_id,'MISSION',action,result,source,detail,task_id,occurred_at,version,'MISSION_EVENT' FROM mission_flight_log_view" : "");
        StringBuilder where = new StringBuilder(" WHERE user_id=?");
        List<Object> args = new ArrayList<>(List.of(userId));
        if (!query.keyword().isEmpty()) {
            String pattern = "%" + query.keyword().replace("!", "!!").replace("%", "!%") .replace("_", "!_") + "%";
            where.append(" AND (action LIKE ? ESCAPE '!' OR result LIKE ? ESCAPE '!' OR detail LIKE ? ESCAPE '!' OR task_id LIKE ? ESCAPE '!')");
            for (int i=0; i<4; i++) args.add(pattern);
        }
        if (query.source() != null) { where.append(" AND source=?"); args.add(query.source()); }
        if (query.category() != null) { where.append(" AND category=?"); args.add(query.category()); }
        // UTC DATETIME values are formatted without relying on the host/JDBC default timezone.
        if (query.from() != null) { where.append(" AND occurred_at >= ?"); args.add(utcSql(query.from())); }
        if (query.to() != null) { where.append(" AND occurred_at < ?"); args.add(utcSql(query.to())); }
        String from = " FROM (" + union + ") events" + where;
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from, Long.class, args.toArray());
        args.add(query.size()); args.add((long) query.page() * query.size());
        var items = jdbc.query("SELECT *" + from + " ORDER BY occurred_at DESC,id COLLATE utf8mb4_bin DESC LIMIT ? OFFSET ?", mapper, args.toArray());
        return new LogPage(items, query.page(), query.size(), total == null ? 0 : total, missions);
    }

    public Optional<Stored> find(String userId, String clientId) {
        return jdbc.query("SELECT e.*,f.request_hash FROM (" + CLIENT + ") e JOIN flight_logs f "
                        + "ON CONCAT('client:',f.id)=e.id WHERE e.user_id=? AND e.client_id=?",
                (rs, n) -> new Stored(mapper.mapRow(rs,n),rs.getString("request_hash")), userId, clientId)
                .stream().findFirst();
    }

    public LogEntry insert(String userId, CreateLogRequest request, String hash) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var ps = connection.prepareStatement("INSERT INTO flight_logs(user_id,client_id,category,action,result,source,detail,task_id,occurred_at,version,request_hash) VALUES(?,?,?,?,?,?,?,?,?,1,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1,userId); ps.setString(2,request.clientId()); ps.setString(3,request.category());
            ps.setString(4,request.action()); ps.setString(5,request.result()); ps.setString(6,request.source());
            ps.setString(7,request.detail()); ps.setString(8,request.taskId());
            ps.setTimestamp(9,Timestamp.from(java.time.Instant.parse(request.occurredAt())),utc()); ps.setString(10,hash);
            return ps;
        },key);
        return find(userId,request.clientId()).orElseThrow().entry();
    }
    private static String utcSql(java.time.Instant time) {
        return java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSSSSS")
                .withZone(java.time.ZoneOffset.UTC).format(time);
    }
}
