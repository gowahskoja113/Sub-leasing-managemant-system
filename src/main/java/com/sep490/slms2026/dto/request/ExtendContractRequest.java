package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ExtendContractRequest {
    @NotNull(message = "Thiếu ngày kết thúc mới")
    private LocalDate newEndDate;

    private BigDecimal newRentAmount;
}
