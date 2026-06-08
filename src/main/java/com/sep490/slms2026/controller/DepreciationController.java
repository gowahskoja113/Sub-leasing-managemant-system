package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.CalculateDepreciationRequest;
import com.sep490.slms2026.dto.response.DepreciationCalculationResponse;
import com.sep490.slms2026.service.DepreciationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/properties/{propertyId}/depreciation")
@RequiredArgsConstructor
public class DepreciationController {

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
    public ResponseEntity<DepreciationCalculationResponse> getDepreciation(@PathVariable Long propertyId) {
        return ResponseEntity.ok(depreciationService.getByProperty(propertyId));
    }
}
