package com.sep490.slms2026.dto.host;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record HostExpenseDto(
        String id,
        String propertyId,
        String propertyName,
        String category,
        BigDecimal amount,
        String month,
        String note,
        LocalDateTime createdAt
) {
}
