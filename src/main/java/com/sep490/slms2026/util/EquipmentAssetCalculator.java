package com.sep490.slms2026.util;

import com.sep490.slms2026.entity.Equipment;
import com.sep490.slms2026.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Tính khấu hao còn lại + thời hạn bảo hành còn lại của một món thiết bị (đường thẳng theo tháng bảo hành).
 * <p>
 * Quy tắc đền bù:
 * <ul>
 *   <li>Còn bảo hành → giá mua × (tháng còn lại / tổng tháng BH)</li>
 *   <li>Hết BH / không có kỳ BH → {@code penaltyFee} (bắt buộc trên mọi thiết bị)</li>
 *   <li>Không bao giờ fallback về nguyên giá mua</li>
 * </ul>
 */
public final class EquipmentAssetCalculator {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private EquipmentAssetCalculator() {
    }

    public static LocalDate today() {
        return LocalDate.now(VN_ZONE);
    }

    public static LocalDate purchasedAt(Equipment eq) {
        if (eq.getInstallationDate() != null) {
            return eq.getInstallationDate();
        }
        return eq.getWarrantyStartDate();
    }

    public static LocalDate warrantyEndDate(Equipment eq) {
        if (eq.getWarrantyEndDate() != null) {
            return eq.getWarrantyEndDate();
        }
        if (eq.getWarrantyExpiredDate() != null) {
            return eq.getWarrantyExpiredDate();
        }
        LocalDate start = eq.getWarrantyStartDate() != null
                ? eq.getWarrantyStartDate()
                : eq.getInstallationDate();
        if (start != null && eq.getWarrantyMonths() != null && eq.getWarrantyMonths() > 0) {
            return start.plusMonths(eq.getWarrantyMonths());
        }
        return null;
    }

    public static int remainingWarrantyMonths(Equipment eq) {
        return remainingWarrantyMonths(eq, today());
    }

    public static int remainingWarrantyMonths(Equipment eq, LocalDate asOf) {
        LocalDate end = warrantyEndDate(eq);
        if (end == null || asOf == null || !end.isAfter(asOf)) {
            return 0;
        }
        long months = ChronoUnit.MONTHS.between(asOf, end);
        if (asOf.plusMonths(months).isBefore(end)) {
            months += 1;
        }
        return (int) Math.max(0, months);
    }

    public static int remainingWarrantyYears(int remainingMonths) {
        return Math.max(0, remainingMonths) / 12;
    }

    public static String remainingWarrantyLabel(int remainingMonths) {
        if (remainingMonths <= 0) {
            return "Hết bảo hành";
        }
        int years = remainingMonths / 12;
        int months = remainingMonths % 12;
        if (years <= 0) {
            return "Còn " + months + " tháng";
        }
        if (months <= 0) {
            return "Còn " + years + " năm";
        }
        return "Còn " + years + " năm " + months + " tháng";
    }

    /**
     * Khấu hao / giá trị đền còn lại để hiển thị.
     * Hết BH → {@code penaltyFee}; thiếu penaltyFee → {@code 0} (không trả nguyên giá mua).
     */
    public static BigDecimal remainingDepreciationAmount(Equipment eq) {
        return remainingDepreciationAmount(eq, remainingWarrantyMonths(eq));
    }

    public static BigDecimal remainingDepreciationAmount(Equipment eq, int remainingMonths) {
        BigDecimal price = eq.getPrice();
        Integer totalMonths = eq.getWarrantyMonths();
        if (price != null && price.compareTo(BigDecimal.ZERO) > 0
                && totalMonths != null && totalMonths > 0 && remainingMonths > 0) {
            return price.multiply(BigDecimal.valueOf(remainingMonths))
                    .divide(BigDecimal.valueOf(totalMonths), 0, RoundingMode.HALF_UP);
        }
        if (eq.getPenaltyFee() != null && eq.getPenaltyFee().compareTo(BigDecimal.ZERO) >= 0) {
            return eq.getPenaltyFee();
        }
        return BigDecimal.ZERO;
    }

    /**
     * Số tiền đền khi lập hoá đơn / thay thiết bị — bắt buộc có penaltyFee nếu hết BH.
     */
    public static BigDecimal requireCompensationAmount(Equipment eq) {
        BigDecimal amount = remainingDepreciationAmount(eq);
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    "Thiết bị thiếu penaltyFee hợp lệ — mọi thiết bị phải có mức đền bù khi hết bảo hành");
        }
        return amount;
    }

    public static void requirePenaltyFeePresent(BigDecimal penaltyFee) {
        if (penaltyFee == null) {
            throw new BusinessException("penaltyFee là bắt buộc trên mọi thiết bị");
        }
        if (penaltyFee.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("penaltyFee không được âm");
        }
    }
}
