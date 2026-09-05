package com.sep490.slms2026.util;

import com.sep490.slms2026.dto.response.PaymentBreakdownLineResponse;
import com.sep490.slms2026.dto.response.PaymentBreakdownResponse;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.entity.TenantInvoice;
import com.sep490.slms2026.enums.RentCycleType;
import com.sep490.slms2026.enums.TenantInvoiceType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Build {@link PaymentBreakdownResponse} cho contract / invoice — FE render UI “Cách tính tiền”.
 */
public final class PaymentBreakdownBuilder {

    private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private PaymentBreakdownBuilder() {
    }

    /** Cọc + tiền nhà pro-rata chu kỳ đầu — một QR PayOS lúc onboard. */
    public static PaymentBreakdownResponse forDepositOnboard(TenantContract contract) {
        if (contract == null) {
            return null;
        }
        BigDecimal rent = contract.getRentAmount() != null ? contract.getRentAmount() : BigDecimal.ZERO;
        BigDecimal deposit = TenantContractPaymentAmounts.resolveDepositAmount(contract);
        RentFirstCycleCalculator.Result firstRent = TenantContractPaymentAmounts.resolveFirstRentCycle(contract);
        BigDecimal firstRentAmount = TenantContractPaymentAmounts.resolveFirstRentAmount(contract);
        int months = contract.getDepositMonths() != null ? contract.getDepositMonths() : 0;
        boolean fromMonths = (contract.getDeposit() == null || contract.getDeposit().compareTo(BigDecimal.ZERO) <= 0)
                && months > 0 && rent.compareTo(BigDecimal.ZERO) > 0;

        BigDecimal total = deposit.add(firstRentAmount);
        String depositFormula = fromMonths
                ? String.format("%s × %d = %s",
                RentFirstCycleCalculator.formatVn(rent), months, RentFirstCycleCalculator.formatVn(deposit))
                : RentFirstCycleCalculator.formatVn(deposit);
        String formula = firstRentAmount.compareTo(BigDecimal.ZERO) > 0
                ? depositFormula + " + " + (firstRent.formula() != null ? firstRent.formula() : RentFirstCycleCalculator.formatVn(firstRentAmount))
                : depositFormula;

        List<PaymentBreakdownLineResponse> lines = new ArrayList<>();
        if (months > 0) {
            lines.add(line("depositMonths", "Số tháng cọc", String.valueOf(months), null, "tháng"));
        }
        lines.add(line("depositAmount", "Tiền cọc", RentFirstCycleCalculator.formatVn(deposit), deposit, "VND"));
        if (firstRent.deferredToNextMonth()) {
            lines.add(line("firstRentDeferred", "Tiền nhà chu kỳ đầu",
                    "Gộp tháng sau (≤ " + RentFirstCycleCalculator.DEFER_THRESHOLD_DAYS + " ngày cuối tháng)",
                    BigDecimal.ZERO, "VND"));
        } else if (firstRentAmount.compareTo(BigDecimal.ZERO) > 0) {
            if (firstRent.daysInMonth() > 0) {
                lines.add(line("daysInMonth", "Số ngày trong tháng",
                        String.valueOf(firstRent.daysInMonth()), null, "ngày"));
            }
            if (firstRent.billedDays() > 0) {
                lines.add(line("billedDays", "Số ngày tính tiền nhà (gồm ngày vào ở)",
                        String.valueOf(firstRent.billedDays()), null, "ngày"));
            }
            if (firstRent.periodStart() != null && firstRent.periodEnd() != null) {
                lines.add(line("period", "Kỳ tiền nhà chu kỳ đầu",
                        firstRent.periodStart().format(DAY_MONTH_YEAR) + " → "
                                + firstRent.periodEnd().format(DAY_MONTH_YEAR),
                        null, null));
            }
            lines.add(line("firstRentAmount", "Tiền nhà chu kỳ đầu",
                    RentFirstCycleCalculator.formatVn(firstRentAmount), firstRentAmount, "VND"));
        }
        lines.add(line("total", "Tổng QR onboard", RentFirstCycleCalculator.formatVn(total), total, "VND"));

        String explanation = firstRent.deferredToNextMonth()
                ? "QR gồm tiền cọc. Tiền nhà vài ngày cuối tháng sẽ gộp vào hoá đơn tháng sau."
                : (firstRentAmount.compareTo(BigDecimal.ZERO) > 0
                ? "Một lần quét QR gồm tiền cọc và tiền nhà pro-rata chu kỳ đầu. "
                + "Sau OTP xác nhận, không cần thanh toán tiền nhà lần 2 trên app."
                : "QR chỉ gồm tiền cọc (chưa có tiền nhà chu kỳ đầu trong kỳ này).");

        return PaymentBreakdownResponse.builder()
                .kind("DEPOSIT_ONBOARD")
                .title("Thanh toán lúc nhận nhà (cọc + tiền nhà)")
                .formula(formula)
                .explanation(explanation)
                .totalAmount(total)
                .rentAmountMonthly(rent.compareTo(BigDecimal.ZERO) > 0 ? rent : null)
                .depositMonths(months > 0 ? months : null)
                .depositAmount(deposit)
                .dailyRate(firstRent.dailyRate() != null && firstRent.dailyRate().compareTo(BigDecimal.ZERO) > 0
                        ? firstRent.dailyRate() : null)
                .daysInMonth(firstRent.daysInMonth() > 0 ? firstRent.daysInMonth() : null)
                .billedDays(firstRent.billedDays() > 0 ? firstRent.billedDays() : null)
                .periodStart(firstRent.periodStart())
                .periodEnd(firstRent.periodEnd())
                .proRated(firstRent.proRated())
                .deferredToNextMonth(firstRent.deferredToNextMonth())
                .includesMoveInDay(firstRent.billedDays() > 0 ? true : null)
                .lines(lines)
                .build();
    }

