package com.encore.ticket.storage.db.catalog.price;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ConcertPriceId implements Serializable {
    private Long concertId;
    private String grade;
}