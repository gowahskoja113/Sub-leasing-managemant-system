package com.sep490.slms2026.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractDraftPdfRenderTest {

    @ParameterizedTest
    @ValueSource(strings = {
            ContractTemplateConstants.TEMPLATE_ROOM,
            ContractTemplateConstants.TEMPLATE_WHOLE_HOUSE
    })
    void renderContractTemplate_toPdf(String templateClasspath) throws Exception {
        Map<String, String> sample = sampleVariables();

        try (InputStream in = new ClassPathResource(templateClasspath).getInputStream()) {
            byte[] docx = DocxTemplateRenderer.render(in, sample);
            assertFalse(new String(docx).contains("${equipmentSnapshot}"),
                    "Placeholder equipmentSnapshot chưa được thay trong DOCX");
            byte[] pdf = DocxToPdfConverter.convert(docx);
            assertTrue(pdf.length > 100, "PDF quá nhỏ / trống");
            assertTrue(pdf[0] == '%' && pdf[1] == 'P' && pdf[2] == 'D' && pdf[3] == 'F',
                    "Output không phải PDF");
        }
    }

    private static Map<String, String> sampleVariables() {
        Map<String, String> sample = new HashMap<>();
        sample.put("contractCode", "TC-TEST-001");
        sample.put("signPlace", "TP. HCM");
        sample.put("signDay", "9");
        sample.put("signMonth", "7");
        sample.put("signYear", "2026");
        sample.put("tenantFullName", "Nguyễn Văn A");
        sample.put("tenantCccd", "001234567890");
        sample.put("tenantDob", "01/01/1990");
        sample.put("tenantAddress", "123 Đường ABC, Q1");
        sample.put("tenantPhone", "0901234567");
        sample.put("householdMembers", "Không có thành viên ở cùng.\n\n");
        sample.put("tenantCccdIssueDate", "15/03/2021");
        sample.put("tenantCccdIssuePlace", "CA TP. Hồ Chí Minh");
        sample.put("rentalUnit", "Sunrise Tower - Phòng 101");
        sample.put("areaSize", "45");
        sample.put("leaseDurationMonths", "12");
        sample.put("startDate", "09/07/2026");
        sample.put("endDate", "09/07/2027");
        sample.put("rentAmount", "8.000.000");
        sample.put("rentAmountInWords", "tám triệu đồng");
        sample.put("deposit", "16.000.000");
        sample.put("depositInWords", "mười sáu triệu đồng");
        sample.put("equipmentSnapshot", "Giường (Tốt) x1, Tủ (Tốt) x1, Điều hòa (Mới) x1");
        return sample;
    }

    @Test
    void forceTimesNewRoman_setsAllFontRanges() {
        org.apache.poi.xwpf.usermodel.XWPFDocument doc =
                new org.apache.poi.xwpf.usermodel.XWPFDocument();
        org.apache.poi.xwpf.usermodel.XWPFParagraph paragraph = doc.createParagraph();
        org.apache.poi.xwpf.usermodel.XWPFRun run = paragraph.createRun();
        run.setFontFamily("Arial");
        DocxTemplateRenderer.forceTimesNewRoman(run);
        assertTrue(run.getFontFamily().contains("Times New Roman"));
    }
}
