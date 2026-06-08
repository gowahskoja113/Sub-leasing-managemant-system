package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.ConfirmPropertyActivationRequest;
import com.sep490.slms2026.dto.response.PropertyActivationResponse;

public interface PropertyActivationService {

    PropertyActivationResponse confirmActivation(Long propertyId, ConfirmPropertyActivationRequest request);
}
