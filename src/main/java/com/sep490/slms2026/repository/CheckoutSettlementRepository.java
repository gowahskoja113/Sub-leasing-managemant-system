package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.CheckoutSettlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CheckoutSettlementRepository extends JpaRepository<CheckoutSettlement, Long> {
    Optional<CheckoutSettlement> findByCheckoutRequestId(Long checkoutRequestId);

    @Query("""
            SELECT s FROM CheckoutSettlement s
            JOIN FETCH s.checkoutRequest cr
            JOIN FETCH cr.tenantContract
            WHERE cr.tenantContract.id IN :contractIds
            """)
    List<CheckoutSettlement> findAllByTenantContractIdIn(@Param("contractIds") Collection<Long> contractIds);

    boolean existsByRefundProofHash(String hash);

    /**
     * Host đã chuyển cọc, khách chưa xác nhận / chưa khiếu nại, quá {@code paidBefore}.
     * Dùng cron khoá TK sau 30 ngày im lặng.
     */
    @Query("""
            SELECT s FROM CheckoutSettlement s
            JOIN FETCH s.checkoutRequest cr
            WHERE s.refundPaidAt IS NOT NULL
              AND s.refundPaidAt < :paidBefore
              AND s.refundConfirmedAt IS NULL
              AND s.refundDisputedAt IS NULL
            """)
    List<CheckoutSettlement> findSilentRefundAwaitingAccountDisable(
            @Param("paidBefore") java.time.LocalDateTime paidBefore);
}
