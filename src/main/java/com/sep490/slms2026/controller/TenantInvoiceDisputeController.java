package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.CreateInvoiceDisputeRequest;
import com.sep490.slms2026.dto.response.InvoiceDisputeResponse;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.service.InvoiceDisputeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenant/me")
@RequiredArgsConstructor
public class TenantInvoiceDisputeController {

    private final InvoiceDisputeService invoiceDisputeService;

    @PostMapping("/invoices/{id}/dispute")
    @PreAuthorize("hasRole('TENANT')")
    public ResponseEntity<InvoiceDisputeResponse> create(
            @PathVariable Long id,
            @Valid @RequestBody CreateInvoiceDisputeRequest request) {
        return ResponseEntity.ok(invoiceDisputeService.create(currentUserId(), id, request));
    }

    @PostMapping("/invoices/{id}/dispute/withdraw")
    @PreAuthorize("hasRole('TENANT')")
    public ResponseEntity<InvoiceDisputeResponse> withdraw(@PathVariable Long id) {
        return ResponseEntity.ok(invoiceDisputeService.withdraw(currentUserId(), id));
    }

    private static UUID currentUserId() {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        return user.getId();
    }
}
