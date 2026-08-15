package com.encore.ticket.storage.db.catalog.seat;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "seat")
public class SeatEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long scheduleId;

    @Column(name = "section_name")
    private String section;
    @Column(name = "row_label")
    private String row;
    @Column(name = "seat_number")
    private String number;
    private String grade;
}
