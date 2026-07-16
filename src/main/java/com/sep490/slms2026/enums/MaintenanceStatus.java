package com.sep490.slms2026.enums;

/**
 * Simplified maintenance flow:
 * PENDING → APPROVED → WAITING_TENANT_CONFIRM → CLOSED
 *                         ↘ REJECTED → (manager approve) → APPROVED
 */
public enum MaintenanceStatus {
    /** Tenant vừa tạo yêu cầu */
    PENDING,
    /** Manager đã duyệt, đang chờ thợ ngoài sửa */
    APPROVED,
    /** Manager báo sửa xong, chờ tenant xác nhận */
    WAITING_TENANT_CONFIRM,
    /** Tenant từ chối kết quả sửa (kèm lý do + ảnh) */
    REJECTED,
    /** Kết thúc luồng sửa chữa (tenant confirm hoặc auto-confirm) */
    CLOSED,
    CANCELLED,

    // --- Legacy (giữ để đọc dữ liệu cũ; đã migrate sang status mới) ---
    @Deprecated ACKNOWLEDGED,
    @Deprecated SCHEDULED,
    @Deprecated IN_PROGRESS,
    @Deprecated ON_HOLD,
    @Deprecated PENDING_APPROVAL,
    @Deprecated DONE,
    @Deprecated CONFIRMED,
    @Deprecated RESOLVED,
    @Deprecated REOPENED
}
