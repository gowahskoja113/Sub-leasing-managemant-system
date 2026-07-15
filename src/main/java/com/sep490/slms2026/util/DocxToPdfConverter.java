package com.sep490.slms2026.util;

import com.lowagie.text.Font;
import com.lowagie.text.pdf.BaseFont;
import fr.opensagres.poi.xwpf.converter.pdf.PdfConverter;
import fr.opensagres.poi.xwpf.converter.pdf.PdfOptions;
import fr.opensagres.xdocreport.itext.extension.font.IFontProvider;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Chuyển DOCX (đã fill placeholder) sang PDF, luôn embed Times New Roman (Unicode tiếng Việt).
 */
public final class DocxToPdfConverter {

    private DocxToPdfConverter() {
    }

    public static byte[] convert(byte[] docxBytes) throws IOException {
        try (InputStream in = new ByteArrayInputStream(docxBytes);
             XWPFDocument document = new XWPFDocument(in);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfOptions options = PdfOptions.create();
            options.fontProvider(new TimesNewRomanFontProvider());
            PdfConverter.getInstance().convert(document, out, options);
            return out.toByteArray();
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException("DOCX→PDF thất bại: " + ex.getMessage(), ex);
        }
    }

    /**
     * Mọi font trong DOCX → Times New Roman TTF hệ thống (IDENTITY_H / embed).
     * Không dùng Arial để tránh PDF "lẫn" font.
     */
    static final class TimesNewRomanFontProvider implements IFontProvider {

        private static final String[] CANDIDATES = {
                "C:/Windows/Fonts/times.ttf",
                "C:/Windows/Fonts/Times.ttf",
                "C:/Windows/Fonts/timesnr.ttf",
                "C:/Windows/Fonts/Times New Roman.ttf",
                "/Library/Fonts/Times New Roman.ttf",
                "/System/Library/Fonts/Supplemental/Times New Roman.ttf",
                // Metric-compatible fallback (Linux / Docker)
                "/usr/share/fonts/truetype/liberation/LiberationSerif-Regular.ttf",
                "/usr/share/fonts/truetype/msttcorefonts/Times_New_Roman.ttf",
                "/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf"
        };

        private static final String[] BOLD_CANDIDATES = {
                "C:/Windows/Fonts/timesbd.ttf",
                "C:/Windows/Fonts/Timesbd.ttf",
                "C:/Windows/Fonts/Times New Roman Bold.ttf",
                "/Library/Fonts/Times New Roman Bold.ttf",
                "/System/Library/Fonts/Supplemental/Times New Roman Bold.ttf",
                "/usr/share/fonts/truetype/liberation/LiberationSerif-Bold.ttf",
                "/usr/share/fonts/truetype/msttcorefonts/Times_New_Roman_Bold.ttf",
                "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf"
        };

        private static final String[] ITALIC_CANDIDATES = {
                "C:/Windows/Fonts/timesi.ttf",
                "C:/Windows/Fonts/Timesi.ttf",
                "C:/Windows/Fonts/Times New Roman Italic.ttf",
                "/Library/Fonts/Times New Roman Italic.ttf",
                "/System/Library/Fonts/Supplemental/Times New Roman Italic.ttf",
                "/usr/share/fonts/truetype/liberation/LiberationSerif-Italic.ttf",
                "/usr/share/fonts/truetype/msttcorefonts/Times_New_Roman_Italic.ttf",
                "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Italic.ttf"
        };

        private static final String[] BOLD_ITALIC_CANDIDATES = {
                "C:/Windows/Fonts/timesbi.ttf",
                "C:/Windows/Fonts/Timesbi.ttf",
                "C:/Windows/Fonts/Times New Roman Bold Italic.ttf",
                "/Library/Fonts/Times New Roman Bold Italic.ttf",
                "/System/Library/Fonts/Supplemental/Times New Roman Bold Italic.ttf",
                "/usr/share/fonts/truetype/liberation/LiberationSerif-BoldItalic.ttf",
                "/usr/share/fonts/truetype/msttcorefonts/Times_New_Roman_Bold_Italic.ttf",
                "/usr/share/fonts/truetype/dejavu/DejaVuSerif-BoldItalic.ttf"
        };

        private volatile String regularPath;
        private volatile String boldPath;
        private volatile String italicPath;
        private volatile String boldItalicPath;

        @Override
        public Font getFont(String familyName, String encoding, float size, int style, Color color) {
            try {
                boolean bold = (style & Font.BOLD) != 0;
                boolean italic = (style & Font.ITALIC) != 0;
                String path = resolveFontPath(bold, italic);
                BaseFont bf = BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                return new Font(bf, size <= 0 ? 12f : size, style, color);
            } catch (Exception ex) {
                throw new IllegalStateException(
                        "Không tải được Times New Roman cho PDF: " + ex.getMessage(),
                        ex);
            }
        }

        private String resolveFontPath(boolean bold, boolean italic) {
            if (bold && italic) {
                if (boldItalicPath == null) {
                    boldItalicPath = firstExisting(BOLD_ITALIC_CANDIDATES, true);
                }
                return boldItalicPath;
            }
            if (bold) {
                if (boldPath == null) {
                    boldPath = firstExisting(BOLD_CANDIDATES, true);
                }
                return boldPath;
            }
            if (italic) {
                if (italicPath == null) {
                    italicPath = firstExisting(ITALIC_CANDIDATES, true);
                }
                return italicPath;
            }
            if (regularPath == null) {
                regularPath = firstExisting(CANDIDATES, false);
            }
            return regularPath;
        }

        private static String firstExisting(String[] candidates, boolean fallbackToRegular) {
            for (String candidate : candidates) {
                if (candidate != null && Files.isRegularFile(Path.of(candidate))) {
                    return candidate;
                }
            }
            if (fallbackToRegular) {
                for (String candidate : CANDIDATES) {
                    if (candidate != null && Files.isRegularFile(Path.of(candidate))) {
                        return candidate;
                    }
                }
            }
            throw new IllegalStateException(
                    "Không tìm thấy Times New Roman / Liberation Serif trên máy. "
                            + "OS=" + System.getProperty("os.name", "").toLowerCase(Locale.ROOT));
        }
    }
}
