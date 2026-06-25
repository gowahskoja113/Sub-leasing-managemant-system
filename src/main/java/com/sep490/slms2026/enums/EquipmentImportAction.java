package com.sep490.slms2026.enums;

/**
 * Hành động khi import / gán thiết bị mua mới.
 * THEM_MOI — bổ sung thêm, giữ thiết bị cũ đang ACTIVE.
 * THAY_THE — thay thế thiết bị ACTIVE cùng vị trí + cùng catalog.
 */
public enum EquipmentImportAction {
    THEM_MOI,
    THAY_THE;

    public static EquipmentImportAction parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return THEM_MOI;
        }
        String upper = raw.trim().toUpperCase();
        return switch (upper) {
            case "THAY_THE", "REPLACE", "THAY THE" -> THAY_THE;
            default -> THEM_MOI;
        };
    }
}
