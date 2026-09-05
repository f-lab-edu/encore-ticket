CREATE TABLE payment_refund (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payment_id BIGINT NOT NULL,
    payment_key VARCHAR(200) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    amount BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    reason VARCHAR(255) NULL,
    completed_at TIMESTAMP(6) NULL,
    failure_reason VARCHAR(255) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_refund_payment (payment_id),
    UNIQUE KEY uk_payment_refund_idempotency (idempotency_key),
    KEY ix_payment_refund_pending (status, created_at, id)
) ENGINE=InnoDB;
