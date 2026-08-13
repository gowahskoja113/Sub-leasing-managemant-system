package com.sep490.slms2026.util;

import com.sep490.slms2026.entity.TenantContract;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Vòng lặp tiền nhà REGULAR: phát hành ngày 1, hạn chót ngày 5 hàng tháng.
 * Công tơ / tiện ích vẫn có thể dùng mốc theo {@code startDate} (issue/due helpers bên dưới).
 * Ngày 29/30/31 ở tháng thiếu ngày được kẹp về cuối tháng.
 */
public final class ContractBillingCalendar {

    public static final int REGULAR_RENT_ISSUE_DAY = 1;
    public static final int REGULAR_RENT_DUE_DAY = 5;

    private static final DateTimeFormatter ISO_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter VN_MONTH = DateTimeFormatter.ofPattern("MM/yyyy");

    private ContractBillingCalendar() {
    }

    public static int billingDayOfMonth(TenantContract contract) {
        LocalDate start = contract != null && contract.getStartDate() != null
                ? contract.getStartDate()
                : (contract != null ? contract.getMoveInDate() : null);
        if (start == null) {
            return 1;
        }
        return start.getDayOfMonth();
    }

    public static int clampDay(YearMonth month, int dayOfMonth) {
        if (month == null) {
            return 1;
        }
        return Math.min(Math.max(dayOfMonth, 1), month.lengthOfMonth());
    }

    /** Ngày phát hành hoá đơn REGULAR: luôn ngày 1 của tháng. */
    public static LocalDate regularIssueDate(YearMonth month) {
        if (month == null) {
            return null;
        }
        return month.atDay(REGULAR_RENT_ISSUE_DAY);
    }

    /** Hạn chót REGULAR: ngày 5 (kẹp cuối tháng nếu tháng ngắn). */
    public static LocalDate regularDueDate(YearMonth month) {
        if (month == null) {
            return null;
        }
        return month.atDay(clampDay(month, REGULAR_RENT_DUE_DAY));
    }

    /** Ngày bắt đầu nhắc / phát hành theo mốc billingDay (công tơ). */
    public static LocalDate issueDate(YearMonth month, int billingDay, int reminderLeadDays) {
        int lead = Math.max(reminderLeadDays, 0);
        int actualBilling = clampDay(month, billingDay);
        return month.atDay(clampDay(month, actualBilling - lead));
    }

    /** Hạn chót thanh toán (mốc + grace) — tiện ích / công tơ. */
    public static LocalDate dueDate(YearMonth month, int billingDay, int graceDays) {
        int grace = Math.max(graceDays, 0);
        int actualBilling = clampDay(month, billingDay);
        return month.atDay(clampDay(month, actualBilling + grace));
    }

    public static LocalDate meterDueDate(YearMonth month, int billingDay, int reminderLeadDays) {
        return issueDate(month, billingDay, reminderLeadDays);
    }

    public static LocalDate meterRemindDate(YearMonth month, int billingDay,
                                            int reminderLeadDays, int meterReminderLeadDays) {
        LocalDate due = meterDueDate(month, billingDay, reminderLeadDays);
        return due.minusDays(Math.max(meterReminderLeadDays, 0));
    }

    /**
     * REGULAR từ tháng sau tháng start/move-in, khi {@code today >= ngày 1}.
     * Ngày 6 trở đi vẫn catch-up (hoá đơn sẽ OVERDUE nếu quá ngày 5).
     */
    public static boolean shouldIssueRegularRent(LocalDate today, YearMonth billingMonth,
                                                 TenantContract contract) {
        if (today == null || billingMonth == null || contract == null) {
            return false;
        }
        LocalDate start = contract.getStartDate() != null ? contract.getStartDate() : contract.getMoveInDate();
        if (start == null) {
            return false;
        }
        YearMonth startMonth = YearMonth.from(start);
        if (billingMonth.isBefore(startMonth) || billingMonth.equals(startMonth)) {
            return false;
        }
        if (contract.getEndDate() != null && YearMonth.from(contract.getEndDate()).isBefore(billingMonth)) {
            return false;
        }
        return !today.isBefore(regularIssueDate(billingMonth));
    }

    public static String normalizePeriod(YearMonth month) {
        return month == null ? null : month.toString();
    }

    public static Optional<YearMonth> parsePeriod(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String value = raw.trim();
        try {
            return Optional.of(YearMonth.parse(value, ISO_MONTH));
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return Optional.of(YearMonth.parse(value, VN_MONTH));
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        if (value.matches("\\d{4}/\\d{1,2}")) {
            String[] parts = value.split("/");
            try {
                return Optional.of(YearMonth.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
            } catch (Exception ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
