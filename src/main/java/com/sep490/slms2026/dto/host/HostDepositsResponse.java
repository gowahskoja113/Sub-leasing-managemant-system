package com.sep490.slms2026.dto.host;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Builder
public record HostDepositsResponse(BigDecimal totalHeld, List<DepositItem> items) {
    @Builder
    public record DepositItem(
            String tenantName,
            String propertyName,
            String roomCode,
            BigDecimal amount,
            LocalDate heldSince,
            String status
    ) {
    }
}
