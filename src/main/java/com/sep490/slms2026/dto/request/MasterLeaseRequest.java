package com.sep490.slms2026.dto.request;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class MasterLeaseRequest {
    private Long propertyId;
    private String ownerName;
    private String ownerPhone;
    private BigDecimal monthlyRent;
    private BigDecimal deposit;
    private Integer paymentDay;
    private LocalDate startDate;
    private LocalDate endDate;
    private Double escalationPct;
}
