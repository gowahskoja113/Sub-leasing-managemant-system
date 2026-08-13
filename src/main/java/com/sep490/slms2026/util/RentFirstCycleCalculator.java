package com.sep490.slms2026.util;

import com.sep490.slms2026.entity.TenantContract;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Tính tiền nhà chu kỳ đầu (trước vòng lặp thu ngày 1–5 hàng tháng).
 * Dùng chung khi preview trên HĐ và khi phát hành hoá đơn FIRST.
 */
public final class RentFirstCycleCalculator {

    public static final int DEFER_THRESHOLD_DAYS = 3;

    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private RentFirstCycleCalculator() {
    }

    public record Result(
            /** OUT_OF_MONTH | DEFERRED | PRO_RATA | FULL */
            String outcome,
            LocalDate periodStart,
            LocalDate periodEnd,
            int billedDays,
            int daysInMonth,
            BigDecimal rentMonthly,
            BigDecimal dailyRate,
            BigDecimal amount,
            boolean proRated,
            boolean deferredToNextMonth,
            String formula,
            String explanation
    ) {
    }

    /**
     * @param asOfMonth tháng tham chiếu (thường {@link YearMonth#now()} lúc ACTIVE / preview)
     */
    public static Result calculate(TenantContract contract, YearMonth asOfMonth) {
        if (contract == null || contract.getRentAmount() == null
                || contract.getRentAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return empty();
        }
        LocalDate billStart = contract.getStartDate() != null
                ? contract.getStartDate()
                : contract.getMoveInDate();
        if (billStart == null || asOfMonth == null) {
            return empty();
        }
        return calculate(
                billStart,
                contract.getEndDate(),
                contract.getRentAmount(),
                asOfMonth);
    }

    public static Result calculate(LocalDate billStart, LocalDate contractEndDate,
                                   BigDecimal rentMonthly, YearMonth asOfMonth) {
        if (billStart == null || rentMonthly == null || asOfMonth == null
                || rentMonthly.compareTo(BigDecimal.ZERO) <= 0) {
            return empty();
        }

        LocalDate startOfMonth = asOfMonth.atDay(1);
        LocalDate endOfMonth = asOfMonth.atEndOfMonth();
        LocalDate billEnd = contractEndDate != null && contractEndDate.isBefore(endOfMonth)
                ? contractEndDate
                : endOfMonth;

        if (billStart.isAfter(billEnd) || billStart.isBefore(startOfMonth)) {
            return new Result(
                    "OUT_OF_MONTH",
                    billStart,
                    billEnd,
                    0,
                    asOfMonth.lengthOfMonth(),
                    rentMonthly,
                    dailyRate(rentMonthly, asOfMonth.lengthOfMonth()),
                    BigDecimal.ZERO,
                    false,
                    false,
                    null,
                    "Ngày vào ở không thuộc tháng phát hành này — chưa có tiền nhà chu kỳ đầu trong tháng hiện tại."
            );
        }

        int daysInMonth = asOfMonth.lengthOfMonth();
        long days = ChronoUnit.DAYS.between(billStart, billEnd) + 1;
        int billedDays = (int) days;
        BigDecimal daily = dailyRate(rentMonthly, daysInMonth);

        if (billedDays <= DEFER_THRESHOLD_DAYS && contractEndDate == null) {
            return new Result(
                    "DEFERRED",
                    billStart,
                    billEnd,
                    billedDays,
                    daysInMonth,
                    rentMonthly,
                    daily,
                    BigDecimal.ZERO,
                    true,
                    true,
                    null,
                    "Vào ở còn ≤ " + DEFER_THRESHOLD_DAYS
                            + " ngày trong tháng — BE không phát hoá đơn lẻ. Số ngày này gộp vào hoá đơn tháng sau (vòng lặp 1–5)."
            );
        }

        boolean proRated = billedDays < daysInMonth;
        BigDecimal amount = rentMonthly;
        if (proRated) {
            amount = rentMonthly
                    .multiply(BigDecimal.valueOf(billedDays))
                    .divide(BigDecimal.valueOf(daysInMonth), 0, RoundingMode.HALF_UP);
        }

        String formula;
        String explanation;
        String outcome;
        if (proRated) {
            outcome = "PRO_RATA";
            formula = String.format("(%s ÷ %d) × %d = %s",
                    formatVn(rentMonthly), daysInMonth, billedDays, formatVn(amount));
            explanation = String.format(
                    "Tiền nhà trước vòng lặp: tính theo ngày từ %s đến %s (%d/%d ngày, gồm ngày vào ở). "
                            + "Từ tháng sau thu full theo vòng lặp ngày 1–5.",
                    billStart.format(DAY_MONTH_YEAR),
                    billEnd.format(DAY_MONTH_YEAR),
                    billedDays,
                    daysInMonth);
        } else {
            outcome = "FULL";
            formula = formatVn(rentMonthly) + " (full tháng)";
            explanation = "Vào từ đầu tháng — thu đủ 1 tháng tiền nhà. Từ tháng sau theo vòng lặp ngày 1–5.";
        }

        return new Result(
                outcome,
                billStart,
                billEnd,
                billedDays,
                daysInMonth,
                rentMonthly,
                daily,
                amount,
                proRated,
                false,
                formula,
                explanation
        );
    }

