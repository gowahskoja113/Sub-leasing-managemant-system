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

    /**
     * Luồng A (NORMAL_WEAR): manager chọn thu tiền tenant.
     * false/null = giữ hành vi cũ (công ty/chủ nhà trả). Luồng B MANAGER_REPAIR vẫn luôn thu.
     */
    private Boolean chargeToTenant;

    /**
     * Thiết bị hỏng không sửa được, phải thay mới — đền theo {@code Equipment.penaltyFee}
     * (manager có thể ghi đè bằng {@link #estimatedDamageAmount}).
     */
    private Boolean equipmentNeedsReplacement;

    /**
     * Số tiền đền bù khi thay thiết bị. Null → lấy {@code Equipment.penaltyFee}.
     */
    private BigDecimal estimatedDamageAmount;
}
