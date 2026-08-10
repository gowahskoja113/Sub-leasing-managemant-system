package com.sep490.slms2026.dto.response;

import lombok.*;

import java.math.BigDecimal;

/**
 * Một dòng chi tiết trên UI (bảng “Cách tính tiền”).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentBreakdownLineResponse {
    /** rentAmount | depositMonths | depositAmount | daysInMonth | dailyRate | billedDays | period | total */
    private String key;
    private String label;
    /** Giá trị hiển thị sẵn (đã format số / ngày). */
    private String displayValue;
    /** Số tiền liên quan (nếu có) — FE format currency. */
    private BigDecimal amount;
    /** VND | ngày | tháng | … */
    private String unit;
}
