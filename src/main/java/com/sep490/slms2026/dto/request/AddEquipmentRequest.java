package com.sep490.slms2026.dto.request;

import com.sep490.slms2026.enums.EquipmentSource;
import com.sep490.slms2026.enums.EquipmentStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddEquipmentRequest {

    private Long roomId;

    @NotBlank(message = "Tên thiết bị không được để trống")
    private String name;

    @NotNull(message = "Nguồn thiết bị không được để trống")
    private EquipmentSource source;

    // Optional khi tạo nháp — bắt buộc khi source = PURCHASED và tính khấu hao
    @DecimalMin(value = "0.0", inclusive = false, message = "Giá mua phải lớn hơn 0")
    private BigDecimal purchasePrice;

    private EquipmentStatus status;

    private String note;
}
