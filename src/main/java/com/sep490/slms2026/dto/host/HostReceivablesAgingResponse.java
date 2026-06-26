package com.sep490.slms2026.dto.host;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record HostReceivablesAgingResponse(
        List<AgingBucket> buckets,
        List<TopDebtor> topDebtors
) {
    @Builder
    public record AgingBucket(String label, BigDecimal amount, long count) {
    }

    @Builder
    public record TopDebtor(
            String tenantName,
            String propertyName,
            String roomCode,
            BigDecimal amount,
            long overdueDays
    ) {
    }
}
