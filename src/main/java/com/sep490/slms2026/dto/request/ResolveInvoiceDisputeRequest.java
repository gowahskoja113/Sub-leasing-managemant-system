package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResolveInvoiceDisputeRequest {

    @NotBlank(message = "Kết luận không được để trống")
    @Pattern(regexp = "^(ACCEPTED|REJECTED)$", message = "Kết luận phải là ACCEPTED hoặc REJECTED")
    private String outcome;

    @NotBlank(message = "Ghi chú kết luận không được để trống")
    @Size(min = 10, max = 1000, message = "Ghi chú kết luận phải từ 10 đến 1000 ký tự")
    private String note;
}
