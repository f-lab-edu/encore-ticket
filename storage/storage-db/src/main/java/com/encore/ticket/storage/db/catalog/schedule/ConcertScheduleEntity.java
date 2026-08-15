package com.encore.ticket.storage.db.catalog.schedule;

import com.encore.ticket.core.catalog.dto.ConcertStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "concert_schedule")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class ConcertScheduleEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long concertId;
    private OffsetDateTime startsAt;
    private OffsetDateTime endsAt;
    private OffsetDateTime bookingOpensAt;
    private OffsetDateTime bookingClosesAt;
    @Enumerated(EnumType.STRING)
    private ConcertStatus status;
}
