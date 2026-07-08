package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.CreateMeterReadingRequest;
import com.sep490.slms2026.dto.response.MeterReadingResponse;
import com.sep490.slms2026.service.MeterReadingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class MeterReadingController {

    private final MeterReadingService meterReadingService;

    @GetMapping("/api/v1/properties/{propertyId}/rooms/{roomId}/meter-readings/latest")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<MeterReadingResponse> getLatestRoomReading(
            @PathVariable Long propertyId,
            @PathVariable Long roomId,
            @RequestParam String type) {
        return ResponseEntity.ok(meterReadingService.getLatestReading(propertyId, roomId, type));
    }

    @PostMapping("/api/v1/properties/{propertyId}/rooms/{roomId}/meter-readings")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<MeterReadingResponse> recordRoomReading(
            @PathVariable Long propertyId,
            @PathVariable Long roomId,
            @Valid @RequestBody CreateMeterReadingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(meterReadingService.recordReading(propertyId, roomId, request));
    }

    @GetMapping("/api/v1/properties/{propertyId}/meter-readings/latest")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<MeterReadingResponse> getLatestPropertyReading(
            @PathVariable Long propertyId,
            @RequestParam String type) {
        return ResponseEntity.ok(meterReadingService.getLatestReading(propertyId, null, type));
    }

    @PostMapping("/api/v1/properties/{propertyId}/meter-readings")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<MeterReadingResponse> recordPropertyReading(
            @PathVariable Long propertyId,
            @Valid @RequestBody CreateMeterReadingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(meterReadingService.recordReading(propertyId, null, request));
    }
}
