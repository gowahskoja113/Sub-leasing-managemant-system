package com.sep490.slms2026.util;

import com.sep490.slms2026.entity.TenantContract;

import java.math.BigDecimal;
import java.time.YearMonth;

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
     * Preview / tính tiền nhà chu kỳ đầu (pro-rata hoặc full tháng) theo ngày vào ở trên HĐ.
     */
    public static RentFirstCycleCalculator.Result resolveFirstRentCycle(TenantContract contract) {
        if (contract == null || contract.getRentAmount() == null
                || contract.getRentAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return RentFirstCycleCalculator.calculate(null, null, BigDecimal.ZERO, YearMonth.now());
        }
        var anchor = contract.getStartDate() != null ? contract.getStartDate() : contract.getMoveInDate();
        YearMonth ym = anchor != null ? YearMonth.from(anchor) : YearMonth.now();
        return RentFirstCycleCalculator.calculate(contract, ym);
    }

    /**
     * Số tiền nhà chu kỳ đầu gộp vào QR onboard (0 nếu defer ≤3 ngày cuối tháng).
     */
    public static BigDecimal resolveFirstRentAmount(TenantContract contract) {
        RentFirstCycleCalculator.Result r = resolveFirstRentCycle(contract);
        if (r.deferredToNextMonth() || "OUT_OF_MONTH".equals(r.outcome())
                || r.amount() == null || r.amount().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return r.amount();
    }

    /**
     * Tổng phải trả lúc onboard / PayOS: tiền cọc + tiền nhà pro-rata chu kỳ đầu.
     * VD: cọc 10tr + pro-rata 1tr → 11tr trên một QR.
     */
    public static BigDecimal resolveInitialPaymentAmount(TenantContract contract) {
        return resolveDepositAmount(contract).add(resolveFirstRentAmount(contract));
    }
}
