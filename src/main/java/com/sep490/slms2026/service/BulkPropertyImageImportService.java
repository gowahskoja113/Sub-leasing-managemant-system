package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.response.BulkImportImagesResponse;
import org.springframework.web.multipart.MultipartFile;

public interface BulkPropertyImageImportService {

    /**
     * Bước 2 sau import Excel: đọc zip ảnh theo folder contractCode và gán vào Property.imageUrls.
     * dryRun=true: chỉ kiểm tra zip + đối chiếu mã HĐ, không lưu file/DB.
     */
    BulkImportImagesResponse importFromZip(MultipartFile zipFile, boolean dryRun);
}
