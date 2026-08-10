package com.sep490.slms2026.util;

import com.sep490.slms2026.entity.TenantContract;

import java.math.BigDecimal;

/**
 * Tính các khoản tiền liên quan HĐ thuê (onboard, cọc...).
 */
public final class TenantContractPaymentAmounts {

    private TenantContractPaymentAmounts() {
    }

    /**
     * Tiền cọc: ưu tiên field deposit; nếu trống thì = rentAmount × depositMonths.
     */
    public static BigDecimal resolveDepositAmount(TenantContract contract) {
        if (contract.getDeposit() != null && contract.getDeposit().compareTo(BigDecimal.ZERO) > 0) {
            return contract.getDeposit();
        }
        BigDecimal rent = contract.getRentAmount() != null ? contract.getRentAmount() : BigDecimal.ZERO;
        Integer months = contract.getDepositMonths();
        if (months != null && months > 0 && rent.compareTo(BigDecimal.ZERO) > 0) {
            return rent.multiply(BigDecimal.valueOf(months));
        }
        return BigDecimal.ZERO;
    }

    /**
     * Tổng phải trả lúc onboard / PayOS: chỉ tiền cọc.
     * Tiền nhà tháng vào ở (cộng dồn theo ngày) phát hành sau khi HĐ ACTIVE để tenant thanh toán trên app.
     * VD: thuê 5tr, cọc 2 tháng → 10tr lúc onboard.
     */
    public static BigDecimal resolveInitialPaymentAmount(TenantContract contract) {
        return resolveDepositAmount(contract);
    }
}
