package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.ResolveInvoiceDisputeRequest;
import com.sep490.slms2026.dto.response.AdminInvoiceDisputeResponse;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.service.InvoiceDisputeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/invoice-disputes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminInvoiceDisputeController {

    private final InvoiceDisputeService invoiceDisputeService;

    @GetMapping
    public ResponseEntity<List<AdminInvoiceDisputeResponse>> list() {
        return ResponseEntity.ok(invoiceDisputeService.listForAdmin());
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<AdminInvoiceDisputeResponse> resolve(
            @PathVariable Long id,
            @Valid @RequestBody ResolveInvoiceDisputeRequest request) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(invoiceDisputeService.resolve(user.getId(), id, request));
    }
}
