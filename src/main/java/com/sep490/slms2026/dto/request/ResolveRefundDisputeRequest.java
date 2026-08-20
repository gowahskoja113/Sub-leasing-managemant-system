package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResolveRefundDisputeRequest {
    @NotBlank(message = "Kết quả không được để trống")
    @Pattern(regexp = "^(RETRANSFERRED|REJECTED)$", message = "Kết quả phải là RETRANSFERRED hoặc REJECTED")
    private String outcome;

    @NotBlank(message = "Ghi chú không được để trống")
    private String note;
}
