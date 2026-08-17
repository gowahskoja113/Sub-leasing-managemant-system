package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.TenantPayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface TenantPaymentRepository extends JpaRepository<TenantPayment, Long> {

    List<TenantPayment> findByTenantUserIdOrderByPaidAtDesc(UUID tenantUserId);

    List<TenantPayment> findByTenantInvoiceId(Long tenantInvoiceId);

    @Query(
            value = """
                    SELECT p FROM TenantPayment p
                    JOIN FETCH p.tenantInvoice i
                    JOIN FETCH i.tenantContract tc
                    JOIN FETCH tc.property prop
                    LEFT JOIN FETCH tc.tenant t
                    LEFT JOIN FETCH t.user
                    LEFT JOIN FETCH tc.room
                    WHERE (:managerId IS NULL OR prop.operationManagerId = :managerId)
                      AND (:propertyId IS NULL OR prop.id = :propertyId)
                      AND (:contractId IS NULL OR tc.id = :contractId)
                      AND (:fromPaidAt IS NULL OR p.paidAt >= :fromPaidAt)
                      AND (:toPaidAtExclusive IS NULL OR p.paidAt < :toPaidAtExclusive)
                    ORDER BY p.paidAt DESC
                    """,
            countQuery = """
                    SELECT COUNT(p) FROM TenantPayment p
                    JOIN p.tenantInvoice i
                    JOIN i.tenantContract tc
                    JOIN tc.property prop
                    WHERE (:managerId IS NULL OR prop.operationManagerId = :managerId)
                      AND (:propertyId IS NULL OR prop.id = :propertyId)
                      AND (:contractId IS NULL OR tc.id = :contractId)
                      AND (:fromPaidAt IS NULL OR p.paidAt >= :fromPaidAt)
                      AND (:toPaidAtExclusive IS NULL OR p.paidAt < :toPaidAtExclusive)
                    """)
    Page<TenantPayment> findHistoryForManager(
            @Param("managerId") UUID managerId,
            @Param("propertyId") Long propertyId,
            @Param("contractId") Long contractId,
            @Param("fromPaidAt") LocalDateTime fromPaidAt,
            @Param("toPaidAtExclusive") LocalDateTime toPaidAtExclusive,
            Pageable pageable);
}
