package com.sep490.slms2026.dto.response;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class PropertyPnlResponse {
    private String month;
    private List<PropertyPnlRow> rows;
    private PnlTotals totals;

    @Data
    public static class PropertyPnlRow {
        private Long propertyId;
        private String propertyName;
        private BigDecimal revenue;
        private BigDecimal leaseCost;
        private BigDecimal otherExpense;
        private BigDecimal totalExpense;
        private BigDecimal net;
        private Double marginPct;
    }

    @Data
    public static class PnlTotals {
        private BigDecimal revenue;
        private BigDecimal leaseCost;
        private BigDecimal otherExpense;
        private BigDecimal totalExpense;
        private BigDecimal net;
        private Double marginPct;
    }
}
