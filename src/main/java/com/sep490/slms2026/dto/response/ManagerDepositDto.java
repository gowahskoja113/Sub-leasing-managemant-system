package com.sep490.slms2026.dto.response;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManagerDepositDto {
    private Long contractId;
    private String contractCode;
    private String propertyName;
    private String roomNumber;
    private String tenantName;
    private String tenantPhone;
    private Integer depositMonths;
    private String paymentStatus;
    private String depositMethod;
    private LocalDateTime depositPaidAt;
    private String contractStatus;
    private LocalDate moveInDate;
}
