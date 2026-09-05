package com.sep490.slms2026.util;

/**
 * Mã phương thức thanh toán trả ra FE.
 * PayOS = QR chuyển khoản; không dùng {@code PAYOS}/{@code OTHER} trên hoá đơn (FE hiện "Khác").
 */
public final class PaymentMethods {

    public static final String QR = "QR";
    public static final String CASH = "CASH";
    public static final String BANK_TRANSFER = "BANK_TRANSFER";

    private PaymentMethods() {
    }

    /** {@code PAYOS} (nội bộ / data cũ) → {@code QR}. CASH / QR / BANK_TRANSFER giữ nguyên. */
    public static String toPublic(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        if ("PAYOS".equalsIgnoreCase(raw.trim())) {
            return QR;
        }
        return raw.trim();
    }
}
