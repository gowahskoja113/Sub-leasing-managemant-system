package com.sep490.slms2026.dto.request;

import com.sep490.slms2026.enums.EquipmentStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Thiết bị lắp thêm khi tạo/sửa HĐ — chưa có trong inventory lúc submit. */
@Getter
@Setter
public class ContractAddedEquipmentRequest {

    @NotBlank(message = "Tên thiết bị không được để trống")
    private String name;

  /** Danh mục / loại (vd. Điện lạnh). */
    private String category;

    @DecimalMin(value = "0", inclusive = true, message = "Chi phí không hợp lệ")
    private BigDecimal cost;

    /** Phòng gắn thiết bị; null = khu vực chung hoặc nguyên căn. */
    private Long roomId;

    private EquipmentStatus condition;
}
