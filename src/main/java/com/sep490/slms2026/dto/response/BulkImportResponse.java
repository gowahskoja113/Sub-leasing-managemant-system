package com.sep490.slms2026.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class BulkImportResponse {

    private boolean dryRun;
    /** Số hợp đồng import thành công (không tính skip) */
    private int contractsProcessed;
    /** Số hợp đồng bỏ qua vì mã đã tồn tại trong hệ thống */
    private int contractsSkipped;
    private int renovationLinesImported;
    private int equipmentRowsImported;
    private List<BulkImportContractResultResponse> results;
    private List<BulkImportErrorResponse> errors;
}
