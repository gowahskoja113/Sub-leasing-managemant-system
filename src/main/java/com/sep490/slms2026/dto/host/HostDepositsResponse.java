package com.sep490.slms2026.dto.host;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Builder
public record HostDepositsResponse(BigDecimal totalHeld, List<DepositItem> items) {
    @Getter
    @Builder
    public static class DepositItem {
        private Long contractId;
        private String contractCode;
        private LocalDate endDate;
        private String tenantName;
        private String propertyName;
        private String roomCode;
        private BigDecimal amount;
        private LocalDate heldSince;
        private String status;
        private Long checkoutRequestId;
        /** Số còn lại cần chuyển cho khách (từ quyết toán). Null nếu chưa có bảng quyết toán. */
        private BigDecimal refundAmount;
        private LocalDate refundedAt;
        private String refundBankName;
        private String refundBankAccount;
        private String refundAccountHolder;
        private Boolean chargesSettled;
        private BigDecimal outstandingAmount;
        private String checkoutNote;
        private LocalDate refundConfirmedAt;
        private LocalDate refundDisputedAt;
        private String refundDisputeReason;
        private LocalDate refundDisputeResolvedAt;
        private String refundDisputeOutcome;
    }
}
