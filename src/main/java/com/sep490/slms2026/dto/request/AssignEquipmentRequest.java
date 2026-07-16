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

    private Integer warrantyMonths;

    private java.time.LocalDate warrantyStartDate;

    private java.time.LocalDate warrantyEndDate;

    /**
     * Mức phạt cố định (VNĐ) khi thiết bị hết bảo hành.
     * Dùng cho luồng maintain — không tính từ đơn giá.
     */
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal penaltyFee;

    /**
     * THEM_MOI (mặc định) | THAY_THE — khi import Excel cải tạo.
     */
    private com.sep490.slms2026.enums.EquipmentImportAction importAction;
}
