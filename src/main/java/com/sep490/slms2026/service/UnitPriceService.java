package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.UpdateUnitPriceRequest;
import com.sep490.slms2026.dto.response.PropertyResponse;
import com.sep490.slms2026.dto.response.RoomPriceHistoryResponse;
import com.sep490.slms2026.dto.response.RoomResponse;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.enums.RoomPriceChangeType;

import java.math.BigDecimal;
import java.util.List;

public interface UnitPriceService {

    RoomResponse updateRoomListedPrice(Long propertyId, Long roomId, UpdateUnitPriceRequest request);

    PropertyResponse updatePropertyListedPrice(Long propertyId, UpdateUnitPriceRequest request);

    List<RoomPriceHistoryResponse> getPriceHistory(Long propertyId, Long roomId);

    boolean isUnitOccupied(Long propertyId, Long roomId);

    void applyContractRent(TenantContract contract, RoomPriceChangeType changeType, String reason);

    void revertToListedPrice(TenantContract contract);

    int applyDueEscalations();

    /** Báo trước khách/quản lý trước 01/01 (≥15 ngày). */
    int notifyUpcomingAnnualEscalations();

    BigDecimal resolveListedPrice(TenantContract contract);

    BigDecimal deltaPercent(BigDecimal oldPrice, BigDecimal newPrice);
}
