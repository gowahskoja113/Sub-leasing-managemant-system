package com.sep490.slms2026.dto.response;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OcrMeterResponse {

    /** Số gợi ý tốt nhất (chuỗi dài/nổi bật nhất) để điền sẵn vào ô chỉ số */
    private String reading;

    /** Tất cả các cụm số OCR nhận được, cho phép người dùng chọn lại nếu cần */
    private List<String> numbers;

    /** Toàn bộ text OCR đọc được (debug) */
    private String rawText;
}
