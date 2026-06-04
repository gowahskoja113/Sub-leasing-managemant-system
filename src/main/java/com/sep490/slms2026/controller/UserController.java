package com.sep490.slms2026.controller;

import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.enums.UserStatus;
import com.sep490.slms2026.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/user")
@CrossOrigin(origins = "*") // Hỗ trợ dev đa nền tảng không lo lỗi CORS
public class UserController {

    @Autowired
    private UserService userService;

    // 1. API Lấy toàn bộ danh sách User
    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    // 2. API Lấy chi tiết User bằng ID
    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    // 3. API Tạo mới một User
    @PostMapping
    public ResponseEntity<User> createUser(@RequestBody User user) {
        return ResponseEntity.ok(userService.createUser(user));
    }

    // 4. API Chỉnh sửa thông tin cơ bản của User
    @PutMapping("/{id}")
    public ResponseEntity<User> updateUser(@PathVariable UUID id, @RequestBody User userDetails) {
        return ResponseEntity.ok(userService.updateUser(id, userDetails));
    }

    // 5. API Đặc quyền Admin: Thay đổi trạng thái tài khoản (ACTIVE, INACTIVE, DISABLE)
    @PatchMapping("/{id}/status")
    public ResponseEntity<User> changeStatus(@PathVariable UUID id, @RequestParam UserStatus status) {
        return ResponseEntity.ok(userService.changeUserStatus(id, status));
    }
}