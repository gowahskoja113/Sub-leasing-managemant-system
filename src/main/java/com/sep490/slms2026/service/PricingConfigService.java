package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.UpdatePricingConfigRequest;
import com.sep490.slms2026.dto.response.PricingConfigResponse;
import com.sep490.slms2026.entity.PricingConfig;

import java.util.UUID;

public interface PricingConfigService {

    PricingConfig current();

    PricingConfigResponse get();

    PricingConfigResponse update(UpdatePricingConfigRequest request, UUID actorId);
}
