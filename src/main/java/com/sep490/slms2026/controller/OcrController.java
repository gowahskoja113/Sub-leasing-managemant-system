package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.OcrMeterRequest;
import com.sep490.slms2026.dto.response.OcrEvnBillResponse;
import com.sep490.slms2026.dto.response.OcrMeterResponse;
import com.sep490.slms2026.service.OcrService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ocr")
@RequiredArgsConstructor
public class OcrController {

    private final OcrService ocrService;

    /**
     * POST /api/v1/ocr/meter
     * Nhận URL ảnh đồng hồ (đã upload Cloudinary) -> trả về chỉ số gợi ý.
     */
    @PostMapping("/meter")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<OcrMeterResponse> readMeter(@Valid @RequestBody OcrMeterRequest request) {
        return ResponseEntity.ok(ocrService.readMeter(request.getImageUrl()));
    }

    /**
     * POST /api/v1/ocr/evn-bill
     * Nhận URL ảnh hóa đơn EVN -> trả tổng kWh, tổng tiền, kỳ thanh toán.
     */
    @PostMapping("/evn-bill")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<OcrEvnBillResponse> readEvnBill(@Valid @RequestBody OcrMeterRequest request) {
        return ResponseEntity.ok(ocrService.readEvnBill(request.getImageUrl()));
    }
}
