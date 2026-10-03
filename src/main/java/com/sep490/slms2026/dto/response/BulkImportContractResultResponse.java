package com.sep490.slms2026.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Builder
public class BulkImportContractResultResponse {

    /** IMPORTED = tạo mới; SKIPPED = bỏ qua; CODES_UPDATED = chỉ cập nhật mã KH */
    private String importStatus;
    /** Mã máy đọc được cho lý do skip (vd. ALREADY_IMPORTED, IN_SUPPLEMENT_PHASE) — optional */
    private String code;
    private String contractCode;
    private Long propertyId;
    private String propertyName;
    private String finalStatus;
    /** Ghi chú (vd. lý do skip) — optional */
    private String message;
    /** Chi tiết thay đổi, vd. {@code { electricity: { from, to }, water: { from, to } }} — optional */
    private Map<String, Object> details;
    private Long roomId;
    private String roomNumber;
    private String tenantName;
    private BigDecimal listedPrice;
    private BigDecimal rentAmount;
    private BigDecimal rentEscalationPercent;
    private BigDecimal deltaPercent;
}
