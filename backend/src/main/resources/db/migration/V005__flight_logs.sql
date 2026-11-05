-- F21 owns client business events only. T06 owns mission tables and the optional read-only mission_flight_log_view.
CREATE TABLE flight_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    client_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
    category VARCHAR(32) NOT NULL,
    action VARCHAR(128) NOT NULL,
    result VARCHAR(64) NOT NULL,
    source VARCHAR(32) NOT NULL,
    detail VARCHAR(2000) NOT NULL DEFAULT '',
    task_id VARCHAR(128) NOT NULL DEFAULT '',
    occurred_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    CONSTRAINT fk_flight_log_user FOREIGN KEY(user_id) REFERENCES users(id),
    CONSTRAINT uk_flight_log_client UNIQUE(user_id,client_id),
    CONSTRAINT ck_flight_log_category CHECK(category IN ('CONNECTION','SETTING','COMMAND')),
    CONSTRAINT ck_flight_log_source CHECK(source IN ('DEVICE','DJI_SIMULATOR','LOCAL_SIMULATION','MANUAL','UNKNOWN')),
    CONSTRAINT ck_flight_log_version CHECK(version=1),
    INDEX ix_flight_log_time(user_id,occurred_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
