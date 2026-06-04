package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.TenantCreationRequest;
import com.sep490.slms2026.dto.response.TenantResponse;
import com.sep490.slms2026.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
public class OnboaringController {

    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_MANAGER', 'ROLE_ADMIN')")
    public ResponseEntity<TenantResponse> createTenant(@RequestBody TenantCreationRequest request) {
        return ResponseEntity.ok(userService.createTenant(request));
    }
}
