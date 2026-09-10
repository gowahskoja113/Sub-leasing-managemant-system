package com.sep490.slms2026.util;

/**
 * Chuẩn hoá mã khách hàng điện/nước khi ghi DB / so khớp.
 */
public final class UtilityCustomerCodeHelper {

    public static final int MAX_LENGTH = 64;

    private UtilityCustomerCodeHelper() {
    }

    /** Trim + lowercase; blank → null. */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase();
    }
}
