package com.sep490.slms2026.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutSettlementResponse {
    // Khối A - Khách phải trả
    private List<InvoiceResponse> finalCharges;
    private BigDecimal chargesTotal;
    private BigDecimal chargesPaid;
    private Boolean chargesSettled;

    // Khối B - Công ty phải hoàn
    private BigDecimal depositAmount;
    private List<AdjustmentResponse> adjustments;
    private BigDecimal adjustmentTotal;

    private String refundProofUrl;
    private java.time.LocalDate refundedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvoiceResponse {
        private Long id;
        private String code;
        private String type;
        private BigDecimal amount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DamageResponse {
        private String label;
        private BigDecimal amount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdjustmentResponse {
        private String label;
        private BigDecimal amount;
    }
}
