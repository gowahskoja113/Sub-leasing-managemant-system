package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.PropertyType;
import com.sep490.slms2026.enums.RoomStatus;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomResponse {

    private Long id;
    private Long propertyId;
    private String propertyName;    // tiện cho FE hiển thị, khỏi gọi thêm API
    private String roomNumber;
    private Integer floor;
    /** Giá niêm yết — alias listedPrice, giữ để FE cũ không vỡ. */
    private BigDecimal price;
    private BigDecimal listedPrice;
    /** Giá đang áp dụng (HĐ hiện hành). Trống thì = listedPrice. */
    private BigDecimal appliedPrice;
    /** true khi đơn vị đang có HĐ hiệu lực / hết hạn chờ thanh lý. */
    private Boolean priceLocked;
    private BigDecimal deposit;
    private Double area;
    private Double length;
    private Double width;
    private Integer maxOccupants;
    private String structureDescription;
    private String imageUrls;
    private PropertyType propertyType;
    private RoomStatus status;
    private String electricMeterCode;
    private String waterMeterCode;
    /** Số chữ số phần nguyên điện (default 5). */
    private Integer elecIntegerDigits;
    /** Số chữ số phần thập phân điện — khung đỏ (default 1). */
    private Integer elecDecimalDigits;
    private Integer waterIntegerDigits;
    private Integer waterDecimalDigits;
    private CurrentTenantResponse currentTenant;
}
