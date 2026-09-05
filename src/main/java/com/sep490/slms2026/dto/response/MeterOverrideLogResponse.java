package com.sep490.slms2026.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeterOverrideLogResponse {
    private Long id;
    private UUID managerId;
    private String managerName;
    private Long contractId;
    private String meterKind;
    private BigDecimal enteredValue;
    private String reason;
    private LocalDateTime createdAt;
}
