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
@CrossOrigin(origins = "*")
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
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    // 2. API Lấy chi tiết User bằng ID
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MANAGER', 'USER')")
    public ResponseEntity<User> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    // 3. API Tạo mới một User (Admin)
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<User> createUser(@RequestBody User user) {
        return ResponseEntity.ok(userService.createUser(user));
    }

    // 4. API Chỉnh sửa thông tin cơ bản của User
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<User> updateUser(@PathVariable UUID id, @RequestBody User userDetails) {
        return ResponseEntity.ok(userService.updateUser(id, userDetails));
    }

    // 5. API Đặc quyền Admin: Thay đổi trạng thái tài khoản (ACTIVE, INACTIVE, DISABLE)
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<User> changeStatus(@PathVariable UUID id, @RequestParam UserStatus status) {
        return ResponseEntity.ok(userService.changeUserStatus(id, status));
    }

    @GetMapping("/managers")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MANAGER')")
    public ResponseEntity<List<UserResponse>> getAllManagers() {
        // Lấy danh sách entity từ repository
        List<User> managers = userRepository.findByRoleAndStatus(Role.ROLE_MANAGER, UserStatus.ACTIVE);

        // Map sang UserResponse DTO
        List<UserResponse> response = managers.stream()
                .map(user -> UserResponse.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .phoneNumber(user.getPhoneNumber())
                        .fullName(user.getFullName())
                        .role(user.getRole())
                        .status(user.getStatus())
                        .createAt(user.getCreateAt())
                        .build())
                .toList();

        return ResponseEntity.ok(response);
    }

    @PutMapping("/me")
    public ResponseEntity<AuthMeResponse> updateMyProfile(@RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateMyProfile(request));
    }
}