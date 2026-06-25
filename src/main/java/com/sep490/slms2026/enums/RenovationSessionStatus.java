package com.sep490.slms2026.enums;

/**
 * Trạng thái đợt cải tạo theo session.
 * ACTIVE = đang có hiệu lực (chỉ một session / nhà).
 * DISABLED = đã bị thay thế bởi đợt cải tạo mới hơn.
 */
public enum RenovationSessionStatus {
    IN_PROGRESS,
    ACTIVE,
    DISABLED
}
