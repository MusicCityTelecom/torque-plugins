CREATE TABLE torque_diagnostic_reports (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_id VARCHAR(96) NOT NULL,
    vin VARCHAR(32) NULL,
    profile VARCHAR(96) NULL,
    started_at DATETIME(3) NULL,
    received_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    report_json JSON NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_torque_session_id (session_id),
    KEY idx_torque_vin_received (vin, received_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
