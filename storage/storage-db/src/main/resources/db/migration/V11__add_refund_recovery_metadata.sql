ALTER TABLE payment_refund
    ADD COLUMN recovery_category VARCHAR(64) NULL,
    ADD COLUMN recovery_error_code VARCHAR(128) NULL,
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0,
    ADD COLUMN next_retry_at TIMESTAMP(6) NULL,
    ADD COLUMN recovery_stop_reason VARCHAR(255) NULL,
    ADD CONSTRAINT ck_payment_refund_retry_count CHECK (retry_count >= 0);
