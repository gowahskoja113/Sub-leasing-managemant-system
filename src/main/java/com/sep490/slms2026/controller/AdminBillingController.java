package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.response.AdminHostDto;
import com.sep490.slms2026.dto.response.AdminInvoiceDto;
import com.sep490.slms2026.service.AdminBillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminBillingController {

    private final AdminBillingService adminBillingService;

    @GetMapping("/invoices")
    public ResponseEntity<Page<AdminInvoiceDto>> listInvoices(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String hostId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminBillingService.getAdminInvoices(month, hostId, status, keyword, PageRequest.of(page, size)));
    }

    @GetMapping("/hosts")
    public ResponseEntity<List<AdminHostDto>> listHosts() {
        return ResponseEntity.ok(adminBillingService.getAdminHosts());
    }
}
