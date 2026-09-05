package com.sep490.slms2026.dto.billing;

import com.sep490.slms2026.enums.PaymentCollectionMode;
import lombok.Builder;

import java.util.UUID;

@Builder
public record InvoicePaymentContext(
        PaymentCollectionMode collectionMode,
        UUID remittedBy,
        String remitMethod,
        String payerName,
        String payerPhone,
        UUID facilitatedBy,
        UUID unlockedByAdmin,
        String note
) {
    public static InvoicePaymentContext selfQr() {
        return InvoicePaymentContext.builder()
                .collectionMode(PaymentCollectionMode.SELF)
                .remitMethod("QR")
                .build();
    }
}
