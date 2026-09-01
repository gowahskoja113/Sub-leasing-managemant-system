package com.sep490.slms2026.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutDamageItemRequest {
    private Long equipmentId;
    /** Link từ outstanding_damage_records / maintenance ticket. */
    private Long maintenanceRequestId;
    private String label;
    private BigDecimal amount;
    private String note;
    private List<String> photos;
}
