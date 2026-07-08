package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.*;
import com.sep490.slms2026.dto.response.*;
import com.sep490.slms2026.service.PropertyOnboardingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PropertyOnboardingController {

    private final PropertyOnboardingService propertyOnboardingService;

    @PostMapping("/properties/draft")
    public ResponseEntity<PropertyResponse> createDraft(@Valid @RequestBody PropertyDraftRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(propertyOnboardingService.createDraft(request));
    }

    @PostMapping("/properties/{propertyId}/onboarding-options")
    public ResponseEntity<PropertyResponse> setOnboardingOptions(
            @PathVariable Long propertyId,
            @Valid @RequestBody OnboardingOptionsRequest request) {
        return ResponseEntity.ok(propertyOnboardingService.setOnboardingOptions(propertyId, request));
    }

    @PutMapping("/properties/{propertyId}/structure")
    public ResponseEntity<PropertyResponse> updateStructure(
            @PathVariable Long propertyId,
            @Valid @RequestBody UpdatePropertyStructureRequest request) {
        return ResponseEntity.ok(propertyOnboardingService.updateStructure(propertyId, request));
    }

    @PutMapping("/properties/{propertyId}/equipment-manifest")
    public ResponseEntity<List<EquipmentManifestResponse>> saveEquipmentManifest(
            @PathVariable Long propertyId,
            @Valid @RequestBody SaveEquipmentManifestRequest request) {
        return ResponseEntity.ok(propertyOnboardingService.saveEquipmentManifest(propertyId, request));
    }

    @GetMapping("/properties/{propertyId}/equipment-manifest")
    public ResponseEntity<List<EquipmentManifestResponse>> getEquipmentManifest(
            @PathVariable Long propertyId) {
        return ResponseEntity.ok(propertyOnboardingService.getEquipmentManifest(propertyId));
    }

    @GetMapping("/properties/{propertyId}/handover-equipments")
    public ResponseEntity<List<HandoverEquipmentResponse>> getHandoverEquipments(
            @PathVariable Long propertyId) {
        return ResponseEntity.ok(propertyOnboardingService.getHandoverEquipments(propertyId));
    }

    /**
     * Thiết bị mua mới (PURCHASED) — gồm giá, bảo hành và vị trí lắp đặt.
     * Dùng sau import cải tạo đợt 2 hoặc cải tạo bổ sung.
     */
    @GetMapping("/properties/{propertyId}/purchased-equipments")
    public ResponseEntity<List<EquipmentResponse>> getPurchasedEquipments(
            @PathVariable Long propertyId,
            @RequestParam(required = false) Integer sessionNumber) {
        return ResponseEntity.ok(propertyOnboardingService.getPurchasedEquipments(propertyId, sessionNumber));
    }

    @PostMapping("/properties/{propertyId}/equipments/assign")
    public ResponseEntity<EquipmentResponse> assignEquipment(
            @PathVariable Long propertyId,
            @Valid @RequestBody AssignEquipmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(propertyOnboardingService.assignEquipment(propertyId, request));
    }

    @PostMapping("/properties/{propertyId}/renovation-lines")
    public ResponseEntity<RenovationLineResponse> addRenovationLine(
            @PathVariable Long propertyId,
            @Valid @RequestBody AddRenovationLineRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(propertyOnboardingService.addRenovationLine(propertyId, request));
    }

    @GetMapping("/properties/{propertyId}/renovation-lines")
    public ResponseEntity<List<RenovationLineResponse>> getRenovationLines(
            @PathVariable Long propertyId) {
        return ResponseEntity.ok(propertyOnboardingService.getRenovationLines(propertyId));
    }

    @GetMapping("/properties/{propertyId}/renovation/sessions")
    public ResponseEntity<List<RenovationSessionResponse>> getRenovationSessions(
            @PathVariable Long propertyId) {
        return ResponseEntity.ok(propertyOnboardingService.getRenovationSessions(propertyId));
    }

    @PutMapping("/properties/{propertyId}/renovation-schedule")
    public ResponseEntity<PropertyResponse> setRenovationSchedule(
            @PathVariable Long propertyId,
            @Valid @RequestBody RenovationScheduleRequest request) {
        return ResponseEntity.ok(propertyOnboardingService.setRenovationSchedule(propertyId, request));
    }

    @PostMapping("/properties/{propertyId}/renovation/start")
    public ResponseEntity<PropertyResponse> startRenovation(@PathVariable Long propertyId) {
        return ResponseEntity.ok(propertyOnboardingService.startRenovation(propertyId));
    }

    @PostMapping("/properties/{propertyId}/renovation/complete")
    public ResponseEntity<PropertyResponse> completeRenovation(@PathVariable Long propertyId) {
        return ResponseEntity.ok(propertyOnboardingService.completeRenovation(propertyId));
    }

    @PostMapping("/properties/{propertyId}/submit-to-host")
    public ResponseEntity<OnboardingSummaryResponse> submitToHost(@PathVariable Long propertyId) {
        return ResponseEntity.ok(propertyOnboardingService.submitToHost(propertyId));
    }

    @PostMapping("/properties/{propertyId}/host-confirm")
    public ResponseEntity<PropertyActivationResponse> hostConfirm(
            @PathVariable Long propertyId,
            @Valid @RequestBody HostConfirmRequest request) {
        return ResponseEntity.ok(propertyOnboardingService.hostConfirm(propertyId, request));
    }

    @PatchMapping("/properties/{propertyId}/operation-manager")
    public ResponseEntity<PropertyActivationResponse> assignOperationManager(
            @PathVariable Long propertyId,
            @Valid @RequestBody AssignOperationManagerRequest request) {
        return ResponseEntity.ok(propertyOnboardingService.assignOperationManager(propertyId, request));
    }

    @PostMapping("/properties/{propertyId}/operation-manager")
    public ResponseEntity<PropertyActivationResponse> assignOperationManagerPost(
            @PathVariable Long propertyId,
            @Valid @RequestBody AssignOperationManagerRequest request) {
        return ResponseEntity.ok(propertyOnboardingService.assignOperationManager(propertyId, request));
    }

    @PutMapping("/properties/{propertyId}/operation-manager")
    public ResponseEntity<PropertyResponse> changeOperationManager(
            @PathVariable Long propertyId,
            @Valid @RequestBody AssignOperationManagerRequest request) {
        return ResponseEntity.ok(propertyOnboardingService.changeOperationManager(propertyId, request));
    }

    @PostMapping("/properties/{propertyId}/disable")
    public ResponseEntity<PropertyResponse> disableProperty(@PathVariable Long propertyId) {
        return ResponseEntity.ok(propertyOnboardingService.disableProperty(propertyId));
    }

    @PostMapping("/properties/{propertyId}/enable")
    public ResponseEntity<PropertyResponse> enableProperty(@PathVariable Long propertyId) {
        return ResponseEntity.ok(propertyOnboardingService.enableProperty(propertyId));
    }

    @GetMapping("/properties/{propertyId}/onboarding-summary")
    public ResponseEntity<OnboardingSummaryResponse> getOnboardingSummary(
            @PathVariable Long propertyId) {
        return ResponseEntity.ok(propertyOnboardingService.getOnboardingSummary(propertyId));
    }

    @GetMapping("/equipment-catalog")
    public ResponseEntity<List<EquipmentCatalogResponse>> listEquipmentCatalog() {
        return ResponseEntity.ok(propertyOnboardingService.listEquipmentCatalog());
    }

    @PostMapping("/equipment-catalog")
    public ResponseEntity<EquipmentCatalogResponse> createEquipmentCatalog(
            @Valid @RequestBody EquipmentCatalogCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(propertyOnboardingService.createEquipmentCatalog(request));
    }

    @GetMapping("/renovation-categories")
    public ResponseEntity<List<RenovationCategoryResponse>> listRenovationCategories() {
        return ResponseEntity.ok(propertyOnboardingService.listRenovationCategories());
    }

    /**
     * Xóa cứng căn nhà và toàn bộ dữ liệu onboarding (hợp đồng, cải tạo, thiết bị, phòng...).
     * Dùng khi import Excel sai và cần import lại từ đầu.
     */
    @DeleteMapping("/properties/{propertyId}/purge")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PropertyPurgeResponse> purgeProperty(@PathVariable Long propertyId) {
        return ResponseEntity.ok(propertyOnboardingService.purgeProperty(propertyId));
    }
}
