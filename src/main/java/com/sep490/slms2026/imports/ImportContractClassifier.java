package com.sep490.slms2026.imports;

/**
 * Suy ra có đợt 2 hay không từ mã HĐ demo (HD-*-NORENO-*).
 * Luồng cũ (onboarding-excel) vẫn dùng isWholeHouse theo prefix/tag.
 */
public final class ImportContractClassifier {

    private ImportContractClassifier() {
    }

    /** Luồng import cũ (1 file) — không dùng cho lease-excel / renovation-excel mới. */
    public static boolean isWholeHouse(String contractCode, String description) {
        if (contractCode != null) {
            String upper = contractCode.trim().toUpperCase();
            if (upper.startsWith("HD-WH-")) {
                return true;
            }
            if (upper.startsWith("HD-ROOM-")) {
                return false;
            }
        }
        if (description != null) {
            String desc = description.toUpperCase();
            if (desc.contains("[NGUYEN_CAN]")) {
                return true;
            }
            if (desc.contains("[THEO_PHONG]")) {
                return false;
            }
        }
        return true;
    }

    /** Mã HĐ không có dòng đợt 2 (HD-*-NORENO-*). */
    public static boolean expectsPhase2(String contractCode) {
        if (contractCode == null || contractCode.isBlank()) {
            return true;
        }
        return !contractCode.trim().toUpperCase().contains("NORENO");
    }

    public static boolean inferHasRenovationForPhase1(String contractCode) {
        return expectsPhase2(contractCode);
    }
}
