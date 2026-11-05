-- Requires T03's ONE users table: id BIGINT primary key and username.
-- F19/F20 do not create credentials, accounts or an authentication bypass.
ALTER TABLE users
    ADD COLUMN nickname VARCHAR(64) NULL,
    ADD COLUMN email VARCHAR(254) NOT NULL DEFAULT '',
    ADD COLUMN organization VARCHAR(60) NOT NULL DEFAULT '',
    ADD COLUMN profile_version BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN profile_updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

CREATE TABLE drones (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    client_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
    drone_name VARCHAR(64) NOT NULL,
    model VARCHAR(64) NOT NULL DEFAULT '',
    serial_no VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
    firmware_version VARCHAR(64) NOT NULL DEFAULT '',
    source VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    active_serial_no VARCHAR(128) COLLATE utf8mb4_bin
        GENERATED ALWAYS AS (IF(deleted_at IS NULL,serial_no,NULL)) STORED,
    CONSTRAINT fk_drones_user FOREIGN KEY(user_id) REFERENCES users(id),
    CONSTRAINT uk_drones_client UNIQUE(user_id,client_id),
    CONSTRAINT uk_drones_active_serial UNIQUE(user_id,active_serial_no),
    CONSTRAINT ck_drones_version CHECK(version >= 1),
    CONSTRAINT ck_drones_source CHECK(source='DJI_SDK'),
    INDEX ix_drones_owner_list(user_id,deleted_at,created_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Shared with the future T11 sync controller: do not create another set of receipt/change tables.
-- All writers lock users.id BEFORE allocating a cursor and hold that lock through commit.
CREATE TABLE sync_user_sequences (
    user_id BIGINT PRIMARY KEY,
    last_cursor BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_sync_sequence_user FOREIGN KEY(user_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE sync_receipts (
    user_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    response_json JSON NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY(user_id,idempotency_key),
    CONSTRAINT fk_sync_receipt_user FOREIGN KEY(user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sync_changes (
    user_id BIGINT NOT NULL,
    change_cursor BIGINT NOT NULL,
    entity_type VARCHAR(32) NOT NULL,
    server_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
    client_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
    operation VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    changed_at DATETIME(6) NOT NULL,
    payload_json JSON NULL,
    PRIMARY KEY(user_id,change_cursor),
    CONSTRAINT fk_sync_change_user FOREIGN KEY(user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
