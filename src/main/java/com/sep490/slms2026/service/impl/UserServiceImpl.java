package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.enums.UserStatus;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.service.UserService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public User createUser(User user) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new RuntimeException("Username đã tồn tại trên hệ thống!");
        }
        if (userRepository.existsByPhoneNumber(user.getPhoneNumber())) {
            throw new RuntimeException("Số điện thoại đã được đăng ký!");
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
    public User updateUser(UUID id, User userDetails) {
        User existingUser = getUserById(id);

        // Tránh trùng số điện thoại khi đổi thông tin công khai
        if (!existingUser.getPhoneNumber().equals(userDetails.getPhoneNumber())
                && userRepository.existsByPhoneNumber(userDetails.getPhoneNumber())) {
            throw new RuntimeException("Số điện thoại mới đã tồn tại!");
        }

        existingUser.setPhoneNumber(userDetails.getPhoneNumber());
        if (userDetails.getPassword() != null && !userDetails.getPassword().isEmpty()) {
            existingUser.setPassword(userDetails.getPassword()); // Cân nhắc mã hóa BCrypt sau này
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