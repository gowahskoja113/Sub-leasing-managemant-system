package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.response.BulkImportResponse;
import org.springframework.web.multipart.MultipartFile;

public interface BulkRenovationSupplementImportService {

    BulkImportResponse importSupplementWorkbook(MultipartFile file, boolean dryRun);
}
