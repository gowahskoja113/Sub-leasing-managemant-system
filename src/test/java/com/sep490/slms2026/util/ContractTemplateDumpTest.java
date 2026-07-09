package com.sep490.slms2026.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

class ContractTemplateDumpTest {

    @Test
    void dumpBuiltTemplateTextNodes() throws Exception {
        Path built = Path.of("src/main/resources/templates/contract/tenant-apartment-draft-template.docx");
        List<String> nodes = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(built))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    String xml = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    Matcher m = Pattern.compile("<w:t[^>]*>([^<]*)</w:t>").matcher(xml);
                    while (m.find()) {
                        String t = m.group(1);
                        if (!t.isBlank()) {
                            nodes.add(t);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
        for (int i = 0; i < nodes.size(); i++) {
            System.out.printf("%04d: %s%n", i, nodes.get(i));
        }
    }
}
