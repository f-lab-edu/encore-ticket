CREATE TABLE refresh_token (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    token_hash          CHAR(64)     CHARACTER SET ascii NOT NULL,
    token_family_id     CHAR(36)     CHARACTER SET ascii NOT NULL,
    member_id           BIGINT       NOT NULL,
    status              VARCHAR(32)  NOT NULL,
    idle_expires_at     TIMESTAMP(6) NOT NULL,
    absolute_expires_at TIMESTAMP(6) NOT NULL,

    created_at          TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    KEY ix_refresh_token_family (token_family_id),
    KEY ix_refresh_token_expires (absolute_expires_at)
) ENGINE=InnoDB;
