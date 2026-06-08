package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.entity.Admin;
import com.sep490.slms2026.entity.OperationManagement;
import com.sep490.slms2026.entity.Owner;
import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.enums.Role;
import com.sep490.slms2026.enums.UserStatus;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.service.UserService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public User createUser(User user) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new RuntimeException("Username đã tồn tại trên hệ thống!");
        }
        if (userRepository.existsByPhoneNumber(user.getPhoneNumber())) {
            throw new RuntimeException("Số điện thoại đã được đăng ký!");
        }

        // Mã hóa mật khẩu cấp bởi Admin
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        // Mặc định tài khoản do admin tạo sẽ ACTIVE ngay
        if (user.getStatus() == null) {
            user.setStatus(UserStatus.ACTIVE);
        }

        // Tạo logic map Profile tự động khi Admin chỉ định Role cho Account mới
        if (user.getRole() == Role.ROLE_MANAGER) {
            OperationManagement managerProfile = new OperationManagement();
            managerProfile.setUser(user);
            managerProfile.setStartAt(LocalDateTime.now());
            user.setOperationManagementProfile(managerProfile);
        } else if (user.getRole() == Role.ROLE_ADMIN) {
            Admin adminProfile = new Admin();
            adminProfile.setUser(user);
            adminProfile.setStartAt(LocalDateTime.now());
            user.setAdminProfile(adminProfile);
        } else if (user.getRole() == Role.ROLE_OWNER) {
            Owner ownerProfile = new Owner();
            ownerProfile.setUser(user);
            user.setOwnerProfile(ownerProfile);
        }

        return userRepository.save(user);
    }

    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    public User getUserById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng với ID: " + id));
    }

    @Override
    @Transactional
    public User updateUser(UUID id, User userDetails) {
        User existingUser = getUserById(id);

        if (!existingUser.getPhoneNumber().equals(userDetails.getPhoneNumber())
                && userRepository.existsByPhoneNumber(userDetails.getPhoneNumber())) {
            throw new RuntimeException("Số điện thoại mới đã tồn tại!");
        }

        // Cập nhật các thông tin cơ bản bao gồm cả fullName mới thêm vào
        existingUser.setPhoneNumber(userDetails.getPhoneNumber());
        existingUser.setFullName(userDetails.getFullName()); // <-- Cập nhật fullName tại đây

        if (userDetails.getPassword() != null && !userDetails.getPassword().isEmpty()) {
            existingUser.setPassword(passwordEncoder.encode(userDetails.getPassword())); // Đã tích hợp mã hóa BCrypt
        }

        existingUser.setRole(userDetails.getRole());

        return userRepository.save(existingUser);
    }

    @Override
    @Transactional
    public User changeUserStatus(UUID id, UserStatus newStatus) {
        User user = getUserById(id);
        user.setStatus(newStatus);
        return userRepository.save(user);
    }
}