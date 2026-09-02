package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.CreateExtensionRequest;
import com.sep490.slms2026.dto.request.ExtensionNoteRequest;
import com.sep490.slms2026.dto.request.RejectExtensionRequest;
import com.sep490.slms2026.dto.response.ExtensionOptionsResponse;
import com.sep490.slms2026.dto.response.ExtensionRequestResponse;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.service.ExtensionRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ExtensionRequestController {

    private final ExtensionRequestService extensionRequestService;

    // --- Tenant Endpoints ---

    @GetMapping("/tenant/me/contracts/{contractId}/extension-options")
    @PreAuthorize("hasRole('TENANT')")
    public ResponseEntity<ExtensionOptionsResponse> getExtensionOptions(@PathVariable Long contractId) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(extensionRequestService.getExtensionOptions(user.getId(), contractId));
    }

    @PostMapping("/tenant/me/contracts/{contractId}/extension-requests")
    @PreAuthorize("hasRole('TENANT')")
    public ResponseEntity<ExtensionRequestResponse> createExtensionRequest(
            @PathVariable Long contractId,
            @Valid @RequestBody CreateExtensionRequest request) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(extensionRequestService.createRequest(user.getId(), contractId, request));
    }

    @DeleteMapping("/tenant/me/extension-requests/{requestId}")
    @PreAuthorize("hasRole('TENANT')")
    public ResponseEntity<ExtensionRequestResponse> withdrawExtensionRequest(@PathVariable Long requestId) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(extensionRequestService.withdrawRequest(user.getId(), requestId));
    }

    @GetMapping("/tenant/me/contracts/{contractId}/extension-requests")
    @PreAuthorize("hasRole('TENANT')")
    public ResponseEntity<List<ExtensionRequestResponse>> listExtensionRequests(
            @PathVariable Long contractId) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(extensionRequestService.listRequestsForTenant(user.getId(), contractId));
    }

    // --- Manager Endpoints ---

    @GetMapping("/manager/extension-requests")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<List<ExtensionRequestResponse>> listRequestsForManager(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(extensionRequestService.listRequestsForManager(status));
    }

    @PostMapping("/manager/extension-requests/{requestId}/note")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ExtensionRequestResponse> addManagerNote(
            @PathVariable Long requestId,
            @Valid @RequestBody ExtensionNoteRequest request) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(extensionRequestService.addManagerNote(user.getId(), requestId, request));
    }

    // --- Admin Endpoints ---

    @GetMapping("/admin/extension-requests")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ExtensionRequestResponse>> listRequestsForAdmin(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(extensionRequestService.listRequestsForAdmin(status));
    }

    @PostMapping("/admin/extension-requests/{requestId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ExtensionRequestResponse> approveExtensionRequest(@PathVariable Long requestId) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(extensionRequestService.approveRequest(user.getId(), requestId));
    }

    @PostMapping("/admin/extension-requests/{requestId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ExtensionRequestResponse> rejectExtensionRequest(
            @PathVariable Long requestId,
            @Valid @RequestBody RejectExtensionRequest request) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(extensionRequestService.rejectRequest(user.getId(), requestId, request));
    }
}
