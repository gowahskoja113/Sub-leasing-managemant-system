package com.sep490.slms2026.enums;

public enum OtpPurpose {
    /**
     * Legacy — xác nhận HĐ 1 mã (manager). Giữ trong DB constraint cho bản ghi cũ;
     * luồng mới dùng {@link #CONTRACT_CONFIRM_TENANT} / {@link #CONTRACT_CONFIRM_MANAGER}.
     */
    CONTRACT_CONFIRM,
    /** Khách thuê xác nhận đồng ý hợp đồng (1 trong 2 chữ ký OTP). */
    CONTRACT_CONFIRM_TENANT,
    /** Quản lý xác nhận đồng ý hợp đồng (1 trong 2 chữ ký OTP). */
    CONTRACT_CONFIRM_MANAGER,
    /** Tenant kích hoạt tài khoản lần đầu (OTP → tự đặt mật khẩu). */
    TENANT_ACTIVATION
}
