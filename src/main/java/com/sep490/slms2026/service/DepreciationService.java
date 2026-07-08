package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.CalculateDepreciationRequest;
import com.sep490.slms2026.dto.response.DepreciationCalculationResponse;
import com.sep490.slms2026.dto.response.PricingReconciliationResponse;

import java.math.BigDecimal;
import java.time.YearMonth;

public interface DepreciationService {

    DepreciationCalculationResponse calculate(Long propertyId, CalculateDepreciationRequest request);

    DepreciationCalculationResponse getByProperty(Long propertyId);

    PricingReconciliationResponse reconcile(
            Long propertyId,
            YearMonth month,
            BigDecimal oOperation,
            BigDecimal pDesired,
            BigDecimal vRate);
}
