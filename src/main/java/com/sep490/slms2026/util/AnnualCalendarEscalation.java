package com.sep490.slms2026.util;

import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.enums.RentEscalationType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Tăng giá thuê theo năm dương lịch (01/01) + ân hạn + báo giá năm sau.
 */
public final class AnnualCalendarEscalation {

    private AnnualCalendarEscalation() {
    }

    public static boolean isAnnualCalendar(TenantContract contract) {
        return contract != null
                && contract.getRentEscalationType() == RentEscalationType.ANNUAL_CALENDAR;
    }

    public static boolean hasPositivePercent(TenantContract contract) {
        return contract != null
                && contract.getRentEscalationPercent() != null
                && contract.getRentEscalationPercent().compareTo(BigDecimal.ZERO) > 0;
    }

    /** Số tháng tròn từ ngày bắt đầu thuê tới mốc 01/01 năm Y. */
    public static long monthsTenantedByJan1(LocalDate startDate, int year) {
        if (startDate == null) {
            return 0;
        }
        LocalDate jan1 = LocalDate.of(year, 1, 1);
        if (startDate.isAfter(jan1)) {
            return 0;
        }
        return ChronoUnit.MONTHS.between(startDate, jan1);
    }

    public static boolean isDeferredByGrace(LocalDate startDate, int year, int graceMonths) {
        return monthsTenantedByJan1(startDate, year) < graceMonths;
    }

    public static boolean alreadyEscalatedForYear(TenantContract contract, int year) {
        Integer last = contract.getLastEscalationYear();
        return last != null && last >= year;
    }

    /**
     * Năm giá phải báo khách khi ký vào {@code signDate}.
     * leadMonths=2 → từ 01/11 báo giá năm sau (01/01 năm sau trừ leadMonths).
     */
    public static int quotePriceYear(LocalDate signDate, int leadMonths) {
        if (signDate == null) {
            return LocalDate.now().getYear();
        }
        int lead = Math.max(0, Math.min(11, leadMonths));
        LocalDate threshold = LocalDate.of(signDate.getYear() + 1, 1, 1).minusMonths(lead);
        return !signDate.isBefore(threshold) ? signDate.getYear() + 1 : signDate.getYear();
    }

    public static BigDecimal compound(BigDecimal base, BigDecimal percent, int years) {
        if (base == null || years <= 0) {
            return base;
        }
        BigDecimal pct = percent != null ? percent : BigDecimal.ZERO;
        BigDecimal factor = BigDecimal.ONE.add(
                pct.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP));
        BigDecimal result = base;
        for (int i = 0; i < years; i++) {
            result = result.multiply(factor).setScale(0, RoundingMode.HALF_UP);
        }
        return result;
    }

    public static BigDecimal applyOneYearIncrease(BigDecimal amount, BigDecimal percent) {
        return compound(amount, percent, 1);
    }

    /**
     * Ngày tăng kế tiếp (01/01 năm sau hoặc năm sau nữa nếu đang trong ân hạn).
     * Null nếu không tăng.
     */
    public static LocalDate nextEscalationDate(TenantContract contract, int graceMonths, LocalDate today) {
        if (!isAnnualCalendar(contract) || !hasPositivePercent(contract) || contract.getStartDate() == null) {
            return null;
        }
        int year = today.getYear() + 1;
        // Nếu hôm nay đã qua 01/01 năm này và chưa escalate năm này → còn có thể là năm nay
        // Nhưng cron chỉ chạy 01/01; với today sau 01/01, next là năm sau.
        if (today.getMonthValue() == 1 && today.getDayOfMonth() == 1) {
            year = today.getYear();
        }
        Integer last = contract.getLastEscalationYear();
        if (last != null && last >= year) {
            year = last + 1;
        }
        // Tìm năm đầu tiên không bị ân hạn và chưa escalate
        for (int i = 0; i < 30; i++) {
            int candidate = year + i;
            if (alreadyEscalatedForYear(contract, candidate)) {
                continue;
            }
            if (!isDeferredByGrace(contract.getStartDate(), candidate, graceMonths)) {
                return LocalDate.of(candidate, 1, 1);
            }
        }
        return null;
    }

    public static BigDecimal nextEscalationAmount(TenantContract contract, int graceMonths, LocalDate today) {
        LocalDate next = nextEscalationDate(contract, graceMonths, today);
        if (next == null || contract.getRentAmount() == null) {
            return null;
        }
        return applyOneYearIncrease(contract.getRentAmount(), contract.getRentEscalationPercent());
    }
}
