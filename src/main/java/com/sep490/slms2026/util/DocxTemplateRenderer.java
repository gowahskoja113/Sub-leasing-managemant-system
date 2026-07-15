package com.sep490.slms2026.util;

import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/** Thay placeholder {@code ${key}} trong file DOCX (paragraph + table). */
public final class DocxTemplateRenderer {

    static final String TIMES_NEW_ROMAN = "Times New Roman";

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
            // Ép toàn bộ document sang Times New Roman (ascii + eastAsia + cs)
            // để PDF convert không lệch font chỗ có/không có placeholder.
            normalizeAllFonts(doc);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }

    private static void normalizeAllFonts(XWPFDocument doc) {
        for (XWPFParagraph paragraph : doc.getParagraphs()) {
            for (XWPFRun run : paragraph.getRuns()) {
                forceTimesNewRoman(run);
            }
        }
        for (XWPFTable table : doc.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph paragraph : cell.getParagraphs()) {
                        for (XWPFRun run : paragraph.getRuns()) {
                            forceTimesNewRoman(run);
                        }
                    }
                }
            }
        }
    }

    static void forceTimesNewRoman(XWPFRun run) {
        if (run == null) {
            return;
        }
        run.setFontFamily(TIMES_NEW_ROMAN);
        CTRPr rPr = run.getCTR().isSetRPr() ? run.getCTR().getRPr() : run.getCTR().addNewRPr();
        CTFonts fonts = rPr.isSetRFonts() ? rPr.getRFonts() : rPr.addNewRFonts();
        fonts.setAscii(TIMES_NEW_ROMAN);
        fonts.setHAnsi(TIMES_NEW_ROMAN);
        fonts.setCs(TIMES_NEW_ROMAN);
        fonts.setEastAsia(TIMES_NEW_ROMAN);
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
        forceTimesNewRoman(run);
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
            int fontSize,
            boolean bold,
            boolean italic,
            UnderlinePatterns underline,
            String color
    ) {
        static RunStyle defaults() {
            return new RunStyle(12, false, false, UnderlinePatterns.NONE, "000000");
        }

        static RunStyle from(XWPFRun run) {
            int size = run.getFontSize();
            if (size <= 0) {
                size = 12;
            }
            UnderlinePatterns underline = run.getUnderline();
            if (underline == null) {
                underline = UnderlinePatterns.NONE;
            }
            return new RunStyle(
                    size,
                    run.isBold(),
                    run.isItalic(),
                    underline,
                    run.getColor()
            );
        }
    }
}
