package com.sep490.slms2026.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class BulkImportImagesResponse {

    private boolean dryRun;
    private int contractsInZip;
    private int contractsMatched;
    private int contractsNotFound;
    private int imagesAttached;
    private List<BulkImportImageContractResult> results;
    private List<String> warnings;
}
