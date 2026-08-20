package com.sep490.slms2026.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutRequestResponse {
    private Long id;
    private Long contractId;
    private String contractCode;
    private String propertyName;
    private String roomNumber;
    private UUID tenantUserId;
    private String tenantFullName;
    private String tenantPhone;
    private LocalDate expectedMoveOutDate;
    private String reason;
    private String note;
    private String status;
    private Integer disputeCount;
    private String disputeReason;
    private java.util.List<String> disputePhotos;
    private LocalDateTime disputedAt;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
    private UUID reviewedBy;
    private String reviewedByName;
    private String managerNote;
    private String rejectReason;
    private LocalDateTime completedAt;

    private String refundBankName;
    private String refundBankAccount;
    private String refundAccountHolder;
    
    private LocalDateTime refundDisputedAt;
    private String refundDisputeReason;
    
    private SettlementDto settlement;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SettlementDto {
        private java.util.List<ChargeItem> finalCharges;
        private java.math.BigDecimal chargesTotal;
        private java.math.BigDecimal chargesPaid;
        private Boolean chargesSettled;

        private java.math.BigDecimal depositAmount;
        private java.time.LocalDate refundDueDate;

        private LocalDateTime refundPaidAt;
        private String refundProofUrl;
        private LocalDateTime refundConfirmedAt;
        private LocalDateTime refundDisputedAt;
        private String refundDisputeReason;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChargeItem {
        private Long id;
        private String code;
        private String type;
        private java.math.BigDecimal amount;
    }
}
