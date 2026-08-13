package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.UpdateBillingConfigRequest;
import com.sep490.slms2026.dto.response.BillingConfigResponse;
import com.sep490.slms2026.entity.BillingConfig;

import java.util.UUID;

public interface BillingConfigService {

    BillingConfig current();

    BillingConfigResponse get();

    BillingConfigResponse update(UpdateBillingConfigRequest request, UUID adminId);
}
