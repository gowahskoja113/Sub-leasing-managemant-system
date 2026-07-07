package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.CreateViewingLeadRequest;
import com.sep490.slms2026.dto.response.ViewingLeadResponse;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.service.ViewingLeadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/viewing-leads")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminViewingLeadController {

    private final ViewingLeadService viewingLeadService;

    @PostMapping
    public ResponseEntity<ViewingLeadResponse> createLead(@Valid @RequestBody CreateViewingLeadRequest request) {
        ViewingLeadResponse response = viewingLeadService.createLead(currentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public Page<ViewingLeadResponse> listLeads(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String keyword,
            Pageable pageable) {
        return viewingLeadService.listLeadsForAdmin(status, phone, keyword, pageable);
    }

    @GetMapping("/{id}")
    public ViewingLeadResponse getLead(@PathVariable Long id) {
        return viewingLeadService.getLeadForAdmin(id);
    }

    private UUID currentUserId() {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        return user.getId();
    }
}
