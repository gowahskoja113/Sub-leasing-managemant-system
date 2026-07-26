package com.sep490.slms2026.constant;

/**
 * DEV/budget: luôn gửi & verify OTP tới số này thay vì SĐT trên hợp đồng / SĐT khách nhập.
 * {@code formatVietnamesePhone} chuẩn hóa thành {@code +84352393203}.
 */
public final class OtpDeliveryOverride {

    public static final String PHONE = "0352393203";

    private OtpDeliveryOverride() {
    }
}
