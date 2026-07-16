package com.sep490.slms2026.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DocxTemplateRendererLayoutTest {

    @Test
    void normalizeRenderedLayout_splitsGluedTenantBlock() {
        String glued = "Ông/bà: Lê Minh Châu  Số CCCD: 079085001003Ngày sinh: 20/01/1995"
                + "Cấp ngày: 01/11/2020 — Nơi cấp: CA Bình ThạnhHKTT: 88 Phạm Văn Đồng"
                + "Điện thoại: 0901000003Người ở cùng: Không có thành viên ở cùng.Sau khi bàn bạc";
        String normalized = DocxTemplateRenderer.normalizeRenderedLayout(glued);
        assertTrue(normalized.contains("\nSố CCCD:") || normalized.contains("Số CCCD: 079085001003\n"));
        assertTrue(normalized.contains("\nNgày sinh:"));
        assertTrue(normalized.contains("\nHKTT:"));
        assertTrue(normalized.contains("ở cùng.\n\nSau khi bàn bạc"));
    }
}
