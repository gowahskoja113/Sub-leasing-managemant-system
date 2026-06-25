package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.InvoiceStatus;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class InvoiceResponse {
    private String id; // usually String representation like "HD-2026-001"
    private String tenantName;
    private String roomCode;
    private String propertyName;
    private BigDecimal amount;
    private LocalDate dueDate;
    private InvoiceStatus status;
}
