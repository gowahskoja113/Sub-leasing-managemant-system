package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.InvoiceUnlockPasscode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InvoiceUnlockPasscodeRepository extends JpaRepository<InvoiceUnlockPasscode, Long> {

    @Query("""
            SELECT p FROM InvoiceUnlockPasscode p
            WHERE p.code = :code AND p.usedAt IS NULL AND p.expiresAt > :now
            """)
    Optional<InvoiceUnlockPasscode> findUsableByCode(@Param("code") String code, @Param("now") LocalDateTime now);

    boolean existsByCodeAndUsedAtIsNullAndExpiresAtAfter(String code, LocalDateTime now);

    Optional<InvoiceUnlockPasscode> findFirstByCodeAndInvoiceIdOrderByCreatedAtDesc(String code, Long invoiceId);

    @Query("""
            SELECT p FROM InvoiceUnlockPasscode p
            WHERE p.usedAt IS NULL AND p.expiresAt > :now
            ORDER BY p.createdAt DESC
            """)
    List<InvoiceUnlockPasscode> findActive(@Param("now") LocalDateTime now);

    List<InvoiceUnlockPasscode> findAllByOrderByCreatedAtDesc();

    long countByCreatedByAndCreatedAtAfter(java.util.UUID createdBy, LocalDateTime since);
}
