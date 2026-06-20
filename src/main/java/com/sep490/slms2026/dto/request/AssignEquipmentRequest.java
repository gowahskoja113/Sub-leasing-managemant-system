package com.sep490.slms2026.dto.request;

import com.sep490.slms2026.enums.EquipmentSource;
import com.sep490.slms2026.enums.EquipmentStatus;
import com.sep490.slms2026.enums.HouseArea;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class AssignEquipmentRequest {

    @NotNull
    private Long catalogId;

    @NotNull
    @Min(1)
    private Integer quantity;

    @NotNull
    private EquipmentStatus status;

    @NotNull
    private EquipmentSource source;

    private Long roomId;

    private HouseArea houseArea;

    /**
     * Đơn giá mỗi thiết bị. Bắt buộc khi source = PURCHASED (thiết bị mua mới).
     * Bỏ qua / = 0 khi source = INITIAL_HANDOVER.
     */
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal price;

    private String note;
}
