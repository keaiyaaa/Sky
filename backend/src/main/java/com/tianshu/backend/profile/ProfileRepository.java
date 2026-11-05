package com.tianshu.backend.profile;

import com.tianshu.backend.common.ApiFailure;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;
import java.util.TimeZone;
import static com.tianshu.backend.profile.ProfileModels.*;

/** A projection over the ONE users table; does not introduce a second account entity. */
@Repository
public class ProfileRepository {
    private final JdbcTemplate jdbc;
    public ProfileRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public ProfileResponse find(String userId) {
        return jdbc.query("""
                SELECT id, username, COALESCE(nickname,'') nickname, email, organization,
                       profile_version, profile_updated_at FROM users WHERE id=?
                """, (rs, row) -> new ProfileResponse(rs.getString("id"), rs.getString("username"),
                rs.getString("nickname"), rs.getString("email"), rs.getString("organization"),
                rs.getLong("profile_version"), rs.getTimestamp("profile_updated_at", utc()).toInstant()),
                userId).stream().findFirst().orElseThrow(ApiFailure::notFound);
    }

    public boolean update(String userId, UpdateProfileRequest request, Instant at) {
        return jdbc.update("""
                UPDATE users SET nickname=?,email=?,organization=?,profile_version=profile_version+1,
                                 profile_updated_at=? WHERE id=? AND profile_version=?
                """, ps -> {
            ps.setString(1, request.nickname()); ps.setString(2, request.email());
            ps.setString(3, request.organization()); ps.setTimestamp(4, Timestamp.from(at), utc());
            ps.setString(5, userId); ps.setLong(6, request.expectedVersion());
        }) == 1;
    }

    public static Calendar utc() { return Calendar.getInstance(TimeZone.getTimeZone("UTC")); }
}
