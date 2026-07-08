package com.sep490.slms2026.dto.request;

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
public class CreateRentInvoiceRequest {

    @NotNull(message = "contractId không được để trống")
    private Long contractId;

    @NotBlank(message = "billingMonth không được để trống")
    private String billingMonth;

    @NotNull(message = "amount không được để trống")
    private BigDecimal amount;

    @NotNull(message = "dueDate không được để trống")
    private LocalDate dueDate;

    private String note;
}
