-- Account preferences only. Cloud persistence does not assert that any physical aircraft applied them.
CREATE TABLE user_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    auto_sync_records CHAR(1) NOT NULL DEFAULT '1',
    low_battery_return CHAR(1) NOT NULL DEFAULT '1',
    return_altitude_m DECIMAL(6,2) NULL DEFAULT 100.00,
    failsafe_action CHAR(1) NOT NULL DEFAULT '0',
    obstacle_action CHAR(1) NOT NULL DEFAULT '0',
    control_sensitivity TINYINT NOT NULL DEFAULT 5,
    video_quality VARCHAR(16) NOT NULL DEFAULT '1080p',
    image_quality VARCHAR(16) NOT NULL DEFAULT 'high',
    beginner_mode CHAR(1) NOT NULL DEFAULT '0',
    version BIGINT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_user_settings_user FOREIGN KEY(user_id) REFERENCES users(id),
    CONSTRAINT ck_settings_flags CHECK(auto_sync_records IN ('0','1') AND low_battery_return IN ('0','1') AND beginner_mode IN ('0','1')),
    CONSTRAINT ck_settings_actions CHECK(failsafe_action IN ('0','1','2') AND obstacle_action IN ('0','1')),
    CONSTRAINT ck_settings_sensitivity CHECK(control_sensitivity BETWEEN 1 AND 10),
    CONSTRAINT ck_settings_quality CHECK(video_quality='1080p' AND image_quality='high'),
    CONSTRAINT ck_settings_height CHECK(return_altitude_m IS NULL OR (return_altitude_m BETWEEN 20 AND 400 AND (beginner_mode='0' OR return_altitude_m<=30))),
    CONSTRAINT ck_settings_version CHECK(version>=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
