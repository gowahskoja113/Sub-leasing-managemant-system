package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RejectContractRequest {

    @NotBlank(message = "Lý do từ chối không được để trống")
    private String reason;
}
