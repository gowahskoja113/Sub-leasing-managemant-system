package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.response.PropertyResponse;
import com.sep490.slms2026.dto.response.TenantSortResponse;
import com.sep490.slms2026.service.TenantSortService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tenant-sort")
@RequiredArgsConstructor
public class TenantSortController {

    private final TenantSortService tenantSortService;

    @GetMapping("/most-properties")
    public ResponseEntity<List<TenantSortResponse>> mostProperties() {

        return ResponseEntity.ok(
                tenantSortService.getMostPropertiesByZone()
        );
    }

    @GetMapping("/most-rooms")
    public ResponseEntity<List<TenantSortResponse>> mostRooms() {

        return ResponseEntity.ok(
                tenantSortService.getMostRoomsByZone()
        );
    }

    @GetMapping("/price-asc")
    public ResponseEntity<List<PropertyResponse>> priceAsc() {

        return ResponseEntity.ok(
                tenantSortService.getPropertiesPriceAsc()
        );
    }

    @GetMapping("/price-desc")
    public ResponseEntity<List<PropertyResponse>> priceDesc() {

        return ResponseEntity.ok(
                tenantSortService.getPropertiesPriceDesc()
        );
    }
}