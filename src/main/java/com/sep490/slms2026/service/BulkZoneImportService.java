package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.response.BulkImportResponse;
import org.springframework.web.multipart.MultipartFile;

public interface BulkZoneImportService {

    /**
     * Import Tỉnh/Thành + Quận/Huyện từ Excel (idempotent — trùng tên thì bỏ qua).
     * Cột: {@code Tỉnh/Thành phố}, {@code Quận/Huyện?} , {@code Mô tả?}.
     */
    BulkImportResponse importZonesWorkbook(MultipartFile file, boolean dryRun);
}
