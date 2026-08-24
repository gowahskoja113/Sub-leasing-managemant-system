package com.sep490.slms2026.dto.request;

import com.sep490.slms2026.enums.InvoiceDisputeReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateInvoiceDisputeRequest {

    @NotNull(message = "Lý do khiếu nại không được để trống")
    private InvoiceDisputeReason reason;

    @NotBlank(message = "Mô tả không được để trống")
    @Size(min = 10, max = 500, message = "Mô tả phải từ 10 đến 500 ký tự")
    private String note;

    @Size(max = 3, message = "Tối đa 3 ảnh")
    private List<String> photos;
}
