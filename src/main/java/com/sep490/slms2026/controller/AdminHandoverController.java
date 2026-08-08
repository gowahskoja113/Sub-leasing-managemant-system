package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.response.AdminHandoverStatusDto;
import com.sep490.slms2026.service.AdminHandoverService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminHandoverController {

    private final AdminHandoverService adminHandoverService;

    @GetMapping("/handover-status")
    public ResponseEntity<AdminHandoverStatusDto> getHandoverStatus(@RequestParam Long propertyId) {
        return ResponseEntity.ok(adminHandoverService.getHandoverStatus(propertyId));
    }
}
