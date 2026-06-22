package com.sep490.slms2026.imports;

import com.sep490.slms2026.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class PropertyImageZipParser {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> IGNORED_NAMES = Set.of(".ds_store", "thumbs.db", "desktop.ini");

    public List<ParsedZipImage> parse(MultipartFile zipFile) {
        String originalName = zipFile.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new BusinessException("File phải là định dạng .zip");
        }

        List<ParsedZipImage> images = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        try (InputStream input = zipFile.getInputStream();
             ZipInputStream zis = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String entryPath = normalizePath(entry.getName());
                if (entryPath.contains("..")) {
                    skipped.add(entryPath + " (path không hợp lệ)");
                    continue;
                }
                if (shouldIgnoreEntry(entryPath)) {
                    continue;
                }
                if (!isImageFile(entryPath)) {
                    skipped.add(entryPath + " (không phải ảnh)");
                    continue;
                }

                String contractCode = extractContractCode(entryPath);
                if (contractCode == null) {
                    skipped.add(entryPath + " (cấu trúc folder không hợp lệ — cần {contractCode}/ảnh hoặc {folder}/{contractCode}/ảnh)");
                    continue;
                }

                byte[] content = zis.readAllBytes();
                if (content.length == 0) {
                    skipped.add(entryPath + " (file rỗng)");
                    continue;
                }

                String fileName = entryPath.substring(entryPath.lastIndexOf('/') + 1);
                images.add(new ParsedZipImage(contractCode.trim(), fileName, content));
            }
        } catch (IOException ex) {
            throw new BusinessException("Không đọc được file zip: " + ex.getMessage());
        }

        return images;
    }

    private String normalizePath(String path) {
        return path.replace('\\', '/').replaceAll("^/+", "");
    }

    private boolean shouldIgnoreEntry(String entryPath) {
        String lower = entryPath.toLowerCase(Locale.ROOT);
        if (lower.startsWith("__macosx/") || lower.contains("/__macosx/")) {
            return true;
        }
        String fileName = lower.substring(lower.lastIndexOf('/') + 1);
        return IGNORED_NAMES.contains(fileName) || fileName.startsWith("._");
    }

    private boolean isImageFile(String entryPath) {
        int dot = entryPath.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        String ext = entryPath.substring(dot + 1).toLowerCase(Locale.ROOT);
        return IMAGE_EXTENSIONS.contains(ext);
    }

    /**
     * Chấp nhận:
     * - HD001/mat-tien.jpg
     * - import-media/HD001/mat-tien.jpg
     * Bỏ qua lồng sâu hơn 1 cấp contractCode.
     */
    private String extractContractCode(String entryPath) {
        String[] parts = entryPath.split("/");
        if (parts.length == 2) {
            return parts[0];
        }
        if (parts.length == 3) {
            return parts[1];
        }
        return null;
    }
}
