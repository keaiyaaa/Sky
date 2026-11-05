package com.tianshu.backend.system;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** A reachable database without the required business schema is NOT ready for traffic. */
@Component("backendSchema")
public class BackendSchemaHealthIndicator implements HealthIndicator {
    private final JdbcTemplate jdbc;
    public BackendSchemaHealthIndicator(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public Health health() {
        try {
            jdbc.execute("SELECT id,username,nickname,email,organization,profile_version,profile_updated_at FROM users LIMIT 0");
            jdbc.execute("SELECT id,user_id,client_id,drone_name,model,serial_no,firmware_version,source,version,created_at,updated_at,deleted_at FROM drones LIMIT 0");
            jdbc.execute("SELECT user_id,last_cursor FROM sync_user_sequences LIMIT 0");
            jdbc.execute("SELECT user_id,idempotency_key,request_hash,response_json,created_at FROM sync_receipts LIMIT 0");
            jdbc.execute("SELECT user_id,change_cursor,entity_type,server_id,client_id,operation,version,changed_at,payload_json FROM sync_changes LIMIT 0");
            jdbc.execute("SELECT id,user_id,client_id,category,action,result,source,detail,task_id,occurred_at,version,request_hash FROM flight_logs LIMIT 0");
            jdbc.execute("SELECT id,user_id,auto_sync_records,low_battery_return,return_altitude_m,failsafe_action,obstacle_action,control_sensitivity,video_quality,image_quality,beginner_mode,version,updated_at FROM user_settings LIMIT 0");
            if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM information_schema.views WHERE table_schema=DATABASE() AND table_name='mission_flight_log_view')",Boolean.class))) {
                jdbc.execute("SELECT event_id,user_id,client_id,action,result,source,detail,task_id,occurred_at,version FROM mission_flight_log_view LIMIT 0");
            }
            return Health.up().build();
        } catch (DataAccessException failure) { return Health.down().build(); }
    }
}
