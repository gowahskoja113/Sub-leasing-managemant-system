package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.*;
import com.sep490.slms2026.dto.response.*;
import com.sep490.slms2026.service.PropertyOnboardingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @GetMapping("/properties/{propertyId}/onboarding-summary")
    public ResponseEntity<OnboardingSummaryResponse> getOnboardingSummary(
            @PathVariable Long propertyId) {
        return ResponseEntity.ok(propertyOnboardingService.getOnboardingSummary(propertyId));
    }

    @GetMapping("/equipment-catalog")
    public ResponseEntity<List<EquipmentCatalogResponse>> listEquipmentCatalog() {
        return ResponseEntity.ok(propertyOnboardingService.listEquipmentCatalog());
    }

    @GetMapping("/renovation-categories")
    public ResponseEntity<List<RenovationCategoryResponse>> listRenovationCategories() {
        return ResponseEntity.ok(propertyOnboardingService.listRenovationCategories());
    }
}
