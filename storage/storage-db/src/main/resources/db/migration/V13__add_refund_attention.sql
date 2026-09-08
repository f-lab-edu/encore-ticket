ALTER TABLE payment_refund
    ADD COLUMN attention_reason VARCHAR(64) NULL,
    ADD COLUMN attention_since TIMESTAMP(6) NULL,
    ADD COLUMN attention_resolved_at TIMESTAMP(6) NULL,
    ADD KEY ix_payment_refund_attention (attention_reason, attention_since, id);
