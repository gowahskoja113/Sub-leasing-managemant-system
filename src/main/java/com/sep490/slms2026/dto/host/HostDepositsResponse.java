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
    }
}
