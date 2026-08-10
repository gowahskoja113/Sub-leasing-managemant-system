package com.sep490.slms2026.util;

import com.sep490.slms2026.dto.response.TenantInvoiceItemResponse;
import com.sep490.slms2026.entity.TenantInvoice;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Build dòng chi tiết hoá đơn (tenant + manager/admin).
 * Hoá đơn cọc onboard: note {@code ONBOARD|…}. FIRST cycle: note {@code FIRST_CYCLE|…}.
 */
public final class InvoiceItemBuilder {

    private InvoiceItemBuilder() {
    }

    public static List<TenantInvoiceItemResponse> buildItems(TenantInvoice invoice) {
        List<TenantInvoiceItemResponse> items = new ArrayList<>();
        if (invoice.getNote() != null && invoice.getNote().startsWith("ONBOARD|")) {
            BigDecimal rent = parseOnboardField(invoice.getNote(), "rentAmount");
            BigDecimal deposit = parseOnboardField(invoice.getNote(), "depositAmount");
            String months = parseOnboardFieldRaw(invoice.getNote(), "depositMonths");
            // Data cũ: onboard = tháng đầu + cọc. Data mới: chỉ cọc.
            if (rent != null && rent.compareTo(BigDecimal.ZERO) > 0) {
                items.add(TenantInvoiceItemResponse.builder()
                        .label("Tiền nhà tháng đầu")
                        .amount(rent)
                        .build());
            }
            if (deposit != null) {
                String depositLabel = (months != null && !months.isBlank())
                        ? "Tiền cọc (" + months + " tháng)"
                        : "Tiền cọc";
                items.add(TenantInvoiceItemResponse.builder()
                        .label(depositLabel)
                        .amount(deposit)
                        .build());
            }
            if (!items.isEmpty()) {
                return items;
            }
        }
        if (invoice.getNote() != null && invoice.getNote().startsWith("FIRST_CYCLE|")) {
            BigDecimal rent = parseOnboardField(invoice.getNote(), "rentAmount");
            String days = parseOnboardFieldRaw(invoice.getNote(), "days");
            String daysInMonth = parseOnboardFieldRaw(invoice.getNote(), "daysInMonth");
            if (rent != null && days != null && daysInMonth != null) {
                items.add(TenantInvoiceItemResponse.builder()
                        .label("Giá thuê / tháng")
                        .amount(rent)
                        .build());
                items.add(TenantInvoiceItemResponse.builder()
                        .label("Số ngày tính (" + days + "/" + daysInMonth + ", gồm ngày vào ở)")
                        .amount(null)
                        .build());
                items.add(TenantInvoiceItemResponse.builder()
                        .label("Thành tiền pro-rata")
                        .amount(invoice.getTotalAmount())
                        .build());
                return items;
            }
        }
        switch (invoice.getInvoiceType()) {
            case ELECTRICITY -> items.add(TenantInvoiceItemResponse.builder()
                    .label("Điện (" + formatQty(invoice.getKwhUsed()) + " kWh)")
                    .amount(invoice.getTotalAmount())
                    .build());
            case WATER -> items.add(TenantInvoiceItemResponse.builder()
                    .label("Nước (" + formatQty(invoice.getM3Used()) + " m³)")
                    .amount(invoice.getTotalAmount())
                    .build());
            case RENT -> items.add(TenantInvoiceItemResponse.builder()
                    .label("Tiền thuê phòng")
                    .amount(invoice.getTotalAmount())
                    .build());
            case SERVICE -> items.add(TenantInvoiceItemResponse.builder()
                    .label("Phí dịch vụ")
                    .amount(invoice.getTotalAmount())
                    .build());
            default -> items.add(TenantInvoiceItemResponse.builder()
                    .label("Khoản thu")
                    .amount(invoice.getTotalAmount())
                    .build());
        }
        if (invoice.getLateFee() != null && invoice.getLateFee().compareTo(BigDecimal.ZERO) > 0) {
            items.add(TenantInvoiceItemResponse.builder()
                    .label("Phí trễ hạn")
                    .amount(invoice.getLateFee())
                    .build());
        }
        return items;
    }

    private static BigDecimal parseOnboardField(String note, String key) {
        String raw = parseOnboardFieldRaw(note, key);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String parseOnboardFieldRaw(String note, String key) {
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

    private static String formatQty(BigDecimal qty) {
        if (qty == null) {
            return "0";
        }
        return qty.stripTrailingZeros().toPlainString();
    }
}
