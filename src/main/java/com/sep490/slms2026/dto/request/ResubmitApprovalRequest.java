package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ResubmitApprovalRequest {

    @NotNull(message = "Giá thuê không được để trống")
    @DecimalMin(value = "0.0", inclusive = false, message = "Giá thuê phải lớn hơn 0")
    private BigDecimal rentAmount;

    @NotNull(message = "Tiền cọc không được để trống")
    @DecimalMin(value = "0.0", inclusive = true, message = "Tiền cọc không hợp lệ")
    private BigDecimal deposit;
}
