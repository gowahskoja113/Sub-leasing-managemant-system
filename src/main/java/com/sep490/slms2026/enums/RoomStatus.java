package com.sep490.slms2026.enums;

public enum RoomStatus {
    DRAFT,          // Vừa tạo, chưa có giá thật
    AVAILABLE,      // Sẵn sàng cho thuê
    RENTED,
    MAINTENANCE,    // Đang cải tạo / sửa chữa
    DISABLED        // Ngưng khai thác — không nhận khách / không tạo hóa đơn
}
