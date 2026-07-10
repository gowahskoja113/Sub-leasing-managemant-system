package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateAddedEquipmentRequest {

    @NotBlank(message = "Tên thiết bị không được để trống")
    private String equipmentName;

    @NotBlank(message = "Danh mục không được để trống")
    private String category;

    @DecimalMin(value = "0", inclusive = true, message = "Chi phí không hợp lệ")
    private java.math.BigDecimal cost;

    private Long roomId;
}
