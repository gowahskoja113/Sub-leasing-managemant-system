package com.sep490.slms2026.dto.response;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class DepositLedgerResponse {
    private BigDecimal totalHeld;
    private List<DepositItem> items;

    @Data
    public static class DepositItem {
        private String tenantName;
        private String propertyName;
        private String roomCode;
        private BigDecimal amount;
        private String heldSince;
        private String status;
    }
}
