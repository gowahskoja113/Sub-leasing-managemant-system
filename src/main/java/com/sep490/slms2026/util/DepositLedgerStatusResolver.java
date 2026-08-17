package com.sep490.slms2026.util;

import com.sep490.slms2026.entity.CheckoutSettlement;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.enums.CheckoutRequestStatus;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.enums.DepositStatus;
import com.sep490.slms2026.enums.PaymentStatus;

import java.math.BigDecimal;

/**
 * Suy trạng thái cọc trên Sổ cọc host từ {@code paymentStatus} + quyết toán trả phòng —
 * không suy từ {@link ContractStatus} một mình.
 */
public final class DepositLedgerStatusResolver {

    private DepositLedgerStatusResolver() {
    }

    public static DepositStatus resolve(TenantContract contract,
                                        CheckoutSettlement settlement,
                                        CheckoutRequestStatus checkoutStatus) {
        if (contract == null || contract.getPaymentStatus() != PaymentStatus.PAID) {
            return DepositStatus.NOT_COLLECTED;
        }
        if (!isContractClosed(contract.getStatus())) {
            return DepositStatus.HELD;
        }
        if (settlement == null) {
            return DepositStatus.HELD;
        }
        return resolveClosedContract(settlement, checkoutStatus);
    }

    private static boolean isContractClosed(ContractStatus status) {
        return status == ContractStatus.TERMINATED || status == ContractStatus.EXPIRED;
    }

    private static DepositStatus resolveClosedContract(CheckoutSettlement settlement,
                                                       CheckoutRequestStatus checkoutStatus) {
        if (settlement.getRefundPaidAt() != null) {
            return DepositStatus.REFUNDED;
        }
        if (checkoutStatus != CheckoutRequestStatus.COMPLETED) {
            return DepositStatus.HELD;
        }

        BigDecimal refund = nz(settlement.getRefundAmount());
        BigDecimal extra = nz(settlement.getExtraChargeAmount());
        BigDecimal damage = nz(settlement.getDamageTotal());
        BigDecimal unpaid = nz(settlement.getUnpaidTotal());

        if (refund.compareTo(BigDecimal.ZERO) > 0) {
            return DepositStatus.HELD;
        }
        if (extra.compareTo(BigDecimal.ZERO) > 0
                || damage.compareTo(BigDecimal.ZERO) > 0
                || unpaid.compareTo(BigDecimal.ZERO) > 0) {
            return DepositStatus.FORFEITED;
        }
        return DepositStatus.REFUNDED;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
