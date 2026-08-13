package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.CreateMeterReadingRequest;
import com.sep490.slms2026.dto.response.MeterReadingResponse;
import com.sep490.slms2026.dto.response.PendingMeterReadingItem;
import com.sep490.slms2026.enums.UtilityType;

import java.util.List;

public interface MeterReadingService {

    MeterReadingResponse getLatestReading(Long propertyId, Long roomId, String type);

    MeterReadingResponse recordReading(Long propertyId, Long roomId, CreateMeterReadingRequest request);

    List<PendingMeterReadingItem> listPending(String period);

    boolean hasPhoto(Long propertyId, Long roomId, UtilityType type, String period);
}
