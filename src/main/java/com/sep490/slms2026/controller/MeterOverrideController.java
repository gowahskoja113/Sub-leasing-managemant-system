package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.MeterOverridePasscodeGenerateRequest;
import com.sep490.slms2026.dto.request.MeterOverrideVerifyRequest;
import com.sep490.slms2026.dto.response.MeterOverrideLogResponse;
import com.sep490.slms2026.dto.response.MeterOverridePasscodeResponse;
import com.sep490.slms2026.dto.response.MeterOverrideVerifyResponse;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.service.MeterOverrideService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class MeterOverrideController {

    private final MeterOverrideService meterOverrideService;

    /**
     * Admin gen mã OTP 1 lần (6 chữ số). Gửi mã cho manager.
     * Body optional: { "ttlMinutes": 10, "note": "..." }
     */
    @PostMapping("/api/v1/admin/meter-override/passcodes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MeterOverridePasscodeResponse> generatePasscode(
            @RequestBody(required = false) MeterOverridePasscodeGenerateRequest request) {
        return ResponseEntity.ok(meterOverrideService.generatePasscode(
                SecurityUtils.requireCurrentUser().getId(),
                request != null ? request : new MeterOverridePasscodeGenerateRequest()));
    }

    /** activeOnly=true → chỉ mã còn dùng được. */
    @GetMapping("/api/v1/admin/meter-override/passcodes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<MeterOverridePasscodeResponse>> listPasscodes(
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        return ResponseEntity.ok(meterOverrideService.listPasscodes(activeOnly));
    }

    /**
     * Manager nhập mã admin gen → nhận overrideToken.
     * Mã passcode chết ngay khi verify OK.
     */
    @PostMapping("/api/v1/manager/meter-override/verify")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<?> verify(@Valid @RequestBody MeterOverrideVerifyRequest request) {
        try {
            MeterOverrideVerifyResponse res = meterOverrideService.verifyPasscode(
                    SecurityUtils.requireCurrentUser().getId(), request);
            return ResponseEntity.ok(res);
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of(
                    "valid", false,
                    "message", e.getReason() != null ? e.getReason() : "Mã không đúng"
            ));
        }
    }

    @GetMapping("/api/v1/admin/meter-overrides")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<MeterOverrideLogResponse>> listLogs() {
        return ResponseEntity.ok(meterOverrideService.listLogs());
    }
}