    /**
     * Preview tiền nhà chu kỳ đầu (dựa moveIn/startDate) — gắn vào contract response.
     * Dùng cho màn manager “tóm tắt nhận khách” / tenant trước khi có hoá đơn.
     */
    public static PaymentBreakdownResponse forFirstRentPreview(TenantContract contract) {
        if (contract == null || contract.getRentAmount() == null
                || contract.getRentAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        LocalDate anchor = contract.getStartDate() != null ? contract.getStartDate() : contract.getMoveInDate();
        YearMonth ym = anchor != null ? YearMonth.from(anchor) : YearMonth.now();
        RentFirstCycleCalculator.Result r = RentFirstCycleCalculator.calculate(contract, ym);
        return fromFirstCycleResult(r);
    }

    public static PaymentBreakdownResponse fromInvoice(TenantInvoice invoice) {
        if (invoice == null) {
            return null;
        }
        if (invoice.getNote() != null && invoice.getNote().startsWith("ONBOARD|")) {
            return fromOnboardInvoice(invoice);
        }
        if (invoice.getInvoiceType() == TenantInvoiceType.RENT
                && invoice.getCycleType() == RentCycleType.FIRST) {
            return fromFirstCycleInvoice(invoice);
        }
        if (invoice.getInvoiceType() == TenantInvoiceType.RENT) {
            return fromRegularRentInvoice(invoice);
        }
        return PaymentBreakdownResponse.builder()
                .kind("OTHER")
                .title(invoice.getBillingPeriod() != null ? invoice.getBillingPeriod() : "Khoản thu")
                .formula(invoice.getGrandTotal() != null
                        ? RentFirstCycleCalculator.formatVn(invoice.getGrandTotal()) : null)
                .explanation(null)
                .totalAmount(invoice.getGrandTotal())
                .lines(List.of(line("total", "Thành tiền",
                        invoice.getGrandTotal() != null
                                ? RentFirstCycleCalculator.formatVn(invoice.getGrandTotal()) : "0",
                        invoice.getGrandTotal(), "VND")))
                .build();
    }

    private static PaymentBreakdownResponse fromOnboardInvoice(TenantInvoice invoice) {
        BigDecimal deposit = parseNoteAmount(invoice.getNote(), "depositAmount");
        if (deposit == null) {
            deposit = invoice.getGrandTotal() != null ? invoice.getGrandTotal() : BigDecimal.ZERO;
        }
        BigDecimal rentMonthly = parseNoteAmount(invoice.getNote(), "rentAmount");
        BigDecimal firstRentAmount = parseNoteAmount(invoice.getNote(), "firstRentAmount");
        if (firstRentAmount == null) {
            // Legacy: rentAmount trong note = tiền nhà gộp onboard
            BigDecimal legacyRent = parseNoteAmount(invoice.getNote(), "rentAmount");
            if (legacyRent != null && legacyRent.compareTo(deposit) != 0
                    && invoice.getGrandTotal() != null
                    && invoice.getGrandTotal().compareTo(deposit) > 0) {
                firstRentAmount = invoice.getGrandTotal().subtract(deposit);
            }
        }
        Integer billedDays = parseNoteInt(invoice.getNote(), "billedDays");
        Integer daysInMonth = parseNoteInt(invoice.getNote(), "daysInMonth");
        LocalDate periodStart = parseNoteDate(invoice.getNote(), "periodStart");
        LocalDate periodEnd = parseNoteDate(invoice.getNote(), "periodEnd");
        String monthsRaw = parseNoteRaw(invoice.getNote(), "depositMonths");
        Integer months = null;
        try {
            if (monthsRaw != null && !monthsRaw.isBlank()) {
                months = Integer.parseInt(monthsRaw.trim());
            }
        } catch (NumberFormatException ignored) {
            // keep null
        }

        boolean hasFirstRent = firstRentAmount != null && firstRentAmount.compareTo(BigDecimal.ZERO) > 0;
        List<PaymentBreakdownLineResponse> lines = new ArrayList<>();
        if (months != null) {
            lines.add(line("depositMonths", "Số tháng cọc", String.valueOf(months), null, "tháng"));
        }
        lines.add(line("depositAmount", "Tiền cọc", RentFirstCycleCalculator.formatVn(deposit), deposit, "VND"));
        if (hasFirstRent) {
            if (periodStart != null && periodEnd != null) {
                lines.add(line("period", "Kỳ tiền nhà chu kỳ đầu",
                        periodStart.format(DAY_MONTH_YEAR) + " → " + periodEnd.format(DAY_MONTH_YEAR),
                        null, null));
            }
            lines.add(line("firstRentAmount", "Tiền nhà chu kỳ đầu",
                    RentFirstCycleCalculator.formatVn(firstRentAmount), firstRentAmount, "VND"));
        }
        BigDecimal total = invoice.getGrandTotal() != null ? invoice.getGrandTotal() : deposit;
        lines.add(line("total", "Tổng đã thu", RentFirstCycleCalculator.formatVn(total), total, "VND"));

        String formula;
        if (hasFirstRent) {
            formula = RentFirstCycleCalculator.formatVn(deposit) + " + "
                    + RentFirstCycleCalculator.formatVn(firstRentAmount);
        } else if (months != null && rentMonthly != null) {
            formula = String.format("%s × %d", RentFirstCycleCalculator.formatVn(rentMonthly), months);
        } else {
            formula = RentFirstCycleCalculator.formatVn(deposit);
        }

        return PaymentBreakdownResponse.builder()
                .kind("DEPOSIT_ONBOARD")
                .title(hasFirstRent ? "Thanh toán lúc nhận nhà (cọc + tiền nhà)" : "Tiền cọc lúc nhận nhà")
                .formula(formula)
                .explanation(hasFirstRent
                        ? "Đã thu cọc và tiền nhà pro-rata chu kỳ đầu trong một lần thanh toán onboard."
                        : "Chỉ thu tiền cọc lúc onboard.")
                .totalAmount(total)
                .rentAmountMonthly(rentMonthly)
                .depositMonths(months)
                .depositAmount(deposit)
                .daysInMonth(daysInMonth)
                .billedDays(billedDays)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .proRated(hasFirstRent && billedDays != null && daysInMonth != null && billedDays < daysInMonth)
                .lines(lines)
                .build();
    }

    private static PaymentBreakdownResponse fromFirstCycleInvoice(TenantInvoice invoice) {
        // Ưu tiên parse note; fallback suy từ contract + billing month
        Integer billedDays = parseNoteInt(invoice.getNote(), "days");
        Integer daysInMonth = parseNoteInt(invoice.getNote(), "daysInMonth");
        BigDecimal rent = parseNoteAmount(invoice.getNote(), "rentAmount");
        if (rent == null && invoice.getTenantContract() != null) {
            rent = invoice.getTenantContract().getRentAmount();
        }
        LocalDate periodStart = parseNoteDate(invoice.getNote(), "periodStart");
        LocalDate periodEnd = parseNoteDate(invoice.getNote(), "periodEnd");
        if (periodStart == null && invoice.getTenantContract() != null) {
            periodStart = invoice.getTenantContract().getStartDate() != null
                    ? invoice.getTenantContract().getStartDate()
                    : invoice.getTenantContract().getMoveInDate();
        }
        if (periodEnd == null && invoice.getBillingYear() != null && invoice.getBillingMonth() != null) {
            YearMonth ym = YearMonth.of(invoice.getBillingYear(), invoice.getBillingMonth());
            periodEnd = ym.atEndOfMonth();
            if (periodStart == null && billedDays != null) {
                periodStart = periodEnd.minusDays(billedDays - 1L);
            }
        }

        boolean proRated = billedDays != null && daysInMonth != null && billedDays < daysInMonth;
        BigDecimal daily = (rent != null && daysInMonth != null && daysInMonth > 0)
                ? RentFirstCycleCalculator.dailyRate(rent, daysInMonth)
                : null;
        BigDecimal total = invoice.getGrandTotal() != null ? invoice.getGrandTotal() : invoice.getTotalAmount();

        String formula;
        if (proRated && rent != null && daysInMonth != null && billedDays != null) {
            formula = String.format("(%s ÷ %d) × %d = %s",
                    RentFirstCycleCalculator.formatVn(rent), daysInMonth, billedDays,
                    RentFirstCycleCalculator.formatVn(total));
        } else {
            formula = total != null ? RentFirstCycleCalculator.formatVn(total) + " (full tháng)" : null;
        }

        List<PaymentBreakdownLineResponse> lines = new ArrayList<>();
        if (rent != null) {
            lines.add(line("rentAmount", "Giá thuê / tháng", RentFirstCycleCalculator.formatVn(rent), rent, "VND"));
        }
        if (daysInMonth != null) {
            lines.add(line("daysInMonth", "Số ngày trong tháng", String.valueOf(daysInMonth), null, "ngày"));
        }
        if (daily != null) {
            lines.add(line("dailyRate", "Đơn giá / ngày", RentFirstCycleCalculator.formatVn(daily), daily, "VND"));
        }
        if (periodStart != null && periodEnd != null) {
            lines.add(line("period", "Kỳ tính tiền",
                    periodStart.format(DAY_MONTH_YEAR) + " → " + periodEnd.format(DAY_MONTH_YEAR),
                    null, null));
        }
        if (billedDays != null) {
            lines.add(line("billedDays", "Số ngày tính tiền (gồm ngày vào ở)",
                    String.valueOf(billedDays), null, "ngày"));
        }
        lines.add(line("total", "Thành tiền",
                total != null ? RentFirstCycleCalculator.formatVn(total) : "0", total, "VND"));

        return PaymentBreakdownResponse.builder()
                .kind(proRated ? "RENT_FIRST_PRO_RATA" : "RENT_FIRST_FULL")
                .title("Tiền nhà chu kỳ đầu (trước vòng lặp)")
                .formula(formula)
                .explanation("Đây là tiền nhà trước khi vào vòng lặp thu ngày 1–5 hàng tháng. "
                        + "Công thức: (giá thuê tháng ÷ số ngày tháng) × số ngày ở (tính cả ngày vào). "
                        + "Từ tháng sau hoá đơn REGULAR full tháng.")
                .totalAmount(total)
                .rentAmountMonthly(rent)
                .dailyRate(daily)
                .daysInMonth(daysInMonth)
                .billedDays(billedDays)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .includesMoveInDay(true)
                .proRated(proRated)
                .deferredToNextMonth(false)
                .lines(lines)
                .build();
    }

    private static PaymentBreakdownResponse fromFirstCycleResult(RentFirstCycleCalculator.Result r) {
        if (r == null) {
            return null;
        }
        String kind = switch (r.outcome()) {
            case "PRO_RATA" -> "RENT_FIRST_PRO_RATA";
            case "FULL" -> "RENT_FIRST_FULL";
            case "DEFERRED" -> "RENT_FIRST_DEFERRED";
            default -> "RENT_FIRST_PRO_RATA";
        };

        List<PaymentBreakdownLineResponse> lines = new ArrayList<>();
        if (r.rentMonthly() != null && r.rentMonthly().compareTo(BigDecimal.ZERO) > 0) {
            lines.add(line("rentAmount", "Giá thuê / tháng",
                    RentFirstCycleCalculator.formatVn(r.rentMonthly()), r.rentMonthly(), "VND"));
        }
        if (r.daysInMonth() > 0) {
            lines.add(line("daysInMonth", "Số ngày trong tháng",
                    String.valueOf(r.daysInMonth()), null, "ngày"));
            lines.add(line("dailyRate", "Đơn giá / ngày",
                    RentFirstCycleCalculator.formatVn(r.dailyRate()), r.dailyRate(), "VND"));
        }
        if (r.periodStart() != null && r.periodEnd() != null && !"OUT_OF_MONTH".equals(r.outcome())) {
            lines.add(line("period", "Kỳ tính tiền",
                    r.periodStart().format(DAY_MONTH_YEAR) + " → " + r.periodEnd().format(DAY_MONTH_YEAR),
                    null, null));
        }
        if (r.billedDays() > 0) {
            lines.add(line("billedDays", "Số ngày tính tiền (gồm ngày vào ở)",
                    String.valueOf(r.billedDays()), null, "ngày"));
        }
        lines.add(line("total", r.deferredToNextMonth() ? "Tạm chưa thu (gộp tháng sau)" : "Thành tiền dự kiến",
                RentFirstCycleCalculator.formatVn(r.amount()), r.amount(), "VND"));

        return PaymentBreakdownResponse.builder()
                .kind(kind)
                .title("Tiền nhà chu kỳ đầu (trước vòng lặp)")
                .formula(r.formula())
                .explanation(r.explanation())
                .totalAmount(r.amount())
                .rentAmountMonthly(r.rentMonthly())
                .dailyRate(r.dailyRate())
                .daysInMonth(r.daysInMonth() > 0 ? r.daysInMonth() : null)
                .billedDays(r.billedDays() > 0 ? r.billedDays() : null)
                .periodStart(r.periodStart())
                .periodEnd(r.periodEnd())
                .includesMoveInDay(true)
                .proRated(r.proRated())
                .deferredToNextMonth(r.deferredToNextMonth())
                .lines(lines)
                .build();
    }

    private static PaymentBreakdownResponse fromRegularRentInvoice(TenantInvoice invoice) {
        BigDecimal total = invoice.getGrandTotal() != null ? invoice.getGrandTotal() : invoice.getTotalAmount();
        List<PaymentBreakdownLineResponse> lines = new ArrayList<>();
        lines.add(line("total", "Tiền nhà tháng",
                total != null ? RentFirstCycleCalculator.formatVn(total) : "0", total, "VND"));
        return PaymentBreakdownResponse.builder()
                .kind("RENT_REGULAR")
                .title(invoice.getBillingPeriod() != null ? invoice.getBillingPeriod() : "Tiền nhà hàng tháng")
                .formula(total != null ? RentFirstCycleCalculator.formatVn(total) : null)
                .explanation("Hoá đơn vòng lặp định kỳ (thường phát ngày 1, hạn khoảng ngày 5).")
                .totalAmount(total)
                .proRated(false)
                .lines(lines)
                .build();
    }

    private static PaymentBreakdownLineResponse line(String key, String label, String display,
                                                     BigDecimal amount, String unit) {
        return PaymentBreakdownLineResponse.builder()
                .key(key)
                .label(label)
                .displayValue(display)
                .amount(amount)
                .unit(unit)
                .build();
    }

    private static BigDecimal parseNoteAmount(String note, String key) {
        String raw = parseNoteRaw(note, key);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseNoteInt(String note, String key) {
        String raw = parseNoteRaw(note, key);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate parseNoteDate(String note, String key) {
        String raw = parseNoteRaw(note, key);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String parseNoteRaw(String note, String key) {
        if (note == null) {
            return null;
        }
        for (String part : note.split("\\|")) {
            if (part.startsWith(key + "=")) {
                return part.substring(key.length() + 1);
            }
        }
        return null;
    }
}
