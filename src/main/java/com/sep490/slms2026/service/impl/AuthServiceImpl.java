package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.AuthRequest;
import com.sep490.slms2026.dto.response.AuthResponse;
import com.sep490.slms2026.entity.Admin;
import com.sep490.slms2026.entity.OperationManagement;
import com.sep490.slms2026.entity.Owner;
import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.enums.Role;
import com.sep490.slms2026.enums.UserStatus;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.security.CustomUserDetailsService;
import com.sep490.slms2026.security.JwtUtil;
import com.sep490.slms2026.service.AuthService;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@AllArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final PasswordEncoder passwordEncoder;
    private final CustomUserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    @Override
    @Transactional
    public String register(AuthRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username đã tồn tại trên hệ thống!");
        }
        if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new RuntimeException("Số điện thoại này đã được đăng ký!");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        // Mã hóa Hash mật khẩu trước khi lưu xuống DB
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhoneNumber(request.getPhoneNumber());
        user.setStatus(UserStatus.ACTIVE);

        // Mặc định gán Role nếu request gửi lên trống hoặc sai enum
        Role userRole;
        try {
            userRole = Role.valueOf(request.getRole().toUpperCase());
        } catch (Exception e) {
            userRole = Role.ROLE_TENANT;
        }
        user.setRole(userRole);

        // Khởi tạo thực thể Profile tương ứng dựa vào Role để tránh lỗi khi thao tác chức năng khác
        if (userRole == Role.ROLE_ADMIN) {
            Admin adminProfile = new Admin();
            adminProfile.setUser(user);
            adminProfile.setStartAt(LocalDateTime.now());
            user.setAdminProfile(adminProfile);
        } else if (userRole == Role.ROLE_MANAGER) {
            OperationManagement managerProfile = new OperationManagement();
            managerProfile.setUser(user);
            managerProfile.setStartAt(LocalDateTime.now());
            user.setOperationManagementProfile(managerProfile);
        } else if (userRole == Role.ROLE_OWNER) {
            Owner ownerProfile = new Owner();
            ownerProfile.setUser(user);
            user.setOwnerProfile(ownerProfile);
        }

        // CascadeType.ALL cấu hình ở thực thể User sẽ tự động lưu bản ghi Profile đi kèm xuống DB
        userRepository.save(user);
        return "Đăng ký tài khoản thành công!";
    }

    @Override
    public AuthResponse login(AuthRequest request) {
        // Thực hiện kiểm tra tài khoản, mật khẩu tự động qua AuthenticationManager
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        // Nếu không văng exception (nghĩa là đúng pass), tiến hành cấp mã Token
        final UserDetails userDetails = userDetailsService.loadUserByUsername(request.getUsername());
        final String jwt = jwtUtil.generateToken(userDetails);

        String roleName = userDetails.getAuthorities().iterator().next().getAuthority();

        return new AuthResponse(jwt, userDetails.getUsername(), roleName);
    }
}