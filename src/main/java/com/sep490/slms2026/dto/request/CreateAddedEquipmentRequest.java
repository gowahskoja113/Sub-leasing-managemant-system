package com.sep490.slms2026.dto.request;

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

    private Long roomId;
}
