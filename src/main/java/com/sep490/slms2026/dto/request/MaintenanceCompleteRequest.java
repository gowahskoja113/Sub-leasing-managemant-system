package com.sep490.slms2026.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class MaintenanceCompleteRequest {
    private String resolutionNote;
    private String repairDescription;
    private List<String> afterImages;
    private List<String> invoiceImages;
    private String invoiceVendor;
    private String invoiceNumber;
    private LocalDate invoiceDate;
    private BigDecimal invoiceAmount;
}
