package com.encore.ticket.storage.db.auth.token;

import com.encore.ticket.core.auth.token.domain.RefreshTokenStatus;
import jakarta.persistence.*;
import lombok.*;


import java.time.OffsetDateTime;

@Entity
@Table(name = "refresh_token")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RefreshTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String tokenHash;
    private String tokenFamilyId;
    private Long memberId;

    @Enumerated(EnumType.STRING)
    private RefreshTokenStatus status;
    private OffsetDateTime idleExpiresAt;
    private OffsetDateTime absoluteExpiresAt;

}
