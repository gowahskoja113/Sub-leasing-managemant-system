package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.service.TwilioService;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
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

    @Value("${twilio.from-number:}")
    private String fromNumber;

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
                && StringUtils.hasText(fromNumber);
    }

    @Override
    public void sendSms(String toPhoneNumber, String message) {
        String formattedTo = formatVietnamesePhone(toPhoneNumber);

        if (!isConfigured()) {
            log.warn("[DEV] Twilio chưa cấu hình — SMS tới {}: {}", formattedTo, message);
            return;
        }

        try {
            Message.creator(
                    new PhoneNumber(formattedTo),
                    new PhoneNumber(fromNumber),
                    message
            ).create();
            log.info("Đã gửi SMS Twilio tới {}", formattedTo);
        } catch (Exception e) {
            log.error("Gửi SMS Twilio thất bại tới {}: {}", formattedTo, e.getMessage());
            throw new BusinessException("Không gửi được SMS OTP. Vui lòng thử lại sau.");
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
