package com.sep490.slms2026.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RentInvoiceSummaryResponse {
    private Long id;
    private Long contractId;
    private String roomNumber;
    private String billingMonth;
    private BigDecimal amount;
    private String status;
    private Boolean autoIssued;
    private LocalDate dueDate;
}
