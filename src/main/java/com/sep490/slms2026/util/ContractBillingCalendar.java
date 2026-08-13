package com.sep490.slms2026.util;

import com.sep490.slms2026.entity.TenantContract;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Mốc thu tiền theo ngày hiệu lực từng HĐ (mentor 12/08):
 * ngày đóng tiền = ngày trong tháng của {@code startDate};
 * bắt đầu nhắc = mốc − {@code reminderLeadDays};
 * hạn chót = mốc + {@code graceDays} (quá hạn mới OVERDUE).
 * Ngày 29/30/31 ở tháng thiếu ngày được kẹp về cuối tháng.
 */
public final class ContractBillingCalendar {

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

    /** Ngày bắt đầu nhắc / phát hành hoá đơn REGULAR trong tháng. */
    public static LocalDate issueDate(YearMonth month, int billingDay, int reminderLeadDays) {
        int lead = Math.max(reminderLeadDays, 0);
        int actualBilling = clampDay(month, billingDay);
        return month.atDay(clampDay(month, actualBilling - lead));
    }

    /** Hạn chót thanh toán (mốc + grace). */
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

    public static boolean shouldIssueRegularRent(LocalDate today, YearMonth billingMonth,
                                                 TenantContract contract, int reminderLeadDays) {
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
        LocalDate issue = issueDate(billingMonth, billingDayOfMonth(contract), reminderLeadDays);
        return !today.isBefore(issue);
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
