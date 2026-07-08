package com.sep490.slms2026.dto.host;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record HostExpenseBreakdownResponse(String month, List<ExpenseBreakdownItem> breakdown) {
    @Builder
    public record ExpenseBreakdownItem(String category, BigDecimal amount) {
    }
}
