package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.PushTokenRequest;
import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.service.UserPushTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

/**
 * Lưu / gỡ push token (Expo) cho người dùng đang đăng nhập — multi-device.
 */
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class PushTokenController {

    private final UserRepository userRepository;
    private final UserPushTokenService userPushTokenService;

    @PostMapping("/me/push-token")
    @Transactional
    public ResponseEntity<Map<String, Object>> savePushToken(
            @Valid @RequestBody PushTokenRequest request,
            Principal principal) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        userPushTokenService.register(user, request.getPushToken());
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * Gỡ push token khi logout.
     * Body optional: {@code { "pushToken": "…" }} → gỡ đúng máy; không body → gỡ mọi máy của account.
     */
    @DeleteMapping("/me/push-token")
    @Transactional
    public ResponseEntity<Map<String, Object>> deletePushToken(
            @RequestBody(required = false) PushTokenRequest request,
            Principal principal) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        String token = request != null ? request.getPushToken() : null;
        userPushTokenService.unregister(user, token);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @DeleteMapping("/me/push-token")
    @Transactional
    public ResponseEntity<Map<String, Object>> deletePushToken(Principal principal) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        user.setPushToken(null);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
