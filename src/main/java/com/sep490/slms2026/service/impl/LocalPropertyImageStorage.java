package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.config.PropertyImageUploadProperties;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.service.PropertyImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class LocalPropertyImageStorage implements PropertyImageStorage {

    private static final Pattern UNSAFE = Pattern.compile("[^a-zA-Z0-9._-]+");

    private final PropertyImageUploadProperties properties;

    @Override
    public String store(String contractCode, String originalFilename, byte[] content) {
        try {
            Path baseDir = Path.of(properties.getDir()).toAbsolutePath().normalize();
            String safeContract = sanitizeSegment(contractCode);
            String safeName = sanitizeFilename(originalFilename);
            String uniqueName = UUID.randomUUID().toString().substring(0, 8) + "-" + safeName;

            Path contractDir = baseDir.resolve(safeContract);
            Files.createDirectories(contractDir);
            Files.write(contractDir.resolve(uniqueName), content);

            String baseUrl = properties.getPublicBaseUrl().replaceAll("/+$", "");
            return baseUrl + "/uploads/properties/" + safeContract + "/" + uniqueName;
        } catch (IOException ex) {
            throw new BusinessException("Lưu ảnh thất bại: " + ex.getMessage());
        }
    }

    private String sanitizeSegment(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException("Mã hợp đồng không hợp lệ khi lưu ảnh");
        }
        return UNSAFE.matcher(trimmed).replaceAll("_");
    }

    private String sanitizeFilename(String filename) {
        String name = filename == null ? "image.jpg" : filename.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.isBlank()) {
            name = "image.jpg";
        }
        return UNSAFE.matcher(name.toLowerCase(Locale.ROOT)).replaceAll("_");
    }
}
