package com.sep490.slms2026.dto.host;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record HostCashflowResponse(List<CashflowPoint> series) {
    @Builder
    public record CashflowPoint(String month, BigDecimal revenue, BigDecimal expense) {
    }
}
