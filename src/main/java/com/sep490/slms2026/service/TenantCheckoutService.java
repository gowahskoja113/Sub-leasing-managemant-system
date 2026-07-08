package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.CreateCheckoutRequest;
import com.sep490.slms2026.dto.response.CheckoutRequestResponse;

import java.util.List;
import java.util.UUID;

public interface TenantCheckoutService {

    CheckoutRequestResponse createRequest(UUID tenantUserId, CreateCheckoutRequest request);

    List<CheckoutRequestResponse> listRequests(UUID tenantUserId);

    CheckoutRequestResponse getRequest(UUID tenantUserId, Long requestId);
}
