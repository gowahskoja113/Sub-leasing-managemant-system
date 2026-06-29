package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.CreateMeterReadingRequest;
import com.sep490.slms2026.dto.response.MeterReadingResponse;

public interface MeterReadingService {

    MeterReadingResponse getLatestReading(Long propertyId, Long roomId, String type);

    MeterReadingResponse recordReading(Long propertyId, Long roomId, CreateMeterReadingRequest request);
}
