CREATE TABLE reservation (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    member_id             BIGINT       NOT NULL,
    schedule_id           BIGINT       NOT NULL,
    hold_id               VARCHAR(64)  NOT NULL,
    amount                BIGINT       NOT NULL,
    status                VARCHAR(32)  NOT NULL,
    reserved_at           TIMESTAMP(6)  NOT NULL,
    performance_starts_at TIMESTAMP(6)  NOT NULL,
    original_expires_at   TIMESTAMP(6)  NOT NULL,
    expires_at            TIMESTAMP(6)  NOT NULL,
    payment_attempt_no    INT          NOT NULL,
    payment_starts_at     TIMESTAMP(6)  NULL,
    cancelled_at          TIMESTAMP(6)  NULL,

    created_at            TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_reservation_hold (hold_id),
    KEY ix_reservation_member (member_id, id)
) ENGINE=InnoDB;

CREATE TABLE reservation_seat (
    reservation_id BIGINT NOT NULL,
    schedule_id    BIGINT NOT NULL,
    seat_id        BIGINT NOT NULL,

    created_at     TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (reservation_id, seat_id),
    UNIQUE KEY uk_seat_per_schedule (schedule_id, seat_id)
) ENGINE=InnoDB;

CREATE TABLE payment (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    payment_key    VARCHAR(200) NOT NULL,
    order_id       VARCHAR(64)  NOT NULL,
    amount         BIGINT       NOT NULL,
    reservation_id BIGINT       NOT NULL,
    member_id      BIGINT       NOT NULL,
    hold_id        VARCHAR(64)  NOT NULL,
    status         VARCHAR(32)  NOT NULL,
    method         VARCHAR(32)  NULL,
    approved_at    TIMESTAMP(6) NULL,
    fail_reason    VARCHAR(255) NULL,

    created_at     TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_key (payment_key),
    UNIQUE KEY uk_payment_order (order_id),
    KEY ix_payment_hold (hold_id, id)
) ENGINE=InnoDB;
