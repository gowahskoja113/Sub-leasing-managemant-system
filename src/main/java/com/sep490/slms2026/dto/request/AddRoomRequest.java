package com.sep490.slms2026.dto.request;

import com.sep490.slms2026.enums.PropertyType;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddRoomRequest {

    @NotBlank(message = "Số phòng không được để trống")
    private String roomNumber;

    // Optional khi tạo nháp — sẽ set khi confirm giá sau khấu hao
    @DecimalMin(value = "0.0", inclusive = false, message = "Giá thuê phải lớn hơn 0")
    private BigDecimal price;

    @DecimalMin(value = "0.0", message = "Tiền cọc không được âm")
    private BigDecimal deposit;

    @NotNull(message = "Diện tích không được để trống")
    @DecimalMin(value = "0.0", inclusive = false, message = "Diện tích phải lớn hơn 0")
    private Double area;

    @Min(value = 1, message = "Số người ở tối đa phải ít nhất là 1")
    private Integer maxOccupants;

    // Dùng cho wholehouse: mô tả cấu trúc bên trong
    private String structureDescription;

    private String imageUrls;

    @NotNull(message = "Loại phòng không được để trống")
    private PropertyType propertyType;

    // Optional — null nếu tòa dùng đồng hồ chung
    private String electricMeterCode;
    private String waterMeterCode;
}