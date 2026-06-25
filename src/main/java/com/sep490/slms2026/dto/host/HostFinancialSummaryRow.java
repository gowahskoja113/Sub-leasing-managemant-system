package com.sep490.slms2026.dto.host;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record HostFinancialSummaryRow(
        String month,
        BigDecimal revenue,
        BigDecimal expense,
        BigDecimal netProfit,
        BigDecimal marginPct,
        BigDecimal occupancyRate
) {
}
