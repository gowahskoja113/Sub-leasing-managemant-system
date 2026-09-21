package com.sep490.slms2026.enums;

/**
 * Redesigned maintenance flow (2026-09):
 * OPEN → [REPAIR_SCHEDULED] → IN_REPAIR → CLOSED (Luồng A — hao mòn)
 * OPEN → … → TENANT_FAULT → (sửa/bàn giao) → WAITING_PAYMENT → CLOSED khi PAID
 * OPEN → TENANT_FAULT → PENDING_TENANT_REPAIR → … (Luồng B tự sửa)
 */
public enum MaintenanceStatus {
    /** Tenant tạo, chờ manager tới xem (có visitAppointmentAt) */
    OPEN,
    /** Đã duyệt/báo lỗi, chờ tới lịch sửa (đặt lịch sau thay vì sửa ngay) */
    REPAIR_SCHEDULED,
    /** Manager approve, đang sửa (Luồng A) hoặc đang xử lý sau reject-fault nhánh manager sửa */
    IN_REPAIR,
    /** Manager reject — lỗi do tenant */
    TENANT_FAULT,
    /** Giao tenant tự sửa */
    PENDING_TENANT_REPAIR,
    /** Tenant không sửa / quá hạn — chờ checkout trừ cọc */
    OUTSTANDING_DAMAGE,
    /**
     * Đã sửa/bàn giao xong nhưng tenant chưa thanh toán hoá đơn bồi thường.
     * Chỉ chuyển CLOSED khi hoá đơn PAID.
     */
    WAITING_PAYMENT,
    /** Hoàn tất (và đã thanh toán nếu có thu tenant) */
    CLOSED,
    CANCELLED
}
