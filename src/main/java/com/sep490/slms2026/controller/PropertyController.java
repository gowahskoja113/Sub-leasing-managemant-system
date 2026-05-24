package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.PropertyRequest;
import com.sep490.slms2026.dto.response.PropertyResponse;
import com.sep490.slms2026.dto.ZoneSummaryProjection;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.service.PropertyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/properties")
@RequiredArgsConstructor
public class PropertyController {

    private final PropertyService propertyService;

    // 📊 API phục vụ dashboard hiển thị số lượng theo loại hình nhà của từng Zone
    @PreAuthorize("hasRole('OWNER')")
    @GetMapping("/dashboard-summary")
    public ResponseEntity<List<ZoneSummaryProjection>> getDashboardSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(propertyService.getManagerDashboard(userDetails.getId()));
    }

    // ➕ API Tạo mới Bất động sản
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<PropertyResponse> createProperty(
            @RequestBody PropertyRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(propertyService.createProperty(request));
    }

    // 🔍 API Lấy danh sách BĐS theo phân quyền của Manager đang đăng nhập
    @GetMapping
    public ResponseEntity<Page<PropertyResponse>> getProperties(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Pageable pageable) {
        return ResponseEntity.ok(propertyService.getPropertiesForManager(userDetails.getId(), pageable));
    }

    // api update property information
    @PutMapping("/{id}")
    public ResponseEntity<PropertyResponse> updateProperty(
            @PathVariable UUID id,
            @RequestBody PropertyRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(propertyService.updateProperty(id, request, userDetails.getId()));
    }

    // apit delete property
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProperty(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        propertyService.deleteProperty(id, userDetails.getId());
        return ResponseEntity.noContent().build();
    }
}