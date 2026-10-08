-- 平台表结构（幂等，启动时自动执行；兼容 MySQL 8 与 H2 MySQL 模式）
CREATE TABLE IF NOT EXISTS workspace (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(64) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_user (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    username     VARCHAR(64) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(64),
    role         VARCHAR(20) NOT NULL,
    workspace_id BIGINT NOT NULL DEFAULT 1,
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_user_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS device (
    sn           VARCHAR(64) PRIMARY KEY,
    vendor       VARCHAR(20) DEFAULT 'DJI',
    device_name  VARCHAR(64),
    model_key    VARCHAR(20),
    model_name   VARCHAR(64),
    kind         VARCHAR(20),
    parent_sn    VARCHAR(64),
    workspace_id BIGINT NOT NULL DEFAULT 1,
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS audit_log (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(64),
    action     VARCHAR(64) NOT NULL,
    detail     VARCHAR(1000),
    success    TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wayline_file (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    name               VARCHAR(128) NOT NULL,
    file_key           VARCHAR(256) NOT NULL,
    size_bytes         BIGINT,
    drone_model_key    VARCHAR(20),
    drone_model_name   VARCHAR(64),
    payload_model_key  VARCHAR(20),
    payload_model_name VARCHAR(64),
    template_type      VARCHAR(32),
    wayline_count      INT,
    waypoint_count     INT,
    workspace_id       BIGINT NOT NULL DEFAULT 1,
    creator            VARCHAR(64),
    created_at         DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wayline_job (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(128),
    flight_id       VARCHAR(64),
    wayline_file_id BIGINT NOT NULL,
    drone_sn        VARCHAR(64) NOT NULL,
    workspace_id    BIGINT NOT NULL DEFAULT 1,
    task_type       VARCHAR(20) NOT NULL,
    execute_time    BIGINT,
    status          VARCHAR(20) NOT NULL,
    progress        INT DEFAULT 0,
    message         VARCHAR(500),
    bid             VARCHAR(64),
    creator         VARCHAR(64),
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS map_element (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    name         VARCHAR(128) NOT NULL,
    element_type VARCHAR(16) NOT NULL,
    coordinates  TEXT NOT NULL,
    color        VARCHAR(16),
    workspace_id BIGINT NOT NULL DEFAULT 1,
    creator      VARCHAR(64),
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS plot (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    name         VARCHAR(128) NOT NULL,
    code         VARCHAR(64),
    category     VARCHAR(32),
    crop         VARCHAR(64),
    area_mu      DOUBLE,
    description  VARCHAR(500),
    coordinates  TEXT NOT NULL,
    color        VARCHAR(16),
    workspace_id BIGINT NOT NULL DEFAULT 1,
    creator      VARCHAR(64),
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS flight_record (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    drone_sn      VARCHAR(64) NOT NULL,
    job_id        BIGINT,
    job_name      VARCHAR(128),
    status        VARCHAR(16) NOT NULL,
    takeoff_time  BIGINT,
    landing_time  BIGINT,
    takeoff_lat   DOUBLE,
    takeoff_lng   DOUBLE,
    landing_lat   DOUBLE,
    landing_lng   DOUBLE,
    max_height    DOUBLE,
    distance_m    DOUBLE,
    report_status VARCHAR(16) DEFAULT 'NONE',
    report_no     VARCHAR(64),
    workspace_id  BIGINT NOT NULL DEFAULT 1,
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 全量遥测（架次内采样，3 秒/点）；量大后按需为 (flight_id, ts) 补索引
CREATE TABLE IF NOT EXISTS flight_telemetry (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    flight_id  BIGINT NOT NULL,
    ts         BIGINT NOT NULL,
    lat        DOUBLE,
    lng        DOUBLE,
    alt        DOUBLE,
    elevation  DOUBLE,
    speed      DOUBLE,
    heading    DOUBLE,
    battery    INT,
    mode       VARCHAR(16)
);
