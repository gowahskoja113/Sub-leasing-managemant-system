package com.sep490.slms2026.dto.host;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record HostPropertyPnlResponse(
        String month,
        List<PropertyPnlRow> rows,
        PropertyPnlTotals totals
) {
    @Builder
    public record PropertyPnlRow(
            String propertyId,
            String propertyName,
            BigDecimal revenue,
            BigDecimal leaseCost,
            BigDecimal otherExpense,
            BigDecimal totalExpense,
            BigDecimal net,
            BigDecimal marginPct
    ) {
    }

    @Builder
    public record PropertyPnlTotals(
            BigDecimal revenue,
            BigDecimal leaseCost,
            BigDecimal otherExpense,
            BigDecimal totalExpense,
            BigDecimal net,
            BigDecimal marginPct
    ) {
    }
}
