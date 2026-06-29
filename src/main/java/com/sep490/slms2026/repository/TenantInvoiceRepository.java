package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.TenantInvoice;
import com.sep490.slms2026.enums.TenantInvoiceStatus;
import com.sep490.slms2026.enums.TenantInvoiceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantInvoiceRepository extends JpaRepository<TenantInvoice, Long> {

    Optional<TenantInvoice> findByIdAndTenantUserId(Long id, UUID tenantUserId);

    Optional<TenantInvoice> findByUtilityInvoiceId(Long utilityInvoiceId);

    Optional<TenantInvoice> findByPayosOrderCode(Long payosOrderCode);

    boolean existsByTenantContractIdAndInvoiceTypeAndBillingYearAndBillingMonth(
            Long tenantContractId, TenantInvoiceType invoiceType, Integer billingYear, Integer billingMonth);

    @Query("""
            SELECT i FROM TenantInvoice i
            WHERE i.tenantUserId = :tenantUserId
              AND (:status IS NULL OR i.status = :status)
              AND (:type IS NULL OR i.invoiceType = :type)
            ORDER BY i.createdAt DESC
            """)
    List<TenantInvoice> findForTenant(
            @Param("tenantUserId") UUID tenantUserId,
            @Param("status") TenantInvoiceStatus status,
            @Param("type") TenantInvoiceType type);
}
