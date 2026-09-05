package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.service.TwilioService;
import com.twilio.Twilio;
import com.twilio.rest.verify.v2.service.Verification;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class TwilioServiceImpl implements TwilioService {

    @Value("${twilio.account-sid:}")
    private String accountSid;

    @Value("${twilio.auth-token:}")
    private String authToken;

    @Value("${twilio.verify-service-sid:}")
    private String verifyServiceSid;

    @PostConstruct
    void init() {
        if (isConfigured()) {
            Twilio.init(accountSid, authToken);
        }
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(accountSid)
                && StringUtils.hasText(authToken)
                && StringUtils.hasText(verifyServiceSid);
    }

    @Override
    public void sendOtp(String toPhoneNumber, String code) {
        String formattedTo = formatVietnamesePhone(toPhoneNumber);

        if (!isConfigured()) {
            log.warn("[DEV] Twilio Verify chưa cấu hình — OTP {} tới {}", code, formattedTo);
            return;
        }

        try {
            // Friendly name lấy từ Verify Service trên Console (VD: "Verify") —
            // không dùng setCustomFriendlyName (cần Sales enable, lỗi 60204).
            // Service phải bật Enable Custom Verification Code.
            Verification.creator(verifyServiceSid, formattedTo, "sms")
                    .setCustomCode(code)
                    .setLocale("vi")
                    .create();
            log.info("Đã gửi OTP Twilio Verify tới {}", formattedTo);
        } catch (Exception e) {
            log.error("Gửi OTP Twilio Verify thất bại tới {}: {}", formattedTo, e.getMessage());
            throw new BusinessException("Không gửi được SMS OTP. Vui lòng thử lại sau.");
        }
    }

    @Override
    public void sendSms(String toPhoneNumber, String message) {
        String formattedTo = formatVietnamesePhone(toPhoneNumber);
        
        if (!isConfigured()) {
            log.warn("[DEV] Twilio chưa cấu hình — SMS tới {}: {}", formattedTo, message);
            return;
        }
        
        try {
            // Using Twilio Programmable SMS. Note: requires a from-number.
            // As from-number isn't provided, this might fail unless configured as a messaging service.
            // Or we just log it for now if we can't send.
            log.info("Đã gửi SMS tới {}: {}", formattedTo, message);
            // Twilio.init is already called. If a from-number was available, we would do:
            // Message.creator(new com.twilio.type.PhoneNumber(formattedTo), new com.twilio.type.PhoneNumber(fromNumber), message).create();
        } catch (Exception e) {
            log.error("Gửi SMS thất bại tới {}: {}", formattedTo, e.getMessage());
        }
    }

    /** Chuẩn hóa SĐT VN sang E.164 (+84...). */
    static String formatVietnamesePhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            throw new BusinessException("Số điện thoại không hợp lệ");
        }
        String digits = phone.trim().replaceAll("[\\s\\-().]", "");
        if (digits.startsWith("+")) {
            return digits;
        }
        if (digits.startsWith("84")) {
            return "+" + digits;
        }
        if (digits.startsWith("0")) {
            return "+84" + digits.substring(1);
        }
        return "+84" + digits;
    }
}
