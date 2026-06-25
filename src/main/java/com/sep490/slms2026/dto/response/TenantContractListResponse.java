package com.sep490.slms2026.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class TenantContractListResponse {
    private Long id;
    private String code;
    private String type; // WHOLE_HOUSE or ROOM
    private String propertyName;
    private String roomCode;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal rentAmount;
    private BigDecimal depositAmount;
    private String status;
}
