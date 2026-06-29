package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateMeterReadingRequest {

    @NotBlank(message = "Loại tiện ích không được để trống")
    private String type;

    @NotBlank(message = "Kỳ ghi chỉ số không được để trống")
    private String period;

    @NotNull(message = "Chỉ số không được để trống")
    private BigDecimal reading;

    private String imageUrl;
}
