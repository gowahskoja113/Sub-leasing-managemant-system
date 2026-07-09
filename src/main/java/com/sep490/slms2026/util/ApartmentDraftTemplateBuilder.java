package com.sep490.slms2026.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Chuyển {@code docs/Template_contract (1).docx} (dòng chấm) sang template có {@code ${placeholder}}
 * cho luồng HĐ nháp (DRAFT) căn hộ.
 */
public final class ApartmentDraftTemplateBuilder {

    private static final String DOCUMENT_XML = "word/document.xml";
    private static final Pattern TEXT_NODE = Pattern.compile("(<w:t[^>]*>)([^<]*)(</w:t>)");

    private ApartmentDraftTemplateBuilder() {
    }

    public static byte[] buildFromSource(InputStream sourceDocx) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        byte[] documentXml = null;

        try (ZipInputStream zis = new ZipInputStream(sourceDocx)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                byte[] data = zis.readAllBytes();
                if (DOCUMENT_XML.equals(entry.getName())) {
                    documentXml = data;
                } else {
                    entries.put(entry.getName(), data);
                }
                zis.closeEntry();
            }
        }

        if (documentXml == null) {
            throw new IOException("Không tìm thấy " + DOCUMENT_XML + " trong file DOCX");
        }

        String xml = replaceTextNodes(new String(documentXml, StandardCharsets.UTF_8));
        entries.put(DOCUMENT_XML, xml.getBytes(StandardCharsets.UTF_8));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        return out.toByteArray();
    }

    static String replaceTextNodes(String xml) {
        Matcher matcher = TEXT_NODE.matcher(xml);
        StringBuffer sb = new StringBuffer();
        List<String> history = new ArrayList<>();
        boolean inHouseholdBlock = false;
        boolean leaseLineDone = false;

        while (matcher.find()) {
            String text = matcher.group(2);
            String trimmed = text.trim();
            String replacement = text;

            if (text.contains("1417-SUN/HĐTN-2026") || text.contains("1417-SUN/H\u0110TN-2026")) {
                replacement = text.replace("1417-SUN/HĐTN-2026", "${contractCode}")
                        .replace("1417-SUN/H\u0110TN-2026", "${contractCode}");
            } else if (trimmed.startsWith("Hôm nay, ngày")) {
                replacement = "Hôm nay, ngày ${signDay} tháng ${signMonth} năm ${signYear}, tại ${signPlace}, chúng tôi gồm:";
            } else if (isOnlyDots(text) && containsRecent(history, "Hôm nay, ngày")) {
                replacement = "";
            } else if (trimmed.equals(", tại") || trimmed.equals(", chúng tôi gồm:")) {
                replacement = "";
            } else if (trimmed.equals("Ông/bà:") && inTenantBSection(history)) {
                replacement = "Ông/bà: ${tenantFullName}";
            } else if (isOnlyDots(text) && containsRecent(history, "${tenantFullName}")) {
                replacement = "";
            } else if (trimmed.equals("CCCD:") && inTenantBSection(history)) {
                replacement = "Số CCCD: ${tenantCccd}";
            } else if (isOnlyDots(text) && containsRecent(history, "${tenantCccd}")) {
                replacement = "";
            } else if (trimmed.startsWith("HKTT:") && inTenantBSection(history)) {
                replacement = "HKTT: ${tenantAddress}";
            } else if (isOnlyDots(text) && containsRecent(history, "${tenantAddress}")) {
                replacement = "";
            } else if (trimmed.startsWith("Điện thoại:") && inTenantBSection(history)) {
                replacement = "Điện thoại: ${tenantPhone}";
            } else if (trimmed.equals("Người ở cùng")) {
                replacement = "Người ở cùng: ${householdMembers}";
                inHouseholdBlock = true;
            } else if (inHouseholdBlock) {
                if (trimmed.startsWith("Sau khi bàn bạc")) {
                    inHouseholdBlock = false;
                } else {
                    replacement = "";
                }
            } else if (text.contains("căn hộ chung cư số:")) {
                replacement = "Bên A đồng ý cho thuê và bên B đồng ý thuê căn hộ: ${rentalUnit}";
            } else if (isOnlyDots(text) && containsRecent(history, "${rentalUnit}")) {
                replacement = "";
            } else if (trimmed.equals("Tổng diện tích")) {
                replacement = "Tổng diện tích: ${areaSize} m²";
            } else if ((trimmed.equals(":") || isOnlyDots(text)) && containsRecent(history, "${areaSize}")) {
                replacement = "";
            } else if (trimmed.startsWith(": Thời hạn thuê") || trimmed.equals("Điều 3")) {
                if (!leaseLineDone) {
                    replacement = "Điều 3: Thời hạn thuê ${leaseDurationMonths} tháng, từ ngày ${startDate} đến ngày ${endDate}";
                    leaseLineDone = true;
                } else {
                    replacement = "";
                }
            } else if (leaseLineDone && (trimmed.startsWith("năm tính từ ngày")
                    || trimmed.startsWith("đến ngày") || isOnlyDots(text) || trimmed.equals(":"))) {
                replacement = "";
            } else if (trimmed.startsWith("4.1. Tiền thuê nhà mỗi tháng là:")) {
                replacement = "4.1. Tiền thuê nhà mỗi tháng là: ${rentAmount} VND (Bằng chữ: ${rentAmountInWords})";
            } else if (isOnlyDots(text) && containsRecent(history, "${rentAmountInWords}")) {
                replacement = "";
            } else if (trimmed.startsWith("5.1 Đặt cọc:")) {
                replacement = "5.1 Đặt cọc: Bên B đặt cọc cho bên A một khoản tiền là: ${deposit} VND (Bằng chữ: ${depositInWords})";
            } else if (isOnlyDots(text) && containsRecent(history, "${depositInWords}")) {
                replacement = "";
            } else if (trimmed.startsWith("Ngay khi ký kết hợp đồng, Bên B sẽ thanh toán thêm cho Bên A số tiền đặt cọc:")) {
                replacement = "Ngay khi ký kết hợp đồng, Bên B sẽ thanh toán thêm cho Bên A số tiền đặt cọc: ${deposit} VND";
            } else if (isOnlyDots(text) && containsRecent(history, "Ngay khi ký kết hợp đồng, Bên B")) {
                replacement = "";
            } else if (text.contains("Nội thất có thêm:")) {
                replacement = text.replace("Nội thất có thêm:", "Nội thất có thêm: ${equipmentSnapshot}");
            } else if (isOnlyDots(text) && containsRecent(history, "${equipmentSnapshot}")) {
                replacement = "";
            }

            history.add(text);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(1) + replacement + matcher.group(3)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static boolean inTenantBSection(List<String> history) {
        boolean seenB = false;
        for (String h : history) {
            if (h.contains("Bên B")) {
                seenB = true;
            }
            if (h.contains("Người ở cùng")) {
                return false;
            }
        }
        return seenB;
    }

    private static boolean isOnlyDots(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return text.replaceAll("[.…\\s\u00a0]", "").isEmpty();
    }

    private static boolean containsRecent(List<String> history, String needle) {
        for (int i = history.size() - 1; i >= 0 && i >= history.size() - 15; i--) {
            if (history.get(i).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /** Kiểm tra DOCX còn placeholder {@code ${...}} chưa được thay. */
    public static boolean containsUnresolvedPlaceholders(byte[] docx) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (DOCUMENT_XML.equals(entry.getName())) {
                    String xml = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    return xml.contains("${");
                }
                zis.closeEntry();
            }
        }
        return false;
    }
}
