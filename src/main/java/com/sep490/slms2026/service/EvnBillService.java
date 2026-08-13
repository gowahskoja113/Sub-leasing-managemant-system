package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.CreateEvnBillRequest;
import com.sep490.slms2026.dto.response.EvnBillResponse;

import java.util.List;

public interface EvnBillService {
    EvnBillResponse createEvnBill(CreateEvnBillRequest request);
    void revokeEvnBill(Long id);
    List<EvnBillResponse> getEvnBills(Long propertyId, Integer month, Integer year, boolean isManager);
}
