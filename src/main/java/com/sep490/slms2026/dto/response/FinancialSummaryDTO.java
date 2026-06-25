package com.sep490.slms2026.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class FinancialSummaryDTO {
    private String month;
    private BigDecimal revenue;
    private BigDecimal expense;
    private BigDecimal netProfit;
    private Double marginPct;
    private Double occupancyRate;
}
