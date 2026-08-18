package com.encore.ticket.core.auth.token.application;

import com.encore.ticket.core.auth.exception.InvalidRefreshTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import com.encore.ticket.core.auth.token.domain.RefreshToken;
import com.encore.ticket.core.auth.token.domain.RefreshTokenStatus;
import com.encore.ticket.core.auth.token.port.AccessTokenIssuer;
import com.encore.ticket.core.auth.token.port.RefreshTokenGenerator;
import com.encore.ticket.core.auth.token.port.RefreshTokenRepository;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-04T10:00:00Z"), ZoneOffset.UTC);
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-04T10:00:00Z");

    private static final String RAW_TOKEN = "rft_current";
    private static final String TOKEN_HASH = "hash_current";
    private static final String NEW_RAW_TOKEN = "rft_next";
    private static final String NEW_TOKEN_HASH = "hash_next";
    private static final String FAMILY_ID = "family_1";
    private static final long MEMBER_ID = 1L;

    private static final OffsetDateTime IDLE_EXPIRES_AT = OffsetDateTime.parse("2026-08-11T10:00:00Z");
    private static final OffsetDateTime ABSOLUTE_EXPIRES_AT = OffsetDateTime.parse("2026-08-24T10:00:00Z");

    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock RefreshTokenGenerator refreshTokenGenerator;
    @Mock AccessTokenIssuer accessTokenIssuer;

    TokenService service;

    @BeforeEach
    void setUp() {
        service = new TokenService(refreshTokenRepository, refreshTokenGenerator, accessTokenIssuer, CLOCK);
    }

    private RefreshToken token(RefreshTokenStatus status, OffsetDateTime idleExpiresAt,
                               OffsetDateTime absoluteExpiresAt) {
        return RefreshToken.builder()
                .tokenHash(TOKEN_HASH)
                .tokenFamilyId(FAMILY_ID)
                .memberId(MEMBER_ID)
                .status(status)
                .idleExpiresAt(idleExpiresAt)
                .absoluteExpiresAt(absoluteExpiresAt)
                .build();
    }

    private RefreshToken givenStored(RefreshToken stored) {
        given(refreshTokenGenerator.hash(RAW_TOKEN)).willReturn(TOKEN_HASH);
        given(refreshTokenRepository.findByTokenHash(TOKEN_HASH)).willReturn(Optional.of(stored));
        return stored;
    }

    private void givenNewTokenIssued() {
        given(refreshTokenGenerator.generate()).willReturn(NEW_RAW_TOKEN);
        given(refreshTokenGenerator.hash(NEW_RAW_TOKEN)).willReturn(NEW_TOKEN_HASH);
    }

    private List<RefreshToken> savedTokens() {
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void 재발급하면_새_액세스_토큰과_새_리프레시_토큰을_돌려준다() {
        givenStored(token(RefreshTokenStatus.ACTIVE, IDLE_EXPIRES_AT, ABSOLUTE_EXPIRES_AT));
        givenNewTokenIssued();
        given(accessTokenIssuer.issue(MEMBER_ID)).willReturn("new-access-token");

        RefreshResult result = service.refresh(RAW_TOKEN);

        assertThat(result.response().accessToken()).isEqualTo("new-access-token");
        assertThat(result.response().tokenType()).isEqualTo("Bearer");
        assertThat(result.response().expiresIn()).isEqualTo(900);
        assertThat(result.refreshToken()).isEqualTo(NEW_RAW_TOKEN);
    }

    @Test
    void 재발급하면_기존_토큰은_ROTATED가_되고_같은_family에_새_토큰이_저장된다() {
        givenStored(token(RefreshTokenStatus.ACTIVE, IDLE_EXPIRES_AT, ABSOLUTE_EXPIRES_AT));
        givenNewTokenIssued();

        service.refresh(RAW_TOKEN);

        List<RefreshToken> saved = savedTokens();
        assertThat(saved.get(0).tokenHash()).isEqualTo(TOKEN_HASH);
        assertThat(saved.get(0).status()).isEqualTo(RefreshTokenStatus.ROTATED);

        RefreshToken issued = saved.get(1);
        assertThat(issued.tokenHash()).isEqualTo(NEW_TOKEN_HASH);
        assertThat(issued.tokenFamilyId()).isEqualTo(FAMILY_ID);
        assertThat(issued.memberId()).isEqualTo(MEMBER_ID);
        assertThat(issued.status()).isEqualTo(RefreshTokenStatus.ACTIVE);
    }

    @Test
    void 새_미사용_만료는_재발급_시점부터_7일이다() {
        givenStored(token(RefreshTokenStatus.ACTIVE, IDLE_EXPIRES_AT, ABSOLUTE_EXPIRES_AT));
        givenNewTokenIssued();

        service.refresh(RAW_TOKEN);

        assertThat(savedTokens().get(1).idleExpiresAt()).isEqualTo(NOW.plusDays(7));
    }

    @Test
    void 절대_만료가_7일보다_가까우면_새_미사용_만료는_절대_만료를_넘지_않는다() {
        OffsetDateTime absoluteExpiresAt = NOW.plusDays(3);
        givenStored(token(RefreshTokenStatus.ACTIVE, IDLE_EXPIRES_AT, absoluteExpiresAt));
        givenNewTokenIssued();

        service.refresh(RAW_TOKEN);

        RefreshToken issued = savedTokens().get(1);
        assertThat(issued.idleExpiresAt()).isEqualTo(absoluteExpiresAt);
        assertThat(issued.absoluteExpiresAt()).isEqualTo(absoluteExpiresAt);
    }

    @Test
    void 쿠키가_없으면_재발급에_실패한다() {
        assertThatThrownBy(() -> service.refresh(null))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenGenerator, never()).hash(any());
        verify(refreshTokenRepository, never()).findByTokenHash(any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void 알_수_없는_토큰이면_재발급에_실패한다() {
        given(refreshTokenGenerator.hash(RAW_TOKEN)).willReturn(TOKEN_HASH);
        given(refreshTokenRepository.findByTokenHash(TOKEN_HASH)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh(RAW_TOKEN))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeFamily(anyString());
    }

    @Test
    void 미사용_만료_시각에_도달하면_재발급에_실패한다() {
        givenStored(token(RefreshTokenStatus.ACTIVE, NOW, ABSOLUTE_EXPIRES_AT));

        assertThatThrownBy(() -> service.refresh(RAW_TOKEN))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void 절대_만료_시각에_도달하면_재발급에_실패한다() {
        givenStored(token(RefreshTokenStatus.ACTIVE, IDLE_EXPIRES_AT, NOW));

        assertThatThrownBy(() -> service.refresh(RAW_TOKEN))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void 이미_Rotation된_토큰을_다시_쓰면_family_전체가_폐기된다() {
        givenStored(token(RefreshTokenStatus.ROTATED, IDLE_EXPIRES_AT, ABSOLUTE_EXPIRES_AT));

        assertThatThrownBy(() -> service.refresh(RAW_TOKEN))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository).revokeFamily(FAMILY_ID);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void 폐기된_토큰이면_family를_다시_폐기하지_않고_실패한다() {
        givenStored(token(RefreshTokenStatus.REVOKED, IDLE_EXPIRES_AT, ABSOLUTE_EXPIRES_AT));

        assertThatThrownBy(() -> service.refresh(RAW_TOKEN))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository, never()).revokeFamily(anyString());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void 로그아웃하면_현재_기기의_family_전체가_폐기된다() {
        givenStored(token(RefreshTokenStatus.ACTIVE, IDLE_EXPIRES_AT, ABSOLUTE_EXPIRES_AT));

        service.logout(RAW_TOKEN);

        verify(refreshTokenRepository).revokeFamily(FAMILY_ID);
    }

    @Test
    void 쿠키가_없어도_로그아웃은_실패하지_않는다() {
        service.logout(null);

        verify(refreshTokenGenerator, never()).hash(any());
        verify(refreshTokenRepository, never()).findByTokenHash(any());
        verify(refreshTokenRepository, never()).revokeFamily(any());
    }

    @Test
    void 알_수_없는_토큰으로_로그아웃해도_실패하지_않는다() {
        given(refreshTokenGenerator.hash(RAW_TOKEN)).willReturn(TOKEN_HASH);
        given(refreshTokenRepository.findByTokenHash(TOKEN_HASH)).willReturn(Optional.empty());

        service.logout(RAW_TOKEN);

        verify(refreshTokenRepository, never()).revokeFamily(anyString());
    }

    @Test
    void 이미_폐기된_토큰으로_로그아웃해도_실패하지_않는다() {
        givenStored(token(RefreshTokenStatus.REVOKED, IDLE_EXPIRES_AT, ABSOLUTE_EXPIRES_AT));

        service.logout(RAW_TOKEN);

        verify(refreshTokenRepository).revokeFamily(FAMILY_ID);
    }
}
