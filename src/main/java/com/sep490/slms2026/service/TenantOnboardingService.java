package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.OnboardTenantRequest;
import com.sep490.slms2026.dto.response.TenantContractResponse;

import java.util.List;

public interface TenantOnboardingService {

    /**
     * Onboard khách thuê vào 1 phòng (roomId != null) hoặc thuê nguyên căn (roomId == null):
     * tạo/tra tài khoản khách (ROLE_TENANT), tạo hợp đồng thuê ACTIVE, chuyển phòng sang RENTED.
     */
    TenantContractResponse onboardTenant(Long propertyId, Long roomId, OnboardTenantRequest request);

    List<TenantContractResponse> getContractsByProperty(Long propertyId);
}
