package com.sep490.slms2026.util;

import com.sep490.slms2026.exception.BusinessException;
import org.springframework.util.StringUtils;

public final class PhoneUtils {

    private PhoneUtils() {
    }

    /** Chuẩn hóa SĐT VN về dạng 0xxxxxxxxx để lưu/so khớp trong hệ thống. */
    public static String normalizeLocal(String phone) {
        if (!StringUtils.hasText(phone)) {
            throw new BusinessException("Số điện thoại không hợp lệ");
        }
        String digits = phone.trim().replaceAll("[\\s\\-().]", "");
        if (digits.startsWith("+84")) {
            digits = "0" + digits.substring(3);
        } else if (digits.startsWith("84") && digits.length() >= 11) {
            digits = "0" + digits.substring(2);
        }
        if (!digits.matches("0\\d{9}")) {
            throw new BusinessException("Số điện thoại không hợp lệ");
        }
        return digits;
    }

    public static String toInternational(String localPhone) {
        String local = normalizeLocal(localPhone);
        return "+84" + local.substring(1);
    }

    /** Che SĐT cho manager: giữ 3 số đầu + 3 số cuối. */
    public static String maskForManager(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }
        String digits = phone.trim().replaceAll("[\\s\\-().]", "");
        if (digits.startsWith("+84")) {
            digits = "0" + digits.substring(3);
        } else if (digits.startsWith("84") && digits.length() >= 11) {
            digits = "0" + digits.substring(2);
        }
        if (digits.length() < 7) {
            return "***";
        }
        return digits.substring(0, 3) + "****" + digits.substring(digits.length() - 3);
    }
}
