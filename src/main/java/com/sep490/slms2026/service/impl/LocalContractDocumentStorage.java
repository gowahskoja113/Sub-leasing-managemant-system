package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.config.ContractDocumentUploadProperties;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.service.ContractDocumentStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class LocalContractDocumentStorage implements ContractDocumentStorage {

    private static final Pattern UNSAFE = Pattern.compile("[^a-zA-Z0-9._-]+");

    private final ContractDocumentUploadProperties properties;

    @Override
    public String store(String contractCode, String filename, byte[] content) {
        try {
            Path baseDir = Path.of(properties.getDir()).toAbsolutePath().normalize();
            String safeContract = sanitizeSegment(contractCode);
            String safeName = sanitizeFilename(filename);

            Path contractDir = baseDir.resolve(safeContract);
            Files.createDirectories(contractDir);
            Files.write(contractDir.resolve(safeName), content);

            String baseUrl = properties.getPublicBaseUrl().replaceAll("/+$", "");
            return baseUrl + "/uploads/contracts/" + safeContract + "/" + safeName;
        } catch (IOException ex) {
            throw new BusinessException("Lưu file hợp đồng thất bại: " + ex.getMessage());
        }
    }

    private String sanitizeSegment(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException("Mã hợp đồng không hợp lệ khi lưu file");
        }
        return UNSAFE.matcher(trimmed).replaceAll("_");
    }

    private String sanitizeFilename(String filename) {
        String name = filename == null ? "hop-dong.docx" : filename.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.isBlank()) {
            name = "hop-dong.docx";
        }
        if (!name.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            name = name + ".docx";
        }
        return UNSAFE.matcher(name.toLowerCase(Locale.ROOT)).replaceAll("_");
    }
}
