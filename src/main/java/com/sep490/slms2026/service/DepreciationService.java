package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.CalculateDepreciationRequest;
import com.sep490.slms2026.dto.response.DepreciationCalculationResponse;

public interface DepreciationService {

    DepreciationCalculationResponse calculate(Long propertyId, CalculateDepreciationRequest request);

    DepreciationCalculationResponse getByProperty(Long propertyId);
}
