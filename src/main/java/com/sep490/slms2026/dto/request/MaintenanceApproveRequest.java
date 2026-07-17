package com.sep490.slms2026.dto.request;

import lombok.Data;

@Data
public class MaintenanceApproveRequest {
    /**
     * Dùng cho /review-reject:
     * true  = chấp nhận từ chối của tenant → quay lại APPROVED
     * false = không reopen → WAITING_TENANT_CONFIRM
     * Endpoint /approve (duyệt request mới) bỏ qua field này.
     */
    private boolean approve = true;

    /**
     * Bắt buộc khi manager duyệt request mới (PENDING → APPROVED).
     * Phân loại phục vụ báo cáo chi phí sau sửa chữa.
     */
    private String category;

    /** Tùy chọn — manager có thể gán mức độ ưu tiên khi duyệt */
    private String priority;
}
