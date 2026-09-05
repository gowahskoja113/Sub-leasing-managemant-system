package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.CreateUtilityBillRequest;
import com.sep490.slms2026.dto.response.UtilityBillResponse;
import com.sep490.slms2026.enums.UtilityType;

import java.util.List;

public interface UtilityBillService {
    UtilityBillResponse createUtilityBill(CreateUtilityBillRequest request);
    void revokeUtilityBill(Long id);
    List<UtilityBillResponse> getUtilityBills(Long propertyId, Integer month, Integer year, UtilityType type, boolean isManager);

    /** Cron nhắc quản lý chụp đồng hồ nhà chia phòng — 15:00, 20:00, sáng hôm sau. */
    int remindUtilityMeterReading();
}

