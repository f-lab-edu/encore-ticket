ALTER TABLE payment
    ADD COLUMN last_recovery_at TIMESTAMP(6) NULL,
    ADD KEY ix_payment_pending_recovery (status, last_recovery_at, created_at, id);

ALTER TABLE payment_refund
    ADD COLUMN last_recovery_at TIMESTAMP(6) NULL,
    ADD KEY ix_payment_refund_pending_recovery (status, last_recovery_at, created_at, id);
