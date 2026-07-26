package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.AuthRequest;
import com.sep490.slms2026.dto.request.ChangePasswordRequest;
import com.sep490.slms2026.dto.request.TenantActivateConfirmRequest;
import com.sep490.slms2026.dto.request.TenantActivatePhoneRequest;
import com.sep490.slms2026.dto.response.AuthMeResponse;
import com.sep490.slms2026.dto.response.AuthResponse;
import com.sep490.slms2026.dto.response.TenantActivateCheckResponse;
import com.sep490.slms2026.service.AuthService;
import com.sep490.slms2026.service.TenantActivationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final TenantActivationService tenantActivationService;

    public AuthController(AuthService authService, TenantActivationService tenantActivationService) {
        this.authService = authService;
        this.tenantActivationService = tenantActivationService;
    }

    @PostMapping("/register")
    public ResponseEntity<String> registerUser(@Valid @RequestBody AuthRequest request) {
        String message = authService.register(request);
        return ResponseEntity.ok(message);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> loginUser(@Valid @RequestBody AuthRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<AuthMeResponse> getMe() {
        return ResponseEntity.ok(authService.getMe());
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.ok(Map.of("message", "Đổi mật khẩu thành công"));
    }

    /** FE: phân nhánh UI Đăng nhập vs Kích hoạt tài khoản. */
    @PostMapping("/tenant-activate/check")
    public ResponseEntity<TenantActivateCheckResponse> checkTenantActivation(
            @Valid @RequestBody TenantActivatePhoneRequest request) {
        return ResponseEntity.ok(tenantActivationService.check(request.getPhoneNumber()));
    }

    @PostMapping("/tenant-activate/send-otp")
    public ResponseEntity<Map<String, String>> sendTenantActivationOtp(
            @Valid @RequestBody TenantActivatePhoneRequest request) {
        tenantActivationService.sendOtp(request.getPhoneNumber());
        return ResponseEntity.ok(Map.of("message", "Đã gửi mã OTP kích hoạt tài khoản"));
    }

    @PostMapping("/tenant-activate/confirm")
    public ResponseEntity<AuthResponse> confirmTenantActivation(
            @Valid @RequestBody TenantActivateConfirmRequest request) {
        return ResponseEntity.ok(tenantActivationService.confirm(request));
    }
}