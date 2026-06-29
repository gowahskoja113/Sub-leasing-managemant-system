package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.response.OcrEvnBillResponse;
import com.sep490.slms2026.dto.response.OcrMeterResponse;

public interface OcrService {
    /** Đọc chỉ số đồng hồ điện/nước từ ảnh (Cloudinary URL) bằng OCR.space. */
    OcrMeterResponse readMeter(String imageUrl);

    /** Đọc hóa đơn EVN tổng từ ảnh — trả tổng kWh, tổng tiền, kỳ thanh toán. */
    OcrEvnBillResponse readEvnBill(String imageUrl);
}
