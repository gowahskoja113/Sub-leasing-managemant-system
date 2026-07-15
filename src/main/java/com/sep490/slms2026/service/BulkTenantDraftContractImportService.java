package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.response.BulkImportResponse;
import org.springframework.web.multipart.MultipartFile;

public interface BulkTenantDraftContractImportService {

    /**
     * Import hàng loạt hợp đồng thuê nháp (DRAFT) từ Excel.
     * BĐS phải đã tồn tại (map theo Mã HĐ inbound / Mã BĐS / Tên tòa nhà).
     */
    BulkImportResponse importWorkbook(MultipartFile file, boolean dryRun);
}
