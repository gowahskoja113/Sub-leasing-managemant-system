package com.sep490.slms2026.dto.request;

import lombok.Data;

import java.time.LocalDateTime;

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
     * Phân loại phục vụ báo cáo chi phí sau sửa chữa.
     * Bắt buộc khi ticket chưa có category (vd. báo hỏng gắn thiết bị).
     * Nếu tenant đã chọn lúc tạo → có thể bỏ trống (giữ nguyên) hoặc gửi để ghi đè.
     */
    private String category;

    /** Tùy chọn — manager có thể gán mức độ ưu tiên khi duyệt */
    private String priority;

    /**
     * Dùng cho /review-reject khi approve=false:
     * manager bắt buộc nêu lý do giữ nguyên kết quả sửa chữa.
     */
    private String note;

    /**
     * Tùy chọn — nếu có → chuyển REPAIR_SCHEDULED thay vì sửa ngay (IN_REPAIR).
     */
    private LocalDateTime repairAppointmentAt;
}
