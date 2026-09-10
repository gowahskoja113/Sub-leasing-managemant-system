package com.sep490.slms2026.util;

/**
 * Chuẩn hoá mã khách hàng điện/nước khi ghi DB / so khớp.
 * Bỏ khoảng trắng, dấu gạch và mọi ký tự không phải chữ-số — OCR hay chèn space.
 */
public final class UtilityCustomerCodeHelper {

    public static final int MAX_LENGTH = 64;

    private UtilityCustomerCodeHelper() {
    }

    /**
     * Chỉ giữ chữ-số, lowercase; blank → null.
     * Ví dụ: {@code "PE 0500-0222239"} → {@code "pe05000222239"}.
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String stripped = raw.replaceAll("[^A-Za-z0-9]", "");
        if (stripped.isEmpty()) {
            return null;
        }
        return stripped.toLowerCase();
    }
}
