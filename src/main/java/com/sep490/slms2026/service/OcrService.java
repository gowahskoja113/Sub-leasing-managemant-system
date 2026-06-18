package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.response.OcrMeterResponse;

public interface OcrService {
    /** Đọc chỉ số đồng hồ điện/nước từ ảnh (Cloudinary URL) bằng OCR.space. */
    OcrMeterResponse readMeter(String imageUrl);
}
