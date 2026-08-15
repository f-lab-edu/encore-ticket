CREATE TABLE seat_assignment (
    seat_id BIGINT NOT NULL,

    reservation_id BIGINT NOT NULL,
    schedule_id BIGINT NOT NULL,

    created_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (seat_id),
    KEY ix_assignment_schedule (schedule_id),
    KEY ix_assignment_reservation (reservation_id)
) ENGINE=InnoDB;

ALTER TABLE reservation_seat DROP INDEX uk_seat_per_schedule;
