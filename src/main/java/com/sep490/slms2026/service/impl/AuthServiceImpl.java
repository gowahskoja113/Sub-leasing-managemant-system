package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.AuthRequest;
import com.sep490.slms2026.dto.request.ChangePasswordRequest;
import com.sep490.slms2026.dto.response.AuthMeResponse;
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

        // Xác định Role đăng ký công khai (mặc định cho Khách/Tenant/Owner tùy bạn thiết kế public)
        Role userRole;
        try {
            userRole = Role.valueOf(request.getRole().toUpperCase());
        } catch (Exception e) {
            userRole = Role.ROLE_TENANT;
        }

        // CHẶN: Không cho phép tự đăng ký tài khoản nội bộ qua cổng public này
//        if (userRole == Role.ROLE_ADMIN || userRole == Role.ROLE_MANAGER) {
//            throw new RuntimeException("Không có quyền tự đăng ký tài khoản cấp quản trị viên/quản lý!");
//        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhoneNumber(request.getPhoneNumber());
        user.setFullName(request.getFullName()); // <-- Bổ sung field fullName từ Request gửi lên
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(userRole);

        // Khởi tạo thực thể Profile tương ứng (Bây giờ luồng này chỉ tạo profile cho Owner/Tenant...)
        if (userRole == Role.ROLE_OWNER) {
            Owner ownerProfile = new Owner();
            ownerProfile.setUser(user);
            user.setOwnerProfile(ownerProfile);
        }

        userRepository.save(user);
        return "Đăng ký tài khoản thành công!";
    }

    @Override
    public AuthResponse login(AuthRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        final UserDetails userDetails = userDetailsService.loadUserByUsername(request.getUsername());
        final String jwt = jwtUtil.generateToken(userDetails);
        String roleName = userDetails.getAuthorities().iterator().next().getAuthority();

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user"));

        return new AuthResponse(jwt, userDetails.getUsername(), roleName, user.isFirstLogin());
    }

    @Override
    public AuthMeResponse getMe() {
        com.sep490.slms2026.security.CustomUserDetails userDetails = com.sep490.slms2026.security.SecurityUtils.requireCurrentUser();
        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));
        return AuthMeResponse.builder()
                .id(user.getId().toString())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .phone(user.getPhoneNumber())
                .email(user.getEmail())
                .role(user.getRole().name())
                .avatarUrl(user.getAvatarUrl())
                .isFirstLogin(user.isFirstLogin())
                .build();
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        com.sep490.slms2026.security.CustomUserDetails userDetails = com.sep490.slms2026.security.SecurityUtils.requireCurrentUser();
        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new com.sep490.slms2026.exception.BusinessException("Mật khẩu cũ không đúng");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setFirstLogin(false);
        userRepository.save(user);
    }
}