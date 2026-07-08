package com.sep490.slms2026.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UtilityInvoiceHistoryResponse {
    private List<UtilityInvoiceResponse> items;
    private long totalCount;
    private BigDecimal totalAmount;
    private long roomCount;
}
