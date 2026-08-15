package com.encore.ticket.storage.db.catalog.concert;

import com.encore.ticket.core.catalog.dto.ConcertStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "concert")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ConcertEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String description;
    private String notice;
    private String posterUrl;
    private String venue;

    @Enumerated(EnumType.STRING)
    private ConcertStatus status;

}
