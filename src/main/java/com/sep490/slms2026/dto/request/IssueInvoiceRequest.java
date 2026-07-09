package com.sep490.slms2026.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueInvoiceRequest {
    private LocalDate dueDate;
    private String note;
    private List<Long> chargeIds;
}
