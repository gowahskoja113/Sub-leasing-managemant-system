package com.sep490.slms2026.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class TenantActivateCheckResponse {

    /**
     * {@code NEEDS_ACTIVATION} — chưa đặt mật khẩu, đi flow OTP.
     * {@code READY_TO_LOGIN} — đã kích hoạt, dùng màn Đăng nhập.
     * {@code NOT_FOUND} — không có tài khoản / HĐ phù hợp.
     * {@code NOT_ELIGIBLE} — có user nhưng không phải tenant chờ kích hoạt.
     */
    private String status;

    private String message;

    /** Username đăng nhập (= SĐT local) khi tìm thấy tài khoản tenant. */
    private String username;
}
