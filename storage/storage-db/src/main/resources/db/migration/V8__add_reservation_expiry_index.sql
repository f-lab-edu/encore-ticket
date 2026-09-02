CREATE INDEX ix_reservation_expiry
    ON reservation (status, expires_at, id);
