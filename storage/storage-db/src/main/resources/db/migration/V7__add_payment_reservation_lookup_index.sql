CREATE INDEX ix_payment_reservation_status_id
    ON payment (reservation_id, status, id);
