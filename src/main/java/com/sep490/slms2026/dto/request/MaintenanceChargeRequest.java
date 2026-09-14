package com.sep490.slms2026.dto.request;

import com.sep490.slms2026.enums.DamageCause;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class MaintenanceChargeRequest {
    private String invoiceVendor;
    private String invoiceNumber;
    private LocalDate invoiceDate;
    private BigDecimal invoiceAmount;
    private Boolean equipmentNeedsReplacement;
    private DamageCause damageCause;
}
