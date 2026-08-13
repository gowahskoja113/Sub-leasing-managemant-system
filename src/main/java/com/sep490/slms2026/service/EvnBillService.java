package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.CreateEvnBillRequest;
import com.sep490.slms2026.dto.request.CreateUtilityBillRequest;
import com.sep490.slms2026.dto.response.EvnBillResponse;
import com.sep490.slms2026.enums.UtilityType;

import java.util.List;

public interface EvnBillService {
    EvnBillResponse createEvnBill(CreateEvnBillRequest request);
    EvnBillResponse createUtilityBill(CreateUtilityBillRequest request);
    void revokeEvnBill(Long id);
    void revokeUtilityBill(Long id);
    List<EvnBillResponse> getEvnBills(Long propertyId, Integer month, Integer year, boolean isManager);
    List<EvnBillResponse> getUtilityBills(Long propertyId, Integer month, Integer year, UtilityType type, boolean isManager);
}
