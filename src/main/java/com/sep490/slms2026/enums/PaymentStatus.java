package com.sep490.slms2026.enums;

public enum PaymentStatus {
    PENDING,    // chưa thanh toán cọc
    PAID,       // đã thanh toán (xác nhận qua webhook PayOS)
    FAILED,
    CANCELLED
}
