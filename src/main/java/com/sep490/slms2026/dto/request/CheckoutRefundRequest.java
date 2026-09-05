package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutRefundRequest {
    /** Số tiền thực chuyển. Nếu bỏ trống, dùng số hoàn còn lại trên quyết toán. */
    @Positive(message = "Số tiền hoàn cọc phải lớn hơn 0")
    private BigDecimal amount;

    @NotBlank(message = "Phương thức hoàn cọc không được để trống")
    private String method; // BANK_TRANSFER, CASH

    private String proofUrl;

    @NotNull(message = "Ngày chuyển hoàn cọc không được để trống")
    private LocalDate paidAt;

    private String note;
    
    private String refundBankName;
    private String refundBankAccount;
    private String refundAccountHolder;
}
