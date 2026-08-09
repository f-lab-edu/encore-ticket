package com.encore.ticket.storage.db.auth.token;

import com.encore.ticket.core.auth.token.domain.RefreshToken;
import com.encore.ticket.core.auth.token.domain.RefreshTokenStatus;
import com.encore.ticket.core.auth.token.port.RefreshTokenRepository;

import com.querydsl.jpa.impl.JPAQueryFactory;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import lombok.RequiredArgsConstructor;

import static com.encore.ticket.storage.db.auth.token.QRefreshTokenEntity.refreshTokenEntity;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepositoryImpl implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository refreshTokenJpa;
    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return refreshTokenJpa.findByTokenHash(tokenHash).map(RefreshTokenMapper::toDomain);
    }

    @Override
    public void save(RefreshToken refreshToken) {
        refreshTokenJpa.save(RefreshTokenMapper.toEntity(refreshToken));
    }

    @Transactional
    @Override
    public void revokeFamily(String tokenFamilyId) {
        queryFactory
                .update(refreshTokenEntity)
                .set(refreshTokenEntity.status, RefreshTokenStatus.REVOKED)
                .where(refreshTokenEntity.tokenFamilyId.eq(tokenFamilyId))
                .execute();
    }
}
