package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MaintenanceQuoteResponseRequest {

    @NotNull(message = "Bắt buộc phải có trạng thái duyệt báo giá")
    private Boolean approved;

    private String note;
}
