package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.MasterLeaseStatus;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class MasterLeaseResponse {
    private Long id;
    private Long propertyId;
    private String propertyName;
    private String ownerName;
    private String ownerPhone;
    private BigDecimal monthlyRent;
    private BigDecimal deposit;
    private Integer paymentDay;
    private LocalDate startDate;
    private LocalDate endDate;
    private Double escalationPct;
    private MasterLeaseStatus status;
    private LocalDateTime createdAt;
}
