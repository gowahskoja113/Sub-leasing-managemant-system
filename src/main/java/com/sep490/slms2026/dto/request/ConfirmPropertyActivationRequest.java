package com.sep490.slms2026.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfirmPropertyActivationRequest {

    // === Nhà nguyên căn (wholeHouse) ===
    @DecimalMin(value = "0.0", inclusive = false, message = "Giá thuê nhà phải lớn hơn 0")
    private BigDecimal propertyPrice;

    @DecimalMin(value = "0.0", message = "Tiền cọc không được âm")
    private BigDecimal propertyDeposit;

    // === Nhà chia phòng ===
    @Valid
    private List<RoomPriceConfirm> roomPrices;

    // true nếu còn hạng mục cải tạo chưa xong → chuyển MAINTENANCE
    private Boolean hasOngoingRenovation;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RoomPriceConfirm {

        @NotNull(message = "roomId không được để trống")
        private Long roomId;

        @NotNull(message = "Giá thuê xác nhận không được để trống")
        @DecimalMin(value = "0.0", inclusive = false, message = "Giá thuê phải lớn hơn 0")
        private BigDecimal price;

        @NotNull(message = "Tiền cọc không được để trống")
        @DecimalMin(value = "0.0", message = "Tiền cọc không được âm")
        private BigDecimal deposit;
    }
}
