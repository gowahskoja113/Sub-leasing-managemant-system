package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class MaintenanceQuoteRequest {

    @NotNull(message = "Bắt buộc phải có chi phí sửa chữa báo giá")
    @DecimalMin(value = "0", inclusive = true, message = "Chi phí sửa chữa không được âm")
    private BigDecimal quotedRepairCost;
}
