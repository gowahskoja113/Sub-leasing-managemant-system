package com.sep490.slms2026.enums;

/** Loại thiết bị trong ngữ cảnh bàn giao HĐ thuê (map cho FE). */
public enum ContractEquipmentSource {
    /** Có sẵn trong nhà — INITIAL_HANDOVER / PURCHASED. */
    EXISTING,
    /** Khách/manager kê thêm, chủ mua lắp — ADDED_BY_TENANT. */
    ADDED
}
