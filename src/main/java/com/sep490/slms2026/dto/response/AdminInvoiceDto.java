package com.sep490.slms2026.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class AdminInvoiceDto {
    private String id;
    private String hostId;
    private String hostName;
    private String buildingName;
    private String tenantName;
    private String roomCode;
    private BigDecimal amount;
    private LocalDate dueDate;
    private String status;
}
