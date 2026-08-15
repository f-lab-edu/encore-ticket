package com.encore.ticket.storage.db.catalog.price;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "concert_price")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(ConcertPriceId.class)
public class ConcertPriceEntity {
    @Id
    private Long concertId;

    @Id
    private String grade;

    private Long price;
}