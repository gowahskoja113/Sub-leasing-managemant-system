package com.sep490.slms2026.enums;

/**
 * Trạng thái đồng ý bồi thường chi phí bảo trì (độc lập với {@link MaintenanceStatus}).
 */
public enum CostAgreementStatus {
    /** Chủ nhà chịu / không thu khách — không hỏi tiền. */
    NOT_APPLICABLE,
    /** Đã complete với costPaidBy=TENANT, chờ khách đồng ý/khiếu nại. */
    PENDING,
    /** Khách đồng ý trả. */
    AGREED,
    /** Khách khiếu nại số tiền — không tự tạo charge. */
    DISPUTED
}
