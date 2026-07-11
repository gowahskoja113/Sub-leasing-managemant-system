package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConfirmDepositCashRequest {

    @NotBlank(message = "Số điện thoại không được để trống")
    private String phoneNumber;
}
