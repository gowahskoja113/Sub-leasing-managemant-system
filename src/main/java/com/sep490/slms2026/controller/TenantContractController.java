package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.OnboardTenantRequest;
import com.sep490.slms2026.dto.response.TenantContractDetailResponse.EquipmentItem;
import com.sep490.slms2026.dto.response.TenantContractResponse;
import com.sep490.slms2026.service.ContractEquipmentService;
import com.sep490.slms2026.service.TenantOnboardingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/properties/{propertyId}")
@RequiredArgsConstructor
public class TenantContractController {

    private final TenantOnboardingService tenantOnboardingService;
    private final ContractEquipmentService contractEquipmentService;

    /**
     * POST /api/v1/properties/{propertyId}/rooms/{roomId}/tenant-contract
     * Onboard khách thuê vào 1 phòng cụ thể.
     */
    @PostMapping("/rooms/{roomId}/tenant-contract")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<TenantContractResponse> onboardRoomTenant(
            @PathVariable Long propertyId,
            @PathVariable Long roomId,
            @Valid @RequestBody OnboardTenantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tenantOnboardingService.onboardTenant(propertyId, roomId, request));
    }

    /**
     * POST /api/v1/properties/{propertyId}/tenant-contract
     * Onboard khách thuê nguyên căn (không gắn phòng cụ thể).
     */
    @PostMapping("/tenant-contract")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<TenantContractResponse> onboardWholeHouseTenant(
            @PathVariable Long propertyId,
            @Valid @RequestBody OnboardTenantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tenantOnboardingService.onboardTenant(propertyId, null, request));
    }

    /**
     * GET /api/v1/properties/{propertyId}/tenant-contracts
     * Danh sách hợp đồng thuê của tòa nhà.
     */
    @GetMapping("/tenant-contracts")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<List<TenantContractResponse>> listByProperty(@PathVariable Long propertyId) {
        return ResponseEntity.ok(tenantOnboardingService.getContractsByProperty(propertyId));
    }

    /**
     * GET /api/v1/properties/{propertyId}/contract-available-equipments?roomId=
     * Thiết bị có thể chọn bàn giao trước khi tạo HĐ (onboarding / draft).
     */
    @GetMapping("/contract-available-equipments")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<List<EquipmentItem>> listContractAvailableEquipments(
            @PathVariable Long propertyId,
            @RequestParam(required = false) Long roomId) {
        return ResponseEntity.ok(contractEquipmentService.mapAvailableToItems(propertyId, roomId));
    }
}
