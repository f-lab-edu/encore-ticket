package com.encore.ticket.storage.db.auth.token;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, Long> {

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);



}
