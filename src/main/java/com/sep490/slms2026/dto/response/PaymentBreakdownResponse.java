package com.sep490.slms2026.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Cách tính tiền minh bạch cho FE (cọc onboard / tiền nhà pro-rata trước vòng lặp / đầy đủ tháng).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentBreakdownResponse {

    /**
     * DEPOSIT_ONBOARD — QR cọc lúc onboard (chỉ cọc).<br>
     * RENT_FIRST_PRO_RATA — tiền nhà lần đầu, chia theo ngày (trước vòng lặp 1–5).<br>
     * RENT_FIRST_FULL — vào đúng từ đầu tháng → full tháng.<br>
     * RENT_FIRST_DEFERRED — ≤3 ngày cuối tháng → gộp hoá đơn tháng sau.<br>
     * RENT_REGULAR — hoá đơn tháng lặp.<br>
     * OTHER — fallback.
     */
    private String kind;

    /** Tiêu đề ngắn (VD: "Tiền cọc lúc nhận nhà"). */
    private String title;

    /**
     * Công thức toán dạng text, FE có thể hiện mono / footer.
     * VD: {@code (5.000.000 ÷ 30) × 6} hoặc {@code 5.000.000 × 2}.
     */
    private String formula;

    /** Giải thích tiếng Việt 1–2 câu cho UI/tooltip. */
    private String explanation;

    private BigDecimal totalAmount;

    // —— chi tiết có cấu trúc (null khi không áp dụng) ——
    private BigDecimal rentAmountMonthly;
    private Integer depositMonths;
    private BigDecimal depositAmount;
    /** rentAmountMonthly / daysInMonth (làm tròn VND). */
    private BigDecimal dailyRate;
    private Integer daysInMonth;
    /** Số ngày tính tiền (inclusive, gồm ngày vào ở). */
    private Integer billedDays;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    /** Luôn true với pro-rata hiện tại: inclusive cả ngày move-in. */
    private Boolean includesMoveInDay;
    private Boolean proRated;
    /** true nếu ≤3 ngày cuối tháng, BE chưa phát hoá đơn FIRST (sẽ gộp tháng sau). */
    private Boolean deferredToNextMonth;

    /** Dòng chi tiết sẵn cho list/table UI. */
    private List<PaymentBreakdownLineResponse> lines;
}
