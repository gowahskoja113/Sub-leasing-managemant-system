package com.sep490.slms2026.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutstandingDamageResponse {
    private Long id;
    private Long maintenanceRequestId;
    private Long tenantContractId;
    private Long equipmentId;
    private String label;
    private BigDecimal estimatedAmount;
    private String note;
    private List<String> photos;
    private LocalDateTime createdAt;
}
