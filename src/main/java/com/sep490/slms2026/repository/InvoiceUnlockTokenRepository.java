package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.InvoiceUnlockToken;
import com.sep490.slms2026.enums.InvoiceUnlockPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceUnlockTokenRepository extends JpaRepository<InvoiceUnlockToken, Long> {

    Optional<InvoiceUnlockToken> findByToken(UUID token);

    @Query("""
            SELECT t FROM InvoiceUnlockToken t
            WHERE t.managerId = :managerId
              AND t.invoiceId = :invoiceId
              AND t.purpose = :purpose
              AND t.passcodeId = :passcodeId
              AND t.usedAt IS NULL
              AND t.expiresAt > :now
            ORDER BY t.createdAt DESC
            """)
    List<InvoiceUnlockToken> findReusable(
            @Param("managerId") UUID managerId,
            @Param("invoiceId") Long invoiceId,
            @Param("purpose") InvoiceUnlockPurpose purpose,
            @Param("passcodeId") Long passcodeId,
            @Param("now") LocalDateTime now);
}
