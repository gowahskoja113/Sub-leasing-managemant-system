package com.sep490.slms2026.util;

import java.util.Map;

/**
 * Mau tin CSKH co san cua eSMS (Brandname test {@link #BRANDNAME}, SmsType 2).
 * Chi thay doi gia tri {code} (toi da 8 ky tu so/chu), khong doi cau con lai.
 *
 * @see <a href="https://developers.esms.vn/esms-api/ham-gui-tin/tin-nhan-sms-otp-cskh">eSMS OTP/CSKH</a>
 */
public final class EsmsBuiltinTemplates {

    public static final String BRANDNAME = "Baotrixemay";
    public static final String SMS_TYPE = "2";
    public static final int MAX_CODE_LENGTH = 8;

    public static final String REGISTRATION =
            "{code} la ma xac minh dang ky Baotrixemay cua ban";
    public static final String PASSWORD_RESET =
            "{code} la ma dat lai mat khau Baotrixemay cua ban";
    public static final String THANK_YOU =
            "Cam on quy khach da su dung dich vu cua chung toi. Chuc quy khach mot ngay tot lanh!";

    private static final Map<String, String> BY_ID = Map.of(
            "registration", REGISTRATION,
            "password-reset", PASSWORD_RESET,
            "thank-you", THANK_YOU
    );

    private EsmsBuiltinTemplates() {
    }

    public static String resolve(String templateId) {
        String template = BY_ID.get(templateId == null ? "" : templateId.trim().toLowerCase());
        if (template == null) {
            throw new IllegalArgumentException(
                    "ESMS_BUILTIN_TEMPLATE khong hop le: " + templateId
                            + ". Gia tri: registration | password-reset | thank-you"
            );
        }
        return template;
    }
}
