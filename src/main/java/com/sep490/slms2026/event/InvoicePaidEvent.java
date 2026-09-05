package com.sep490.slms2026.event;

import com.sep490.slms2026.dto.billing.InvoicePaymentContext;

/**
 * Quyết định bắn realtime/push khi hoá đơn đã PAID. Listener chỉ chạy AFTER_COMMIT
 * để FE nạp lại không đọc bản chưa commit.
 */
public record InvoicePaidEvent(
        Long invoiceId,
        InvoicePaymentContext context,
        boolean sendPaymentNotification
) {
    public static InvoicePaidEvent of(Long invoiceId, InvoicePaymentContext context) {
        return new InvoicePaidEvent(invoiceId, context, true);
    }

    public static InvoicePaidEvent realtimeOnly(Long invoiceId) {
        return new InvoicePaidEvent(invoiceId, InvoicePaymentContext.selfQr(), false);
    }
}
