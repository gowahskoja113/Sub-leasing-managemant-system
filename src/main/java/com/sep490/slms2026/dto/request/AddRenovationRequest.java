package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddRenovationRequest {

    private Long roomId;

    @NotBlank(message = "Mô tả công việc cải tạo không được để trống")
    private String description;

    @DecimalMin(value = "0.0", inclusive = false, message = "Chi phí cải tạo phải lớn hơn 0")
    private BigDecimal cost;

    private Boolean completed;
}
