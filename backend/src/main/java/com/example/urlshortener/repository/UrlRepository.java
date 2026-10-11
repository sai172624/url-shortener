package com.example.urlshortener.repository;

import com.example.urlshortener.entity.Url;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UrlRepository extends JpaRepository<Url, Long> {

    Optional<Url> findByShortCode(String shortCode);

    List<Url> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    @Query("SELECT u FROM Url u WHERE u.ownerId = :ownerId AND u.isActive = true AND (u.expiresAt IS NULL OR u.expiresAt > :now)")
    List<Url> findActiveByOwnerId(@Param("ownerId") Long ownerId, @Param("now") OffsetDateTime now);

    @Query("SELECT u FROM Url u WHERE u.shortCode = :shortCode AND u.isActive = true AND (u.expiresAt IS NULL OR u.expiresAt > :now)")
    Optional<Url> findActiveByShortCode(@Param("shortCode") String shortCode, @Param("now") OffsetDateTime now);

    boolean existsByShortCode(String shortCode);

    long countByOwnerId(Long ownerId);

    @Query("DELETE FROM Url u WHERE u.shortCode = :shortCode AND u.ownerId = :ownerId")
    int deleteByShortCodeAndOwnerId(@Param("shortCode") String shortCode, @Param("ownerId") Long ownerId);
}