package com.sep490.slms2026.dto;

import java.util.UUID;

public interface ZoneSummaryProjection {
    UUID getZoneId();
    String getZoneName();
    Long getWholeHouseCount();
    Long getRoomBasedCount();
}