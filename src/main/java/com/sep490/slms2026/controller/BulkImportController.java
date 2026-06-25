package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.response.BulkImportImagesResponse;
import com.sep490.slms2026.dto.response.BulkImportResponse;
import com.sep490.slms2026.dto.response.PropertyPurgeResponse;
import com.sep490.slms2026.service.BulkLeaseImportService;
import com.sep490.slms2026.service.BulkOnboardingImportService;
import com.sep490.slms2026.service.BulkPropertyImageImportService;
import com.sep490.slms2026.service.BulkRenovationImportService;
import com.sep490.slms2026.service.BulkRenovationSupplementImportService;
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
    private final BulkLeaseImportService bulkLeaseImportService;
    private final BulkRenovationImportService bulkRenovationImportService;
    private final BulkRenovationSupplementImportService bulkRenovationSupplementImportService;
    private final BulkPropertyImageImportService bulkPropertyImageImportService;

    /**
     * Đợt 1 — Khởi tạo nhà từ file Excel (HĐ thuê + TB bàn giao). Luôn nguyên căn.
     * dryRun=true: chỉ parse + validate, không ghi DB.
     */
    @PostMapping(value = "/lease-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BulkImportResponse> importLeaseExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun) {
        return ResponseEntity.ok(bulkLeaseImportService.importLeaseWorkbook(file, dryRun));
    }

    /**
     * Đợt 2 — Cấu hình khai thác + cải tạo + TB mua mới; tự động completeRenovation → định giá → gửi Host.
     * dryRun=true: chỉ validate, không ghi DB, không gửi Host.
     */
    @PostMapping(value = "/renovation-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BulkImportResponse> importRenovationExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun) {
        return ResponseEntity.ok(bulkRenovationImportService.importRenovationWorkbook(file, dryRun));
    }

    /**
     * Cải tạo bổ sung (lần 2, 3…) — sau khi gọi {@code POST .../start-renovation} khi nhà ACTIVE.
     * File 2 sheet: cải tạo + TB mua mới. Chỉ {@code completeRenovation}, không gửi Host.
     */
    @PostMapping(value = "/renovation-supplement-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BulkImportResponse> importRenovationSupplementExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun) {
        return ResponseEntity.ok(bulkRenovationSupplementImportService.importSupplementWorkbook(file, dryRun));
    }

    /**
     * Import hàng loạt onboarding từ file Excel (3 sheet data) — luồng cũ, giữ tương thích.
     * dryRun=true: chỉ parse + validate, không ghi DB.
     * Hợp đồng có mã đã tồn tại trong DB được bỏ qua (SKIPPED); các mã mới vẫn import bình thường.
     */
    @PostMapping(value = "/onboarding-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BulkImportResponse> importOnboardingExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun) {
        return ResponseEntity.ok(bulkOnboardingImportService.importWorkbook(file, dryRun));
    }

    /**
     * Bước 2 — import ảnh từ file zip sau khi đã import Excel.
     * Cấu trúc zip: {@code {contractCode}/ảnh.jpg} hoặc {@code folder-tổng/{contractCode}/ảnh.jpg}.
     * Tên folder con = mã hợp đồng (sheet Excel). dryRun=true: chỉ kiểm tra, không ghi DB.
     */
    @PostMapping(value = "/property-images-zip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BulkImportImagesResponse> importPropertyImagesZip(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun) {
        return ResponseEntity.ok(bulkPropertyImageImportService.importFromZip(file, dryRun));
    }

    /**
     * Xóa cứng căn nhà theo mã hợp đồng inbound (mã cột trên sheet Excel).
     * Tiện khi cần rollback một dòng import sai mà không cần tra propertyId.
     */
    @DeleteMapping("/onboarding-excel/contracts/{contractCode}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PropertyPurgeResponse> purgeImportedContract(
            @PathVariable String contractCode) {
        return ResponseEntity.ok(bulkOnboardingImportService.purgeByContractCode(contractCode));
    }
}
