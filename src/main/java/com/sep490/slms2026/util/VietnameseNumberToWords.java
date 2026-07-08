package com.sep490.slms2026.util;

import java.math.BigDecimal;

/** Đọc số tiền VNĐ thành chữ (dùng in trên hợp đồng). */
public final class VietnameseNumberToWords {

    private static final String[] UNITS = {
            "", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín"
    };

    private VietnameseNumberToWords() {
    }

    public static String convert(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        long value = amount.longValue();
        if (value == 0) {
            return "Không đồng";
        }
        if (value < 0) {
            return "Âm " + convert(BigDecimal.valueOf(-value));
        }
        return capitalize(readTriple(value)) + " đồng";
    }

    private static String readTriple(long number) {
        if (number == 0) {
            return "không";
        }

        StringBuilder sb = new StringBuilder();
        long billion = number / 1_000_000_000;
        long million = (number % 1_000_000_000) / 1_000_000;
        long thousand = (number % 1_000_000) / 1_000;
        long remainder = number % 1_000;

        if (billion > 0) {
            sb.append(readHundreds((int) billion)).append(" tỷ");
        }
        if (million > 0) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(readHundreds((int) million)).append(" triệu");
        }
        if (thousand > 0) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(readHundreds((int) thousand)).append(" nghìn");
        }
        if (remainder > 0) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(readHundreds((int) remainder));
        }
        return sb.toString().trim();
    }

    private static String readHundreds(int number) {
        int hundred = number / 100;
        int ten = (number % 100) / 10;
        int unit = number % 10;

        StringBuilder sb = new StringBuilder();
        if (hundred > 0) {
            sb.append(UNITS[hundred]).append(" trăm");
        }
        if (ten > 1) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(UNITS[ten]).append(" mươi");
            if (unit == 1) {
                sb.append(" mốt");
            } else if (unit == 5) {
                sb.append(" lăm");
            } else if (unit > 0) {
                sb.append(" ").append(UNITS[unit]);
            }
        } else if (ten == 1) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append("mười");
            if (unit == 5) {
                sb.append(" lăm");
            } else if (unit > 0) {
                sb.append(" ").append(UNITS[unit]);
            }
        } else if (ten == 0 && unit > 0) {
            if (!sb.isEmpty()) sb.append(" lẻ ");
            sb.append(UNITS[unit]);
        }
        return sb.toString().trim();
    }

    private static String capitalize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