    public static String periodLabel(Result r) {
        if (r == null || r.periodStart() == null || r.periodEnd() == null) {
            return null;
        }
        if (r.proRated()) {
            return String.format("Tiền nhà %s–%s (%d/%d ngày)",
                    r.periodStart().format(DAY_MONTH),
                    r.periodEnd().format(DAY_MONTH_YEAR),
                    r.billedDays(),
                    r.daysInMonth());
        }
        if (r.periodStart() != null) {
            YearMonth ym = YearMonth.from(r.periodStart());
            return "Tiền nhà tháng " + String.format("%02d/%d", ym.getMonthValue(), ym.getYear());
        }
        return null;
    }

    public static BigDecimal dailyRate(BigDecimal rentMonthly, int daysInMonth) {
        if (rentMonthly == null || daysInMonth <= 0) {
            return BigDecimal.ZERO;
        }
        return rentMonthly.divide(BigDecimal.valueOf(daysInMonth), 0, RoundingMode.HALF_UP);
    }

    /**
     * Ngày defer của tháng trước (nếu chu kỳ đầu là {@code DEFERRED}) cần cộng vào hoá đơn REGULAR
     * của {@code billingMonth}. Dùng chung cho cron ngày 1 và catch-up khi HĐ ACTIVE trễ.
     */
    public record DeferredCarryOver(BigDecimal amount, int days, YearMonth fromMonth) {
        public static DeferredCarryOver none() {
            return new DeferredCarryOver(BigDecimal.ZERO, 0, null);
        }

        public boolean present() {
            return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
        }
    }

    public static DeferredCarryOver deferredCarryOver(TenantContract contract, YearMonth billingMonth) {
        if (contract == null || billingMonth == null || contract.getRentAmount() == null
                || contract.getRentAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return DeferredCarryOver.none();
        }
        YearMonth prevMonth = billingMonth.minusMonths(1);
        Result first = calculate(contract, prevMonth);
        if (!first.deferredToNextMonth() || first.billedDays() <= 0 || first.daysInMonth() <= 0) {
            return DeferredCarryOver.none();
        }
        BigDecimal amount = contract.getRentAmount()
                .multiply(BigDecimal.valueOf(first.billedDays()))
                .divide(BigDecimal.valueOf(first.daysInMonth()), 0, RoundingMode.HALF_UP);
        return new DeferredCarryOver(amount, first.billedDays(), prevMonth);
    }

    /** Full tháng + ngày defer tháng trước (nếu có). */
    public static BigDecimal regularRentAmount(TenantContract contract, YearMonth billingMonth) {
        if (contract == null || contract.getRentAmount() == null
                || contract.getRentAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return contract.getRentAmount().add(deferredCarryOver(contract, billingMonth).amount());
    }

    /**
     * Hạn hoá đơn REGULAR: ngày {@code dueDayOfMonth} của tháng billing (mặc định 5).
     * Nếu ngày đó đã qua (catch-up giữa tháng / bù tháng cũ) thì {@code today + graceDays}
     * để tenant không bị OVERDUE ngay lúc phát hành.
     */
    public static LocalDate regularRentDueDate(YearMonth billingMonth, int dueDayOfMonth,
                                               long graceDaysAfterDuePassed, LocalDate today) {
        if (billingMonth == null || today == null) {
            return today;
        }
        int day = Math.min(Math.max(dueDayOfMonth, 1), billingMonth.lengthOfMonth());
        LocalDate due = billingMonth.atDay(day);
        if (!due.isBefore(today)) {
            return due;
        }
        return today.plusDays(Math.max(graceDaysAfterDuePassed, 0));
    }

    private static Result empty() {
        return new Result(
                "OUT_OF_MONTH",
                null,
                null,
                0,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                false,
                null,
                "Chưa đủ dữ liệu để tính tiền nhà chu kỳ đầu."
        );
    }

    /** Format gọn cho formula (không locale phụ thuộc). */
    public static String formatVn(BigDecimal amount) {
        if (amount == null) {
            return "0";
        }
        return amount.stripTrailingZeros().toPlainString().replaceAll("\\B(?=(\\d{3})+(?!\\d))", ".");
    }
}
