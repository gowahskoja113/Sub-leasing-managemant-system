package com.sep490.slms2026.dto.request;

import lombok.Data;

/**
 * Tenant xác nhận đã sửa xong.
 * Từ chối dùng endpoint /reject riêng (bắt buộc lý do + ảnh).
 */
@Data
public class MaintenanceConfirmRequest {
    /** Giữ field để tương thích FE cũ; chỉ chấp nhận accept=true. */
    private boolean accept = true;

    /**
     * Đồng ý trả khoản bồi thường.
     * Bắt buộc gửi (true/false) khi costAgreementStatus == PENDING.
     * true → tạo charge + hoá đơn; false → DISPUTED, không thu tự động.
     */
    private Boolean agreeToCharge;

    /** Lý do khiếu nại số tiền (khi agreeToCharge=false). */
    private String chargeDisputeReason;
}
