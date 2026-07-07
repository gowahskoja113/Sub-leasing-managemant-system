package com.sep490.slms2026.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CreateViewingLeadRequest {

    @NotBlank(message = "Tên khách hàng không được để trống")
    private String customerName;

    @NotBlank(message = "Số điện thoại không được để trống")
    private String customerPhone;

    private String note;

    private LocalDateTime preferredViewingAt;

    @NotEmpty(message = "Phải chọn ít nhất một căn nhà quan tâm")
    @Valid
    private List<ViewingLeadPropertyItemRequest> properties;
}
