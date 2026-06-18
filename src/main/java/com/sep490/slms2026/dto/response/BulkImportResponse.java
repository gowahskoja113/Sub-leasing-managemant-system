package com.sep490.slms2026.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class BulkImportResponse {

    private boolean dryRun;
    private int contractsProcessed;
    private int renovationLinesImported;
    private int equipmentRowsImported;
    private List<BulkImportContractResultResponse> results;
    private List<BulkImportErrorResponse> errors;
}
