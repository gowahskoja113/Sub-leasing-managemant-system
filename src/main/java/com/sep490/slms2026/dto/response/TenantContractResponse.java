package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.ContractStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantContractResponse {

    private Long id;
    private Long propertyId;
    private Long roomId;
    private String roomNumber;

    private UUID tenantUserId;
    private String tenantFullName;
    private String tenantPhone;
    private String tenantCccd;

    private String contractCode;
    private BigDecimal rentAmount;
    private BigDecimal deposit;
    private LocalDate moveInDate;
    private LocalDate startDate;
    private LocalDate endDate;
    private ContractStatus status;
    private String equipmentSnapshot;
}
