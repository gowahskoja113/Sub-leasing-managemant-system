package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.InvoiceUnlockPasscodeGenerateRequest;
import com.sep490.slms2026.dto.request.InvoiceUnlockVerifyRequest;
import com.sep490.slms2026.dto.response.InvoiceUnlockLogResponse;
import com.sep490.slms2026.dto.response.InvoiceUnlockPasscodeResponse;
import com.sep490.slms2026.dto.response.InvoiceUnlockVerifyResponse;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.service.InvoiceUnlockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class InvoiceUnlockController {

    private final InvoiceUnlockService invoiceUnlockService;

    @PostMapping("/api/v1/admin/invoice-unlock/passcodes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InvoiceUnlockPasscodeResponse> generatePasscode(
            @Valid @RequestBody InvoiceUnlockPasscodeGenerateRequest request) {
        return ResponseEntity.ok(invoiceUnlockService.generatePasscode(
                SecurityUtils.requireCurrentUser().getId(), request));
    }

    @GetMapping("/api/v1/admin/invoice-unlock/passcodes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<InvoiceUnlockPasscodeResponse>> listPasscodes(
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        return ResponseEntity.ok(invoiceUnlockService.listPasscodes(activeOnly));
    }

    @PostMapping("/api/v1/manager/invoice-unlock/verify")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<?> verify(@Valid @RequestBody InvoiceUnlockVerifyRequest request) {
        try {
            InvoiceUnlockVerifyResponse res = invoiceUnlockService.verifyPasscode(
                    SecurityUtils.requireCurrentUser().getId(), request);
            return ResponseEntity.ok(res);
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of(
                    "valid", false,
                    "message", e.getReason() != null ? e.getReason() : "Mã không đúng"
            ));
        }
    }

    @GetMapping("/api/v1/admin/invoice-unlock/logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<InvoiceUnlockLogResponse>> listLogs() {
        return ResponseEntity.ok(invoiceUnlockService.listLogs());
    }
}
