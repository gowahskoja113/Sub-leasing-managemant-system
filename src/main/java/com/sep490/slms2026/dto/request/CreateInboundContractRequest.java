package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateInboundContractRequest {

    @NotBlank(message = "Mã hợp đồng không được để trống")
    private String contractCode;

    @NotBlank(message = "Tên chủ nhà không được để trống")
    private String ownerName;

    @NotNull(message = "Giá thuê gốc không được để trống")
    @DecimalMin(value = "0.0", inclusive = false, message = "Giá thuê gốc phải lớn hơn 0")
    private BigDecimal baseRentPrice;

    @NotNull(message = "Tiền cọc không được để trống")
    @DecimalMin(value = "0.0", inclusive = false, message = "Tiền cọc phải lớn hơn 0")
    private BigDecimal depositAmount;

    @NotNull(message = "Ngày bắt đầu không được để trống")
    private LocalDate startDate;

    @NotNull(message = "Ngày kết thúc không được để trống")
    private LocalDate endDate;

    private String contractScanUrl;
}
