CREATE TABLE users (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    name                  VARCHAR(120) NOT NULL,
    email                 VARCHAR(190) NOT NULL,
    password_hash         VARCHAR(100) NOT NULL,
    role                  VARCHAR(20)  NOT NULL DEFAULT 'USER',
    notify_email          VARCHAR(190) NULL,
    default_reminder_days VARCHAR(100) NOT NULL DEFAULT '30,15,7,1',
    created_at            DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE certificates (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    user_id           BIGINT        NOT NULL,
    title             VARCHAR(200)  NOT NULL,
    type              VARCHAR(30)   NOT NULL,
    issuer            VARCHAR(200)  NULL,
    cert_number       VARCHAR(120)  NULL,
    issue_date        DATE          NULL,
    expiry_date       DATE          NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    date_source       ENUM('AUTO','MANUAL') NOT NULL DEFAULT 'MANUAL',
    reminder_days     VARCHAR(100)  NULL,
    reminders_enabled BIT(1)        NOT NULL DEFAULT b'1',
    notes             TEXT          NULL,
    tags              VARCHAR(300)  NULL,
    archived          BIT(1)        NOT NULL DEFAULT b'0',
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_cert_expiry_status (expiry_date, status),
    KEY idx_cert_user (user_id),
    CONSTRAINT fk_cert_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE certificate_files (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    certificate_id BIGINT       NOT NULL,
    file_name      VARCHAR(255) NOT NULL,
    mime_type      VARCHAR(100) NOT NULL,
    size_bytes     BIGINT       NOT NULL,
    sha256         CHAR(64)     NOT NULL,
    data           LONGBLOB     NOT NULL,
    version        INT          NOT NULL,
    uploaded_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_file_cert (certificate_id),
    KEY idx_file_sha (sha256),
    CONSTRAINT fk_file_cert FOREIGN KEY (certificate_id) REFERENCES certificates (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE notification_log (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    certificate_id BIGINT       NOT NULL,
    offset_days    INT          NOT NULL,
    expiry_date    DATE         NOT NULL,
    sent_to        VARCHAR(190) NOT NULL,
    sent_at        DATETIME(6)  NOT NULL,
    status         VARCHAR(10)  NOT NULL,
    attempts       INT          NOT NULL DEFAULT 1,
    error          VARCHAR(1000) NULL,
    -- set only for SENT rows: guarantees a reminder is never sent twice, even if the job runs twice
    dedup_key      VARCHAR(100) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notif_dedup (dedup_key),
    KEY idx_notif_cert (certificate_id),
    CONSTRAINT fk_notif_cert FOREIGN KEY (certificate_id) REFERENCES certificates (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
