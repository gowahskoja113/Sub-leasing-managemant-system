package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.entity.OtpVerification;
import com.sep490.slms2026.enums.OtpPurpose;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.repository.OtpVerificationRepository;
import com.sep490.slms2026.service.OtpService;
import com.sep490.slms2026.service.TwilioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpVerificationRepository otpVerificationRepository;
    private final TwilioService twilioService;

    @Value("${twilio.otp.expiry-minutes:5}")
    private int expiryMinutes;

    @Value("${twilio.otp.max-attempts:5}")
    private int maxAttempts;

    @Override
    @Transactional
    public void sendOtp(String phoneNumber, OtpPurpose purpose, Long referenceId) {
        String normalizedPhone = TwilioServiceImpl.formatVietnamesePhone(phoneNumber);
        String code = generateCode();

        OtpVerification otp = OtpVerification.builder()
                .phoneNumber(normalizedPhone)
                .code(code)
                .purpose(purpose)
                .referenceId(referenceId)
                .expiresAt(LocalDateTime.now().plusMinutes(expiryMinutes))
                .build();
        otpVerificationRepository.save(otp);

        String message = buildMessage(code, purpose);
        twilioService.sendSms(normalizedPhone, message);

        if (!twilioService.isConfigured()) {
            log.warn("[DEV] Twilio chưa cấu hình — mã OTP {} cho {} (purpose={}, ref={})",
                    code, normalizedPhone, purpose, referenceId);
        }
    }

    @Override
    @Transactional
    public void verifyOrThrow(String phoneNumber, String code, OtpPurpose purpose, Long referenceId) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException("Vui lòng nhập mã OTP");
        }
        if (!code.trim().matches("\\d{6}")) {
            throw new BusinessException("Mã OTP phải gồm 6 chữ số");
        }

        String normalizedPhone = TwilioServiceImpl.formatVietnamesePhone(phoneNumber);
        OtpVerification otp = otpVerificationRepository
                .findTopByPhoneNumberAndPurposeAndReferenceIdAndVerifiedFalseOrderByCreatedAtDesc(
                        normalizedPhone, purpose, referenceId)
                .orElseThrow(() -> new BusinessException("Chưa có mã OTP hoặc mã đã hết hạn. Vui lòng gửi lại."));

        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Mã OTP đã hết hạn. Vui lòng gửi lại.");
        }
        if (otp.getAttemptCount() >= maxAttempts) {
            throw new BusinessException("Đã nhập sai quá số lần cho phép. Vui lòng gửi lại mã OTP.");
        }

        if (!otp.getCode().equals(code.trim())) {
            otp.setAttemptCount(otp.getAttemptCount() + 1);
            otpVerificationRepository.save(otp);
            throw new BusinessException("Mã OTP không đúng");
        }

        otp.setVerified(true);
        otpVerificationRepository.save(otp);
    }

    private static String generateCode() {
        int value = RANDOM.nextInt(1_000_000);
        return String.format("%06d", value);
    }

    private String buildMessage(String code, OtpPurpose purpose) {
        return switch (purpose) {
            case CONTRACT_CONFIRM -> "[SLMS] Ma xac nhan hop dong cua ban la: " + code
                    + ". Hieu luc " + expiryMinutes + " phut. Khong chia se ma nay.";
        };
    }
}
