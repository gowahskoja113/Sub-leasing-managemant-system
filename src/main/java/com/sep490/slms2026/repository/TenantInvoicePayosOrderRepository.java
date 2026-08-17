package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.TenantInvoicePayosOrder;
import com.sep490.slms2026.enums.PayosOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TenantInvoicePayosOrderRepository extends JpaRepository<TenantInvoicePayosOrder, Long> {

    Optional<TenantInvoicePayosOrder> findByOrderCode(Long orderCode);

    List<TenantInvoicePayosOrder> findByInvoiceIdAndStatus(Long invoiceId, PayosOrderStatus status);

    @Modifying
    @Query("""
            UPDATE TenantInvoicePayosOrder o SET o.status = :newStatus
            WHERE o.invoice.id = :invoiceId AND o.status = :currentStatus
            """)
    int updateStatusForInvoice(
            @Param("invoiceId") Long invoiceId,
            @Param("currentStatus") PayosOrderStatus currentStatus,
            @Param("newStatus") PayosOrderStatus newStatus);

    @Modifying
    @Query("""
            UPDATE TenantInvoicePayosOrder o SET o.status = 'EXPIRED'
            WHERE o.status = 'ACTIVE' AND o.expiredAt IS NOT NULL AND o.expiredAt < :now
            """)
    int expireStaleOrders(@Param("now") LocalDateTime now);
}
