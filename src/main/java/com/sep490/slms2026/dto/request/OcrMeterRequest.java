package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OcrMeterRequest {

    @NotBlank(message = "Thiếu URL ảnh đồng hồ")
    private String imageUrl;
}
