package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RejectExtensionRequest {
    @NotBlank(message = "Lý do từ chối không được để trống")
    private String reason;
}
