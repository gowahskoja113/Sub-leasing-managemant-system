package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.TenantActivateConfirmRequest;
import com.sep490.slms2026.dto.response.AuthResponse;
import com.sep490.slms2026.dto.response.TenantActivateCheckResponse;

public interface TenantActivationService {

    TenantActivateCheckResponse check(String phoneNumber);

    void sendOtp(String phoneNumber);

    AuthResponse confirm(TenantActivateConfirmRequest request);
}
