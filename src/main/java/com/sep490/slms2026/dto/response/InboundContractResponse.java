package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.ContractStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundContractResponse {

    private Long id;
    private Long propertyId;
    private String contractCode;
    private String ownerName;
    private BigDecimal totalRentAmount;
    private LocalDate startDate;
    private LocalDate endDate;
    private String contractScanUrl;
    private ContractStatus status;
}
