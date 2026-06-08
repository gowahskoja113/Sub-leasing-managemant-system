package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.AddRenovationRequest;
import com.sep490.slms2026.dto.response.RenovationResponse;
import com.sep490.slms2026.service.RenovationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/properties/{propertyId}/renovations")
@RequiredArgsConstructor
public class RenovationController {

    private final RenovationService renovationService;

    @PostMapping
    public ResponseEntity<RenovationResponse> addRenovation(
            @PathVariable Long propertyId,
            @Valid @RequestBody AddRenovationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(renovationService.addRenovation(propertyId, request));
    }

    @GetMapping
    public ResponseEntity<List<RenovationResponse>> getRenovations(@PathVariable Long propertyId) {
        return ResponseEntity.ok(renovationService.getRenovationsByProperty(propertyId));
    }
}
