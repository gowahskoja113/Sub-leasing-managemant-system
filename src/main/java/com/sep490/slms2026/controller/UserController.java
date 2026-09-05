package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.response.UserResponse;
import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.enums.Role;
import com.sep490.slms2026.enums.UserStatus;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.service.UserService;
import com.sep490.slms2026.dto.request.UpdateProfileRequest;
import com.sep490.slms2026.dto.response.AuthMeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;

    // 0. API Đăng ký tài khoản cho khách (không cần đăng nhập)
    @PostMapping("/register")
    public ResponseEntity<User> registerUserAccount(@RequestBody User user) {
        user.setRole(Role.ROLE_USER);
        user.setStatus(UserStatus.ACTIVE);
        return ResponseEntity.ok(userService.createUser(user));
    }

    // 1. API Lấy toàn bộ danh sách User
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        List<User> users = userService.getAllUsers();
        return ResponseEntity.ok(users.stream().map(this::mapToResponse).toList());
    }

    // 2. API Lấy chi tiết User bằng ID
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MANAGER', 'USER')")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID id) {
        User user = userService.getUserById(id);
        return ResponseEntity.ok(mapToResponse(user));
    }

    // 3. API Tạo mới một User (Admin)
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> createUser(@RequestBody User user) {
        return ResponseEntity.ok(mapToResponse(userService.createUser(user)));
    }

    // 4. API Chỉnh sửa thông tin cơ bản của User
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<User> updateUser(@PathVariable UUID id, @RequestBody User user) {
        return ResponseEntity.ok(userService.updateUser(id, user));
    }

    // 5. API Thay đổi trạng thái User (Khóa / Mở khóa)
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<User> changeUserStatus(@PathVariable UUID id, @RequestParam UserStatus status) {
        return ResponseEntity.ok(userService.changeUserStatus(id, status));
    }

    @GetMapping("/managers")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<List<UserResponse>> getAllManagers() {
        // Lấy danh sách manager từ service
        List<User> managers = userService.getAllUsers().stream()
                .filter(u -> u.getRole() == Role.ROLE_MANAGER && u.getStatus() == UserStatus.ACTIVE)
                .toList();

        // Map sang UserResponse DTO
        List<UserResponse> response = managers.stream()
                .map(this::mapToResponse)
                .toList();

        return ResponseEntity.ok(response);
    }

    @PutMapping("/me")
    public ResponseEntity<AuthMeResponse> updateMyProfile(@RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateMyProfile(request));
    }

    private UserResponse mapToResponse(User user) {
        String cccd = null;
        if (user.getRole() == Role.ROLE_TENANT && user.getTenantProfile() != null) {
            cccd = user.getTenantProfile().getCccd();
        }
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .phoneNumber(user.getPhoneNumber())
                .fullName(user.getFullName())
                .role(user.getRole())
                .status(user.getStatus())
                .createAt(user.getCreateAt())
                .cccd(cccd)
                .build();
    }
}