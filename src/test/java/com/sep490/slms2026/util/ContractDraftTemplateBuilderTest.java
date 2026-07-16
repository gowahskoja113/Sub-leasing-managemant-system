package com.sep490.slms2026.util;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractDraftTemplateBuilderTest {

    @Test
    void normalizeExistingClasspathTemplate() throws Exception {
        Path templatePath = Path.of("src/main/resources/templates/contract/tenant-apartment-draft-template.docx");
        assertTrue(Files.exists(templatePath), "Thiếu template classpath");

        Map<String, byte[]> entries = new LinkedHashMap<>();
        byte[] documentXml = null;
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(Files.newInputStream(templatePath))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                byte[] data = zis.readAllBytes();
                if ("word/document.xml".equals(entry.getName())) {
                    documentXml = data;
                } else {
                    entries.put(entry.getName(), data);
                }
                zis.closeEntry();
            }
        }
        assertTrue(documentXml != null, "Thiếu word/document.xml");
        String xml = new String(documentXml, java.nio.charset.StandardCharsets.UTF_8);
        xml = ApartmentDraftTemplateBuilder.normalizePreparedTemplate(xml);
        entries.put("word/document.xml", xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zos.putNextEntry(new java.util.zip.ZipEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        Files.write(templatePath, out.toByteArray());
    }

    @Test
    void buildApartmentDraftTemplate_fromSourceDocx() throws Exception {
        Path source = Path.of("docs/Template_contract_source.docx");
        if (!Files.exists(source)) {
            source = Path.of("docs/Template_contract (1).docx");
        }
        assertTrue(Files.exists(source), "Thiếu file mẫu: docs/Template_contract_source.docx hoặc docs/Template_contract (1).docx");

        try (InputStream in = Files.newInputStream(source)) {
            byte[] template = ApartmentDraftTemplateBuilder.buildFromSource(in);

            Path classpathOut = Path.of("src/main/resources/templates/contract/tenant-apartment-draft-template.docx");
            Path docsOut = Path.of("docs/Template_contract (1).docx");
            Files.createDirectories(classpathOut.getParent());
            Files.write(classpathOut, template);
            Files.write(docsOut, template);

            Map<String, String> sample = new HashMap<>();
            sample.put("contractCode", "TC-TEST-001");
            sample.put("signPlace", "TP. HCM");
            sample.put("signDay", "9");
            sample.put("signMonth", "7");
            sample.put("signYear", "2026");
            sample.put("tenantFullName", "Nguyễn Văn A");
            sample.put("tenantCccd", "001234567890");
            sample.put("tenantDob", "01/01/1990");
            sample.put("tenantCccdIssueDate", "15/03/2021");
            sample.put("tenantCccdIssuePlace", "CA TP. Hồ Chí Minh");
            sample.put("tenantAddress", "123 Đường ABC, Q1");
            sample.put("tenantPhone", "0901234567");
            sample.put("householdMembers", "Không có thành viên ở cùng.\n\n");
            sample.put("rentalUnit", "Sunrise Tower - Phòng 101");
            sample.put("areaSize", "45");
            sample.put("leaseDurationMonths", "12");
            sample.put("startDate", "09/07/2026");
            sample.put("endDate", "09/07/2027");
            sample.put("rentAmount", "8.000.000");
            sample.put("rentAmountInWords", "tám triệu đồng");
            sample.put("deposit", "16.000.000");
            sample.put("depositInWords", "mười sáu triệu đồng");
            sample.put("equipmentSnapshot", "Giường, tủ, điều hòa");

            byte[] rendered = DocxTemplateRenderer.render(
                    new java.io.ByteArrayInputStream(template), sample);
            assertFalse(ApartmentDraftTemplateBuilder.containsUnresolvedPlaceholders(rendered),
                    "DOCX render vẫn còn placeholder chưa thay");
        }
    }
}
