package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.TenantCreationRequest;
import com.sep490.slms2026.dto.response.TenantResponse;
import com.sep490.slms2026.entity.Tenant;
import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.enums.Role;
import com.sep490.slms2026.enums.UserStatus;
import com.sep490.slms2026.mapper.UserMapper;
import com.sep490.slms2026.repository.TenantRepository;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.service.UserService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    @Autowired
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public User createUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        String username = user.getUsername();
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("Username đã tồn tại trên hệ thống!");
        }

        String phone = user.getPhoneNumber();
        if (phone == null || phone.trim().isEmpty()) {
            throw new IllegalArgumentException("Phone number is required");
        }
        if (userRepository.existsByPhoneNumber(phone)) {
            throw new RuntimeException("Số điện thoại đã được đăng ký!");
        }

        String rawPassword = user.getPassword();
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("Password is required");
        }
        String encoded = passwordEncoder.encode(rawPassword);
        user.setPassword(encoded);

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

    @Override
    @Transactional
    public TenantResponse createTenant(TenantCreationRequest request) {

        // 1. Validate thông tin User
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username đã tồn tại trên hệ thống!");
        }
        if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new RuntimeException("Số điện thoại đã được đăng ký!");
        }

        // 2. Validate thông tin Tenant (CCCD không được trùng)
        if (tenantRepository.existsByCitizenIdNumber(request.getCitizenIdNumber())) {
            throw new RuntimeException("Số CCCD đã tồn tại trong hệ thống!");
        }
        // 3. Khởi tạo User
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhoneNumber(request.getPhoneNumber());
        user.setRole(Role.ROLE_TENANT);
        user.setStatus(UserStatus.ACTIVE);

        // 4. Khởi tạo Tenant
        Tenant tenant = new Tenant();
        tenant.setFullName(request.getFullName());
        tenant.setCitizenIdNumber(request.getCitizenIdNumber());
        tenant.setRoomRentalStatus("NO_ROOM"); // Giá trị khởi tạo mặc định

        // 5. Liên kết User và Tenant
        tenant.setUser(user);
        user.setTenantProfile(tenant);

        // 6. Lưu xuống DB
        User savedUser = userRepository.save(user);

        // Dùng mapper chuyển Entity thành Response DTO rồi mới trả về
        return userMapper.toTenantResponse(savedUser);
    }
}