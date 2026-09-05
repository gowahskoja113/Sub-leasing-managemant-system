package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.InvoiceDispute;
import com.sep490.slms2026.enums.InvoiceDisputeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface InvoiceDisputeRepository extends JpaRepository<InvoiceDispute, Long> {

    List<InvoiceDispute> findByTenantInvoiceIdIn(Collection<Long> tenantInvoiceIds);

    Optional<InvoiceDispute> findFirstByTenantInvoiceIdOrderByCreatedAtDesc(Long tenantInvoiceId);

    boolean existsByUtilityInvoiceIdAndStatusIn(Long utilityInvoiceId, Collection<InvoiceDisputeStatus> statuses);

    boolean existsByTenantInvoiceIdAndStatusIn(Long tenantInvoiceId, Collection<InvoiceDisputeStatus> statuses);

    @Query("SELECT d.tenantInvoice.id FROM InvoiceDispute d WHERE d.status = :status")
    Set<Long> findTenantInvoiceIdsByStatus(@Param("status") InvoiceDisputeStatus status);

    List<InvoiceDispute> findByTenantContractIdAndStatus(Long tenantContractId, InvoiceDisputeStatus status);

    @Query("""
            SELECT d FROM InvoiceDispute d
            JOIN FETCH d.tenantInvoice ti
            JOIN FETCH d.utilityInvoice ui
            JOIN FETCH d.tenantContract tc
            JOIN FETCH tc.property p
            LEFT JOIN FETCH tc.room
            LEFT JOIN FETCH tc.tenant t
            LEFT JOIN FETCH t.user
            LEFT JOIN FETCH d.replacementInvoice
            ORDER BY d.createdAt DESC
            """)
    List<InvoiceDispute> findAllForAdmin();
}
