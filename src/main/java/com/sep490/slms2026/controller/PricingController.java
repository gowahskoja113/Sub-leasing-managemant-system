package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.CalculateDepreciationRequest;
import com.sep490.slms2026.dto.response.DepreciationCalculationResponse;
import com.sep490.slms2026.dto.response.PricingReconciliationResponse;
import com.sep490.slms2026.service.DepreciationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.YearMonth;

@RestController
@RequestMapping("/api/v1/properties/{propertyId}/pricing")
@RequiredArgsConstructor
public class PricingController {

    private final DepreciationService depreciationService;

    @PostMapping("/calculate")
    public ResponseEntity<DepreciationCalculationResponse> calculate(
            @PathVariable Long propertyId,
            @Valid @RequestBody(required = false) CalculateDepreciationRequest request) {
        CalculateDepreciationRequest body = request != null
                ? request
                : CalculateDepreciationRequest.builder().build();
        return ResponseEntity.ok(depreciationService.calculate(propertyId, body));
    }

    @GetMapping
    public ResponseEntity<DepreciationCalculationResponse> getPricing(@PathVariable Long propertyId) {
        return ResponseEntity.ok(depreciationService.getByProperty(propertyId));
    }

    @GetMapping("/reconciliation")
    public ResponseEntity<PricingReconciliationResponse> reconcile(
            @PathVariable Long propertyId,
            @RequestParam String month,
            @RequestParam(required = false) BigDecimal oOperation,
            @RequestParam(required = false) BigDecimal pDesired,
            @RequestParam(required = false) BigDecimal vRate) {
        YearMonth ym = YearMonth.parse(month);
        return ResponseEntity.ok(depreciationService.reconcile(propertyId, ym, oOperation, pDesired, vRate));
    }
}
