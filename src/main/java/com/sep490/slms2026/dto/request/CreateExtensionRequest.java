package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateExtensionRequest {
    @NotNull(message = "Số tháng gia hạn không được để trống")
    @Min(value = 1, message = "Số tháng gia hạn phải ít nhất là 1")
    private Integer months;

    private String note;
}
