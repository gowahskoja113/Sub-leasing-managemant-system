package com.sep490.slms2026.enums;

public enum PropertyStatus {
    /** Đang nhập thông tin cơ bản (chưa hoàn thành quy trình 1) */
    DRAFT,
    /** Hoàn thành quy trình 1: thông tin cơ bản + hợp đồng inbound */
    PENDING,
    /** Quy trình 2: chọn cải tạo / loại hình thuê — đang cải tạo */
    UNDER_RENOVATION,
    /** Quy trình 3: gán thiết bị — chờ lắp đặt thiết bị */
    PENDING_EQUIPMENT_INSTALLATION,
    /** Hoàn thành 3 quy trình onboarding — đã cải tạo xong */
    RENOVATION_COMPLETED,
    /** Đã tính khấu hao và gửi host xem xét */
    PENDING_HOST_REVIEW,
    /** Host đã xác nhận giá — chờ gán Operation Manager */
    PENDING_OPERATION_MANAGER,
    ACTIVE,
    DISABLED,
    MAINTENANCE,
    INACTIVE;

    public boolean isOnboardingEditable() {
        return this == DRAFT
                || this == PENDING
                || this == UNDER_RENOVATION
                || this == PENDING_EQUIPMENT_INSTALLATION
                || this == RENOVATION_COMPLETED;
    }

    public boolean isEquipmentEditable() {
        return this == DRAFT
                || this == PENDING
                || this == UNDER_RENOVATION
                || this == PENDING_EQUIPMENT_INSTALLATION;
    }
}
