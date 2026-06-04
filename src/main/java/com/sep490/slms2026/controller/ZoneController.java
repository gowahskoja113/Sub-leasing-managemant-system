package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.ZoneRequest;
import com.sep490.slms2026.dto.response.ZoneResponse;
import com.sep490.slms2026.service.ZoneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v2/zones")
@RequiredArgsConstructor
public class ZoneController {

    private final ZoneService zoneService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ZoneResponse> createZone(@Valid @RequestBody ZoneRequest request) {
        return new ResponseEntity<>(zoneService.createZone(request), HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<ZoneResponse>> getAllZones(Pageable pageable) {
        return ResponseEntity.ok(zoneService.getAllZones(pageable));
    }

    // API: Lấy danh sách các tỉnh/thành phố (Level 1)
    @GetMapping("/root")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ZoneResponse>> getRootZones() {
        return ResponseEntity.ok(zoneService.getRootZones());
    }

    // API: Lấy danh sách các quận/huyện thuộc tỉnh/thành phố cha (Level 1 -> Level 2)
    @GetMapping("/{parentId}/children")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ZoneResponse>> getChildrenZones(@PathVariable UUID parentId) {
        return ResponseEntity.ok(zoneService.getChildrenZones(parentId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ZoneResponse> getZoneById(@PathVariable UUID id) {
        return ResponseEntity.ok(zoneService.getZoneById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ZoneResponse> updateZone(
            @PathVariable UUID id,
            @Valid @RequestBody ZoneRequest request) {
        return ResponseEntity.ok(zoneService.updateZone(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteZone(@PathVariable UUID id) {
        zoneService.deleteZone(id);
        return ResponseEntity.noContent().build();
    }
}