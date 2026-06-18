package com.sep490.slms2026.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BulkImportErrorResponse {

    private String sheet;
    private int rowNumber;
    private String contractCode;
    private String field;
    private String message;
}
