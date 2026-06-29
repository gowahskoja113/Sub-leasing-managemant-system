package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.TenantPaymentClaim;
import com.sep490.slms2026.enums.PaymentClaimStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantPaymentClaimRepository extends JpaRepository<TenantPaymentClaim, Long> {

    Optional<TenantPaymentClaim> findByTenantInvoiceIdAndStatus(
            Long tenantInvoiceId, PaymentClaimStatus status);

    @Query("""
            SELECT c FROM TenantPaymentClaim c
            JOIN FETCH c.tenantInvoice i
            JOIN FETCH i.tenantContract tc
            JOIN FETCH tc.property p
            LEFT JOIN FETCH tc.tenant t
            LEFT JOIN FETCH t.user
            LEFT JOIN FETCH tc.room
            WHERE (:managerId IS NULL OR p.operationManagerId = :managerId)
              AND (:status IS NULL OR c.status = :status)
            ORDER BY c.createdAt DESC
            """)
    List<TenantPaymentClaim> findForManager(
            @Param("managerId") UUID managerId,
            @Param("status") PaymentClaimStatus status);
}
