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

    /**
     * Phòng còn thiếu ảnh công tơ cho (property, period, type) — cùng điều kiện {@link #listPending}.
     * Không lọc theo user hiện tại (dùng nội bộ để chốt đối soát).
     */
    List<PendingMeterReadingItem> listPendingFor(Long propertyId, String period, UtilityType type);

    /**
     * Phòng có HĐ ACTIVE cần đọc kỳ này (cùng filter {@link #listPending}, chưa trừ ảnh).
     * Dùng để biết tập phòng phải có hoá đơn trước khi chốt đối soát.
     */
    List<PendingMeterReadingItem> listEligibleForPeriod(Long propertyId, String period, UtilityType type);

    boolean hasPhoto(Long propertyId, Long roomId, UtilityType type, String period);
}
