package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.ConfirmPropertyActivationRequest;
import com.sep490.slms2026.dto.response.PropertyActivationResponse;
import com.sep490.slms2026.service.PropertyActivationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/properties/{propertyId}/activation")
@RequiredArgsConstructor
public class PropertyActivationController {

    private final PropertyActivationService propertyActivationService;

    @PostMapping("/confirm")
    public ResponseEntity<PropertyActivationResponse> confirmActivation(
            @PathVariable Long propertyId,
            @Valid @RequestBody ConfirmPropertyActivationRequest request) {
        return ResponseEntity.ok(propertyActivationService.confirmActivation(propertyId, request));
    }
}
