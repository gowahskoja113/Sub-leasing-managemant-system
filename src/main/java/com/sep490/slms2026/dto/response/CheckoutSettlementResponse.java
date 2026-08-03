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
    private BigDecimal depositAmount;
    private List<InvoiceResponse> unpaidInvoices;
    private BigDecimal unpaidTotal;
    private List<DamageResponse> damages;
    private BigDecimal damageTotal;
    private List<AdjustmentResponse> adjustments;
    private BigDecimal adjustmentTotal;
    private BigDecimal refundAmount;
    private BigDecimal extraChargeAmount;
    private Long extraChargeInvoiceId;

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
