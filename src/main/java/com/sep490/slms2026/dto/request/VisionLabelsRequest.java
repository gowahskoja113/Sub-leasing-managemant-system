package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VisionLabelsRequest {

    @NotBlank(message = "Thiếu URL ảnh")
    private String imageUrl;
}
