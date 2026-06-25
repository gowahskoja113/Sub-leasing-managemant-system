package com.sep490.slms2026.dto.host;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class HostRejectContractRequest {
    @NotBlank
    private String reason;
}
