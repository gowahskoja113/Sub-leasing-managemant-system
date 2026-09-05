package com.sep490.slms2026.enums;

public enum RentEscalationType {
    NONE,
    /** Legacy: tăng theo năm kỷ niệm HĐ (tháng 13, 25, …). */
    PERCENT,
    SCHEDULE,
    /** Tăng vào 01/01 năm dương lịch (mặc định hệ thống). */
    ANNUAL_CALENDAR
}
