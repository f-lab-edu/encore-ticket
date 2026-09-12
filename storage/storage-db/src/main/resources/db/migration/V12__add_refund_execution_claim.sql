ALTER TABLE payment_refund
    ADD COLUMN execution_token VARCHAR(36) NULL,
    ADD COLUMN execution_until TIMESTAMP(6) NULL,
    ADD COLUMN request_started_at TIMESTAMP(6) NULL;

-- 기존 환불은 이미 요청했을 가능성이 있으므로 최초 요청 대상으로 간주하지 않는다.
UPDATE payment_refund SET request_started_at = created_at
WHERE request_started_at IS NULL;
