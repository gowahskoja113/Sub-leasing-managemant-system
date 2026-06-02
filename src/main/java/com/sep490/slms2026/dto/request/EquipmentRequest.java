package com.sep490.slms2026.dto.request;

import com.sep490.slms2026.enums.EquipmentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class EquipmentRequest {

    @NotBlank(message = "Tên thiết bị không được để trống")
    private String name;

    @NotBlank(message = "Danh mục thiết bị không được để trống")
    private String category;

    @NotNull(message = "Trạng thái thiết bị không được để trống")
    private EquipmentStatus status;

    private LocalDate installedDate;

    private BigDecimal purchaseCost;

    private String description;

    /**
     * Nếu property là whole-house (isWholeHouse = true) thì chỉ cần propertyId.
     * Nếu property có rooms thì cần roomId để gán vào phòng cụ thể.
     * Chỉ được cung cấp một trong hai.
     */
    private UUID roomId;

    private UUID propertyId;
}