package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.response.OcrUtilityBillResponse;
import com.sep490.slms2026.dto.response.OcrMeterResponse;

public interface OcrService {
    /** Đọc chỉ số đồng hồ điện/nước từ ảnh (Cloudinary URL) bằng OCR.space. */
    OcrMeterResponse readMeter(String imageUrl);

    /** Đọc hóa đơn EVN/nước từ ảnh — truyền type WATER để bỏ mã khách hàng. */
    OcrUtilityBillResponse readUtilityBill(String imageUrl);

    /** @param billType ELECTRIC hoặc WATER (null = ELECTRIC). */
    OcrUtilityBillResponse readUtilityBill(String imageUrl, String billType);
}

