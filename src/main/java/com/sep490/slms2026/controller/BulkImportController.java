package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.response.BulkImportResponse;
import com.sep490.slms2026.service.BulkOnboardingImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/import")
@RequiredArgsConstructor
public class BulkImportController {

    private final BulkOnboardingImportService bulkOnboardingImportService;

    /**
     * Import hàng loạt onboarding từ file Excel (3 sheet data).
     * dryRun=true: chỉ parse + validate, không ghi DB.
     */
    @PostMapping(value = "/onboarding-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BulkImportResponse> importOnboardingExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun) {
        return ResponseEntity.ok(bulkOnboardingImportService.importWorkbook(file, dryRun));
    }
}
