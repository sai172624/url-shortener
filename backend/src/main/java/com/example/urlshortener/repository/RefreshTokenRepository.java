package com.example.urlshortener.repository;

import com.example.urlshortener.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUserIdAndRevokedAtIsNull(Long userId);

    List<RefreshToken> findByTokenFamily(UUID tokenFamily);

    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken rt SET rt.revokedAt = :now WHERE rt.tokenHash = :tokenHash")
    int revokeByTokenHash(@Param("tokenHash") String tokenHash, @Param("now") OffsetDateTime now);

    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken rt SET rt.revokedAt = :now WHERE rt.tokenFamily = :tokenFamily")
    int revokeByTokenFamily(@Param("tokenFamily") UUID tokenFamily, @Param("now") OffsetDateTime now);

    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken rt SET rt.revokedAt = :now WHERE rt.userId = :userId")
    int revokeAllByUserId(@Param("userId") Long userId, @Param("now") OffsetDateTime now);

    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now OR rt.revokedAt IS NOT NULL")
    int deleteExpiredAndRevoked(@Param("now") OffsetDateTime now);

    boolean existsByTokenHash(String tokenHash);
}