package com.sep490.slms2026.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BulkImportContractResultResponse {

    private String contractCode;
    private Long propertyId;
    private String propertyName;
    private String finalStatus;
}
