package com.sep490.slms2026.util;

import com.sep490.slms2026.enums.UtilityType;
import com.sep490.slms2026.exception.BusinessException;

public final class UtilityTypeMapper {

    private UtilityTypeMapper() {
    }

    public static UtilityType fromApi(String type) {
        if (type == null || type.isBlank()) {
            throw new BusinessException("Loại tiện ích không được để trống");
        }
        return switch (type.trim().toUpperCase()) {
            case "ELECTRICITY", "ELECTRIC" -> UtilityType.ELECTRIC;
            case "WATER" -> UtilityType.WATER;
            default -> throw new BusinessException("Loại tiện ích không hợp lệ: " + type);
        };
    }

    public static String toApi(UtilityType type) {
        return type == UtilityType.ELECTRIC ? "ELECTRICITY" : "WATER";
    }
}
