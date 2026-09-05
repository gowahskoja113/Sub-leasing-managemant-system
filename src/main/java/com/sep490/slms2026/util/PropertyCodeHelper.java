package com.sep490.slms2026.util;

import java.util.Locale;
import java.util.regex.Pattern;

public final class PropertyCodeHelper {

    public static final int MAX_LENGTH = 32;

    private static final Pattern VALID_CODE = Pattern.compile("^[a-zA-Z0-9#\\-_]{1," + MAX_LENGTH + "}$");
    private static final Pattern CODE_LIKE_TOKEN = Pattern.compile(".*[a-zA-Z0-9].*");
    private static final Pattern MTX_NUMBER = Pattern.compile("^mtx#(\\d+)$", Pattern.CASE_INSENSITIVE);

    private PropertyCodeHelper() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isValidFormat(String code) {
        return code != null && VALID_CODE.matcher(code).matches();
    }

    /** Giống FE: token đầu của propertyName, lower-case. */
    public static String extractFromPropertyName(String propertyName) {
        if (propertyName == null || propertyName.isBlank()) {
            return null;
        }
        String token = propertyName.trim().split("\\s+")[0];
        String normalized = normalize(token);
        return looksLikeCode(normalized) ? normalized : null;
    }

    public static boolean looksLikeCode(String token) {
        return token != null && CODE_LIKE_TOKEN.matcher(token).matches();
    }

    public static String withCollisionSuffix(String base, int suffix) {
        String suffixPart = "-" + suffix;
        int maxBaseLen = MAX_LENGTH - suffixPart.length();
        if (maxBaseLen < 1) {
            return base.substring(0, Math.min(base.length(), MAX_LENGTH));
        }
        String trimmedBase = base.length() > maxBaseLen ? base.substring(0, maxBaseLen) : base;
        return trimmedBase + suffixPart;
    }

    public static int parseMtxNumber(String code) {
        if (code == null) {
            return -1;
        }
        var matcher = MTX_NUMBER.matcher(code);
        if (!matcher.matches()) {
            return -1;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static String formatMtxCode(int number) {
        return "mtx#" + number;
    }
}
