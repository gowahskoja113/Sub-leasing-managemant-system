package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.AssignZoneManagerRequest;
import com.sep490.slms2026.dto.response.ZoneAssignmentHistoryResponse;
import com.sep490.slms2026.dto.response.ZoneAssignmentResponse;
import com.sep490.slms2026.dto.response.ZoneHandoverResponse;
import com.sep490.slms2026.service.ZoneAssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ZoneAssignmentController {

    private final ZoneAssignmentService zoneAssignmentService;

    @GetMapping("/zones/assignments")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<List<ZoneAssignmentResponse>> getAllAssignments() {
        return ResponseEntity.ok(zoneAssignmentService.getAllAssignments());
    }

    @PutMapping("/zones/{zoneId}/manager")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<ZoneHandoverResponse> assignManager(
            @PathVariable UUID zoneId,
            @RequestBody @Valid AssignZoneManagerRequest request) {
        return ResponseEntity.ok(zoneAssignmentService.assignManager(zoneId, request));
    }

    @DeleteMapping("/zones/{zoneId}/manager")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeManager(@PathVariable UUID zoneId) {
        zoneAssignmentService.removeManager(zoneId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/zones/{zoneId}/handovers")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<List<ZoneHandoverResponse>> getZoneHandovers(@PathVariable UUID zoneId) {
        return ResponseEntity.ok(zoneAssignmentService.getZoneHandovers(zoneId));
    }

    @GetMapping("/users/{userId}/assignment-history")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<List<ZoneAssignmentHistoryResponse>> getUserAssignmentHistory(@PathVariable UUID userId) {
        return ResponseEntity.ok(zoneAssignmentService.getUserAssignmentHistory(userId));
    }

}
