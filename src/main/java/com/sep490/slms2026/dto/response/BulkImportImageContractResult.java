package com.sep490.slms2026.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BulkImportImageContractResult {

    /** ATTACHED | PREVIEW | NOT_FOUND | NO_IMAGES */
    private String status;
    private String contractCode;
    private Long propertyId;
    private String propertyName;
    private int imagesAttached;
    private String message;
}
