package com.sep490.slms2026.dto.response;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class ReceivablesAgingResponse {
    private List<AgingBucket> buckets;
    private List<Debtor> topDebtors;

    @Data
    public static class AgingBucket {
        private String label;
        private BigDecimal amount;
        private Integer count;
    }

    @Data
    public static class Debtor {
        private String tenantName;
        private String propertyName;
        private String roomCode;
        private BigDecimal amount;
        private Integer overdueDays;
    }
}
