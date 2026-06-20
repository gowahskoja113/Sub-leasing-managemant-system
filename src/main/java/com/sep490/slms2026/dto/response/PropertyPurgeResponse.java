package com.sep490.slms2026.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PropertyPurgeResponse {

    private Long propertyId;
    private String propertyName;
    private String contractCode;
    private int equipmentsDeleted;
    private int equipmentManifestsDeleted;
    private int renovationLinesDeleted;
    private int renovationSessionsDeleted;
    private int roomsDeleted;
    private int depreciationResultsDeleted;
    private int monthlyReadingsDeleted;
}
