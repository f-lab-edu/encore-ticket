CREATE TABLE concert (
    id          BIGINT NOT NULL AUTO_INCREMENT,

    title       VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    notice      TEXT NULL,
    poster_url  VARCHAR(500) NOT NULL,
    venue       VARCHAR(500) NOT NULL,
    status      VARCHAR(32) NOT NULL,
    like_count  INT NOT NULL DEFAULT 0,

    created_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id)
) ENGINE=InnoDB;

CREATE TABLE concert_schedule (
    id                  BIGINT NOT NULL AUTO_INCREMENT,

    concert_id          BIGINT NOT NULL,
    starts_at           TIMESTAMP(6) NOT NULL,
    ends_at             TIMESTAMP(6) NOT NULL,
    booking_opens_at    TIMESTAMP(6) NOT NULL,
    booking_closes_at    TIMESTAMP(6) NOT NULL,
    status              VARCHAR(32) NOT NULL,

    created_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    KEY ix_schedule_concert_starts (concert_id, starts_at)
) ENGINE=InnoDB;

CREATE TABLE concert_price (
    concert_id  BIGINT NOT NULL,
    grade       VARCHAR(32) NOT NULL,
    price       BIGINT NOT NULL,

    created_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (concert_id, grade)

) ENGINE=InnoDB;
CREATE TABLE seat
(
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    schedule_id  BIGINT      NOT NULL,
    section_name VARCHAR(50) NOT NULL,
    row_label    VARCHAR(20) NOT NULL,
    seat_number  VARCHAR(20) NOT NULL,
    grade        VARCHAR(32) NOT NULL,

    created_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_seat_position (schedule_id, section_name, row_label, seat_number),
    KEY ix_seat_schedule (schedule_id)
) ENGINE=InnoDB;
