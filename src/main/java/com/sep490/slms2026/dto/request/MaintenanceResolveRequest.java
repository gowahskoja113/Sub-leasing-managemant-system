package com.sep490.slms2026.dto.request;

import com.sep490.slms2026.enums.CostPaidBy;
import com.sep490.slms2026.enums.DamageCause;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class MaintenanceResolveRequest {
    private BigDecimal repairCost;
    private CostPaidBy costPaidBy;
    private DamageCause cause;
    private String resolutionNote;
    private Long equipmentId;
}
