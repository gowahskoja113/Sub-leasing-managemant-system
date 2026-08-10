package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.MeterOverridePasscode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeterOverridePasscodeRepository extends JpaRepository<MeterOverridePasscode, Long> {

    @Query("""
            SELECT p FROM MeterOverridePasscode p
            WHERE p.code = :code
              AND p.usedAt IS NULL
              AND p.expiresAt > :now
            """)
    Optional<MeterOverridePasscode> findUsableByCode(
            @Param("code") String code, @Param("now") LocalDateTime now);

    boolean existsByCodeAndUsedAtIsNullAndExpiresAtAfter(String code, LocalDateTime now);

    @Query("""
            SELECT p FROM MeterOverridePasscode p
            ORDER BY p.createdAt DESC
            """)
    List<MeterOverridePasscode> findAllOrderByCreatedAtDesc();

    @Query("""
            SELECT p FROM MeterOverridePasscode p
            WHERE p.usedAt IS NULL AND p.expiresAt > :now
            ORDER BY p.createdAt DESC
            """)
    List<MeterOverridePasscode> findActive(@Param("now") LocalDateTime now);

    long countByCreatedByAndCreatedAtAfter(UUID createdBy, LocalDateTime after);
}
