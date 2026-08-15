package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.PropertyCreateRequest;
import com.sep490.slms2026.dto.response.PropertyResponse;
import com.sep490.slms2026.service.PropertyService;
import com.sep490.slms2026.service.UnitPriceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/properties")
@RequiredArgsConstructor
public class PropertyController {

    private final PropertyService propertyService;
    private final UnitPriceService unitPriceService;

    @PostMapping
    public ResponseEntity<PropertyResponse> createProperty(@RequestBody PropertyCreateRequest request) {
        return new ResponseEntity<>(propertyService.createProperty(request), HttpStatus.CREATED);
    }

    /** Danh sách BĐS còn cho thuê được — dùng cho màn onboarding đón khách. */
    @GetMapping("/rentable")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MANAGER', 'USER')")
    public ResponseEntity<List<PropertyResponse>> getRentableProperties() {
        return ResponseEntity.ok(propertyService.getRentableProperties());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MANAGER', 'USER')")
    public ResponseEntity<PropertyResponse> getPropertyById(@PathVariable Long id) {
        return ResponseEntity.ok(propertyService.getPropertyById(id));
    }

    @PatchMapping("/{id}/price")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<PropertyResponse> updatePropertyPrice(
            @PathVariable Long id,
            @Valid @RequestBody com.sep490.slms2026.dto.request.UpdateUnitPriceRequest request) {
        return ResponseEntity.ok(unitPriceService.updatePropertyListedPrice(id, request));
    }

    @GetMapping("/{id}/price-history")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<java.util.List<com.sep490.slms2026.dto.response.RoomPriceHistoryResponse>> getPriceHistory(
            @PathVariable Long id,
            @RequestParam(required = false) Long roomId) {
        return ResponseEntity.ok(unitPriceService.getPriceHistory(id, roomId));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MANAGER', 'USER')")
    public ResponseEntity<Page<PropertyResponse>> getAllProperties(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(propertyService.getAllProperties(pageable));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PropertyResponse> updateProperty(
            @PathVariable Long id,
            @RequestBody PropertyCreateRequest request) {
        return ResponseEntity.ok(propertyService.updateProperty(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProperty(@PathVariable Long id) {
        propertyService.deleteProperty(id);
        return ResponseEntity.noContent().build();
    }
}