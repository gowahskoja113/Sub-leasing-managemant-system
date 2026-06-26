package com.sep490.slms2026.dto.host;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
public record HostInvoiceDto(
        String id,
        String tenantName,
        String roomCode,
        String propertyName,
        BigDecimal amount,
        LocalDate dueDate,
        String status
) {
}
