package com.sep490.slms2026.dto.host;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
public record HostInvoiceDto(
        String id,
        String code,
        String invoiceType,
        Long propertyId,
        String tenantName,
        String roomCode,
        String propertyName,
        String billingPeriod,
        BigDecimal amount,
        LocalDate dueDate,
        String status
) {
}
