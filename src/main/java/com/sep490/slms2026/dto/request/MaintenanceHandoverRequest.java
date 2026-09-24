package com.sep490.slms2026.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class MaintenanceHandoverRequest {
    private List<String> handoverImages;

    /** Ảnh hoá đơn (tách riêng ảnh AFTER / bàn giao — giống complete). */
    private List<String> invoiceImages;
    private String invoiceVendor;
    private String invoiceNumber;
    private LocalDate invoiceDate;
    private BigDecimal invoiceAmount;
    private String repairDescription;
}
