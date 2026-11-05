package com.tianshu.backend.settings;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import static com.tianshu.backend.settings.SettingsModels.*;
import static com.tianshu.backend.profile.ProfileRepository.utc;

@Repository
public class SettingsRepository {
    private final JdbcTemplate jdbc;
    private final RowMapper<SettingsResponse> mapper=(rs,n)->new SettingsResponse(rs.getString("id"),CLIENT_ID,rs.getString("user_id"),
            new Values(rs.getString("auto_sync_records"),rs.getString("low_battery_return"),
                    rs.getBigDecimal("return_altitude_m")==null?null:rs.getBigDecimal("return_altitude_m").toPlainString(),
                    rs.getString("failsafe_action"),rs.getString("obstacle_action"),rs.getInt("control_sensitivity"),
                    rs.getString("video_quality"),rs.getString("image_quality"),rs.getString("beginner_mode")),
            rs.getLong("version"),rs.getTimestamp("updated_at",utc()).toInstant(),true,null);
    public SettingsRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public boolean userExists(String userId) { return !jdbc.queryForList("SELECT id FROM users WHERE id=?",String.class,userId).isEmpty(); }
    public Optional<SettingsResponse> find(String userId) {
        return jdbc.query("SELECT * FROM user_settings WHERE user_id=?",mapper,userId).stream().findFirst();
    }
    public void insert(String userId,Values value,Instant at) {
        jdbc.update("INSERT INTO user_settings(user_id,auto_sync_records,low_battery_return,return_altitude_m,failsafe_action,obstacle_action,control_sensitivity,video_quality,image_quality,beginner_mode,version,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,1,?)",ps->{
            ps.setString(1,userId); values(ps,2,value); ps.setTimestamp(11,Timestamp.from(at),utc());
        });
    }
    public boolean update(String userId,Values value,long expectedVersion,Instant at) {
        return jdbc.update("UPDATE user_settings SET auto_sync_records=?,low_battery_return=?,return_altitude_m=?,failsafe_action=?,obstacle_action=?,control_sensitivity=?,video_quality=?,image_quality=?,beginner_mode=?,version=version+1,updated_at=? WHERE user_id=? AND version=?",ps->{
            values(ps,1,value); ps.setTimestamp(10,Timestamp.from(at),utc()); ps.setString(11,userId); ps.setLong(12,expectedVersion);
        })==1;
    }
    private static void values(java.sql.PreparedStatement ps,int start,Values value) throws java.sql.SQLException {
        ps.setString(start,value.autoSyncRecords()); ps.setString(start+1,value.lowBatteryReturn());
        if(value.returnAltitudeM()==null) ps.setNull(start+2,java.sql.Types.DECIMAL);
        else ps.setBigDecimal(start+2,new java.math.BigDecimal(value.returnAltitudeM()));
        ps.setString(start+3,value.failsafeAction()); ps.setString(start+4,value.obstacleAction());
        ps.setInt(start+5,value.controlSensitivity()); ps.setString(start+6,value.videoQuality());
        ps.setString(start+7,value.imageQuality()); ps.setString(start+8,value.beginnerMode());
    }
}
