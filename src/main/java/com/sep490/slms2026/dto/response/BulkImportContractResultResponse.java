package com.sep490.slms2026.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BulkImportContractResultResponse {

    /** IMPORTED = tạo mới thành công; SKIPPED = mã HĐ đã có trong hệ thống, bỏ qua */
    private String importStatus;
    private String contractCode;
    private Long propertyId;
    private String propertyName;
    private String finalStatus;
    /** Ghi chú (vd. lý do skip) — optional */
    private String message;
}
