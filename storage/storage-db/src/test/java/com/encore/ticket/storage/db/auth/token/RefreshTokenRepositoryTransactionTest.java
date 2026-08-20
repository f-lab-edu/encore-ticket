package com.encore.ticket.storage.db.auth.token;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

import com.encore.ticket.core.auth.token.domain.RefreshToken;
import com.encore.ticket.core.auth.token.domain.RefreshTokenStatus;
import com.encore.ticket.core.auth.token.port.RefreshTokenRepository;
import com.encore.ticket.storage.db.support.MySqlContainerConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(MySqlContainerConfig.class)
@Sql(statements = "DELETE FROM refresh_token WHERE member_id = 7001")
class RefreshTokenRepositoryTransactionTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-20T10:00:00Z"), ZoneOffset.UTC);
    private static final long MEMBER_ID = 7001L;
    private static final String FAMILY_ID = "family-boundary-test";
    private static final String SQUATTER_FAMILY_ID = "family-squatter";
    private static final String CURRENT_HASH = "hash-current";
    private static final String NEXT_HASH = "hash-next";

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Test
    void 새_토큰_발급이_실패하면_기존_토큰도_ACTIVE_로_남는다() {
        RefreshToken stored = storeActive(FAMILY_ID, CURRENT_HASH);
        storeActive(SQUATTER_FAMILY_ID, NEXT_HASH);

        assertThatThrownBy(() -> refreshTokenRepository.saveRotation(
                stored.rotate(),
                RefreshToken.rotatedFrom(stored, NEXT_HASH, CLOCK)))
                .isInstanceOf(RuntimeException.class);

        RefreshToken reloaded = refreshTokenRepository.findByTokenHash(CURRENT_HASH).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(RefreshTokenStatus.ACTIVE);

        RefreshToken squatter = refreshTokenRepository.findByTokenHash(NEXT_HASH).orElseThrow();
        assertThat(squatter.tokenFamilyId()).isEqualTo(SQUATTER_FAMILY_ID);
    }

    @Test
    void 회전에_성공하면_기존은_ROTATED_새_토큰은_ACTIVE_로_남는다() {
        RefreshToken stored = storeActive(FAMILY_ID, CURRENT_HASH);

        refreshTokenRepository.saveRotation(
                stored.rotate(),
                RefreshToken.rotatedFrom(stored, NEXT_HASH, CLOCK));

        assertThat(refreshTokenRepository.findByTokenHash(CURRENT_HASH).orElseThrow().status())
                .isEqualTo(RefreshTokenStatus.ROTATED);

        RefreshToken issued = refreshTokenRepository.findByTokenHash(NEXT_HASH).orElseThrow();
        assertThat(issued.status()).isEqualTo(RefreshTokenStatus.ACTIVE);
        assertThat(issued.tokenFamilyId()).isEqualTo(FAMILY_ID);
    }

    private RefreshToken storeActive(String familyId, String tokenHash) {
        refreshTokenRepository.save(RefreshToken.builder()
                .tokenHash(tokenHash)
                .tokenFamilyId(familyId)
                .memberId(MEMBER_ID)
                .status(RefreshTokenStatus.ACTIVE)
                .idleExpiresAt(OffsetDateTime.now(CLOCK).plusDays(7))
                .absoluteExpiresAt(OffsetDateTime.now(CLOCK).plusDays(30))
                .build());

        return refreshTokenRepository.findByTokenHash(tokenHash).orElseThrow();
    }
}
