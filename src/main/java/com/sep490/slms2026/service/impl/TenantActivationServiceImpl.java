package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.constant.OtpDeliveryOverride;
import com.sep490.slms2026.dto.request.TenantActivateConfirmRequest;
import com.sep490.slms2026.dto.response.AuthResponse;
import com.sep490.slms2026.dto.response.TenantActivateCheckResponse;
import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.enums.OtpPurpose;
import com.sep490.slms2026.enums.PaymentStatus;
import com.sep490.slms2026.enums.Role;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.repository.TenantContractRepository;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.security.CustomUserDetailsService;
import com.sep490.slms2026.security.JwtUtil;
import com.sep490.slms2026.service.OtpService;
import com.sep490.slms2026.service.TenantActivationService;
import com.sep490.slms2026.util.PhoneUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantActivationServiceImpl implements TenantActivationService {

    private final UserRepository userRepository;
    private final TenantContractRepository tenantContractRepository;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;
    private final CustomUserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;

    @Override
    @Transactional(readOnly = true)
    public TenantActivateCheckResponse check(String phoneNumber) {
        String localPhone = PhoneUtils.normalizeLocal(phoneNumber);
        Optional<User> userOpt = findUserByPhone(localPhone);

        if (userOpt.isEmpty()) {
            return TenantActivateCheckResponse.builder()
                    .status("NOT_FOUND")
                    .message("Không tìm thấy tài khoản gắn với số điện thoại này. Vui lòng liên hệ quản lý.")
                    .build();
        }

        User user = userOpt.get();
        if (user.getRole() != Role.ROLE_TENANT) {
            return TenantActivateCheckResponse.builder()
                    .status("NOT_ELIGIBLE")
                    .message("Số điện thoại này không thuộc tài khoản khách thuê.")
                    .username(user.getUsername())
                    .build();
        }

        if (user.getStatus() == com.sep490.slms2026.enums.UserStatus.DISABLE) {
            return TenantActivateCheckResponse.builder()
                    .status("NOT_ELIGIBLE")
                    .message("Tài khoản đã ngừng hoạt động do hợp đồng thuê đã kết thúc. Vui lòng liên hệ quản lý nếu bạn cần thuê lại.")
                    .username(user.getUsername())
                    .build();
        }

        if (!user.isFirstLogin()) {
            return TenantActivateCheckResponse.builder()
                    .status("READY_TO_LOGIN")
                    .message("Tài khoản đã kích hoạt. Vui lòng đăng nhập bằng số điện thoại và mật khẩu.")
                    .username(user.getUsername())
                    .build();
        }

        if (!hasEligibleContract(user)) {
            return TenantActivateCheckResponse.builder()
                    .status("NOT_ELIGIBLE")
                    .message("Chưa có hợp đồng đã thanh toán. Vui lòng hoàn tất thanh toán cọc với quản lý trước.")
                    .username(user.getUsername())
                    .build();
        }

        return TenantActivateCheckResponse.builder()
                .status("NEEDS_ACTIVATION")
                .message("Tài khoản chưa kích hoạt. Nhập OTP rồi tạo mật khẩu để tiếp tục.")
                .username(user.getUsername())
                .build();
    }

    @Override
    @Transactional
    public void sendOtp(String phoneNumber) {
        User user = requireActivatableTenant(phoneNumber);
        long referenceId = activationReferenceId(user);

        log.info("Gửi OTP kích hoạt tài khoản tenant {} tới số override {}",
                user.getUsername(), OtpDeliveryOverride.PHONE);
        otpService.sendOtp(OtpDeliveryOverride.PHONE, OtpPurpose.TENANT_ACTIVATION, referenceId);
    }

    @Override
    @Transactional
    public AuthResponse confirm(TenantActivateConfirmRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("Xác nhận mật khẩu không khớp");
        }

        User user = requireActivatableTenant(request.getPhoneNumber());
        long referenceId = activationReferenceId(user);

        otpService.verifyOrThrow(
                OtpDeliveryOverride.PHONE,
                request.getOtp(),
                OtpPurpose.TENANT_ACTIVATION,
                referenceId);

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setFirstLogin(false);
        userRepository.save(user);

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        String jwt = jwtUtil.generateToken(userDetails);
        String roleName = userDetails.getAuthorities().iterator().next().getAuthority();
        return new AuthResponse(jwt, userDetails.getUsername(), roleName, false);
    }

    private User requireActivatableTenant(String phoneNumber) {
        TenantActivateCheckResponse check = check(phoneNumber);
        if (!"NEEDS_ACTIVATION".equals(check.getStatus())) {
            throw new BusinessException(check.getMessage());
        }
        return findUserByPhone(PhoneUtils.normalizeLocal(phoneNumber))
                .orElseThrow(() -> new BusinessException(check.getMessage()));
    }

    /** Cho phép kích hoạt khi đã ACTIVE hoặc PENDING+PAID (chờ dual-OTP confirm). */
    private boolean hasEligibleContract(User user) {
        return tenantContractRepository.findByTenantId(user.getId()).stream()
                .anyMatch(c -> c.getStatus() == ContractStatus.ACTIVE
                        || (c.getStatus() == ContractStatus.PENDING
                        && c.getPaymentStatus() == PaymentStatus.PAID));
    }

    private Optional<User> findUserByPhone(String localPhone) {
        return userRepository.findByPhoneNumber(localPhone)
                .or(() -> userRepository.findByPhoneNumber(PhoneUtils.toInternational(localPhone)))
                .or(() -> userRepository.findByUsername(localPhone));
    }

    /** Gắn OTP với đúng user đang kích hoạt (kể khi SMS gửi về số override). */
    private static long activationReferenceId(User user) {
        return Math.abs(user.getId().getMostSignificantBits());
    }
}
