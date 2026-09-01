package com.sep490.slms2026.enums;

/**
 * Redesigned maintenance flow (2026-09):
 * OPEN → IN_REPAIR → CLOSED (Luồng A — hao mòn)
 * OPEN → TENANT_FAULT → CLOSED | PENDING_TENANT_REPAIR → CLOSED | OUTSTANDING_DAMAGE (Luồng B)
 */
public enum MaintenanceStatus {
    /** Tenant tạo, chờ manager check */
    OPEN,
    /** Manager approve, đang sửa (Luồng A) hoặc đang xử lý sau reject-fault nhánh manager sửa */
    IN_REPAIR,
    /** Manager reject — lỗi do tenant */
    TENANT_FAULT,
    /** Giao tenant tự sửa */
    PENDING_TENANT_REPAIR,
    /** Tenant không sửa / quá hạn — chờ checkout trừ cọc */
    OUTSTANDING_DAMAGE,
    /** Hoàn tất */
    CLOSED,
    CANCELLED
}
