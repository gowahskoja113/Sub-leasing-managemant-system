package com.sep490.slms2026.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OcrUtilityBillResponse {
    private BigDecimal totalQuantity;
    private BigDecimal totalAmount;
    private String billingPeriod;
    /** Mã khách hàng đọc từ giấy (nếu OCR nhận ra) — FE cho sửa trước khi publish. */
    private String customerCode;
    /** Chỉ số cũ trên giấy — FE confirm/sửa nếu OCR sai. */
    private BigDecimal prevReading;
    /** Chỉ số mới trên giấy — FE confirm/sửa nếu OCR sai. */
    private BigDecimal newReading;
    /**
     * 3 field FE nên luôn hiện bước confirm trước khi gọi publish:
     * customerCode, prevReading, newReading.
     */
    private List<String> fieldsToConfirm;
    private String rawText;
}

