package com.sep490.slms2026.util;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SmsService {

    @Value("${twilio.account.sid}")
    private String accountSid;

    @Value("${twilio.auth.token}")
    private String authToken;

    @Value("${twilio.phone.number}")
    private String twilioPhoneNumber;

    @PostConstruct
    public void init() {
        Twilio.init(accountSid, authToken);
    }

    // @Async rất quan trọng: Giúp việc gửi SMS chạy ngầm ở một luồng khác,
    // không làm người dùng phải chờ đợi lâu khi bấm nút "Tạo tài khoản"
    @Async
    public void sendCredentialsSms(String toPhoneNumber, String username, String rawPassword) {
        try {
            String content = String.format("Chao ban, tai khoan thue tro cua ban da duoc tao. Username: %s, Password: %s", username, rawPassword);

            // Format số điện thoại về chuẩn quốc tế (VD: 0987... -> +84987...)
            String formattedPhone = formatPhoneNumber(toPhoneNumber);

            Message message = Message.creator(
                            new PhoneNumber(formattedPhone), // Số người nhận
                            new PhoneNumber(twilioPhoneNumber), // Số tổng đài Twilio
                            content)
                    .create();

            log.info("Đã gửi SMS thành công. Message ID: {}", message.getSid());
        } catch (Exception e) {
            log.error("Lỗi khi gửi SMS: {}", e.getMessage());
            // Lưu ý: Lỗi gửi tin nhắn KHÔNG NÊN làm crash toàn bộ hệ thống
        }
    }

    private String formatPhoneNumber(String phone) {
        if (phone.startsWith("0")) {
            return "+84" + phone.substring(1);
        }
        return phone;
    }
}