package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForceSettleRequest {
    @NotNull(message = "Phải xác nhận cấn trừ cọc")
    private Boolean deductFromDeposit;

    @NotBlank(message = "Lý do không được để trống")
    @Size(min = 20, message = "Lý do phải từ 20 ký tự trở lên")
    private String reason;
}
