package com.sep490.slms2026.util;

import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/** Thay placeholder {@code ${key}} trong file DOCX (paragraph + table). */
public final class DocxTemplateRenderer {

    private static final String DEFAULT_FONT = "Times New Roman";

    private DocxTemplateRenderer() {
    }

    public static byte[] render(InputStream templateStream, Map<String, String> variables) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(templateStream)) {
            for (XWPFParagraph paragraph : doc.getParagraphs()) {
                replaceInParagraph(paragraph, variables);
            }
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph paragraph : cell.getParagraphs()) {
                            replaceInParagraph(paragraph, variables);
                        }
                    }
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }

    private static void replaceInParagraph(XWPFParagraph paragraph, Map<String, String> variables) {
        String text = paragraph.getText();
        if (text == null || text.isBlank()) {
            return;
        }
        String replaced = text;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            replaced = replaced.replace("${" + entry.getKey() + "}", nullToEmpty(entry.getValue()));
        }
        if (replaced.equals(text)) {
            return;
        }

        RunStyle style = captureStyle(paragraph);
        List<XWPFRun> runs = paragraph.getRuns();
        for (int i = runs.size() - 1; i >= 0; i--) {
            paragraph.removeRun(i);
        }
        XWPFRun run = paragraph.createRun();
        applyStyle(run, style);
        writeMultiline(run, replaced);
    }

    private static RunStyle captureStyle(XWPFParagraph paragraph) {
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs == null || runs.isEmpty()) {
            return RunStyle.defaults();
        }
        for (XWPFRun run : runs) {
            if (run == null) {
                continue;
            }
            String sample = run.text();
            if (sample != null && !sample.isBlank()) {
                return RunStyle.from(run);
            }
        }
        return RunStyle.from(runs.get(0));
    }

    private static void applyStyle(XWPFRun run, RunStyle style) {
        run.setFontFamily(style.fontFamily);
        if (style.fontSize > 0) {
            run.setFontSize(style.fontSize);
        }
        run.setBold(style.bold);
        run.setItalic(style.italic);
        if (style.underline != null) {
            run.setUnderline(style.underline);
        }
        if (style.color != null && !style.color.isBlank()) {
            run.setColor(style.color);
        }
    }

    private static void writeMultiline(XWPFRun run, String text) {
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                run.addBreak();
            }
            run.setText(lines[i], i);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record RunStyle(
            String fontFamily,
            int fontSize,
            boolean bold,
            boolean italic,
            UnderlinePatterns underline,
            String color
    ) {
        static RunStyle defaults() {
            return new RunStyle(DEFAULT_FONT, 12, false, false, UnderlinePatterns.NONE, "000000");
        }

        static RunStyle from(XWPFRun run) {
            String family = run.getFontFamily();
            if (family == null || family.isBlank()) {
                family = DEFAULT_FONT;
            }
            int size = run.getFontSize();
            if (size <= 0) {
                size = 12;
            }
            UnderlinePatterns underline = run.getUnderline();
            if (underline == null) {
                underline = UnderlinePatterns.NONE;
            }
            return new RunStyle(
                    family,
                    size,
                    run.isBold(),
                    run.isItalic(),
                    underline,
                    run.getColor()
            );
        }
    }
}
