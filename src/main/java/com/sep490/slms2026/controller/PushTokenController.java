package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.PushTokenRequest;
import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

/**
 * Lưu push token (FCM/Expo) cho người dùng đang đăng nhập — dùng cho thông báo đẩy (tenant).
 */
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class PushTokenController {

    private final UserRepository userRepository;

    @PostMapping("/me/push-token")
    @Transactional
    public ResponseEntity<Map<String, Object>> savePushToken(
            @Valid @RequestBody PushTokenRequest request,
            Principal principal) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        user.setPushToken(request.getPushToken());
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
