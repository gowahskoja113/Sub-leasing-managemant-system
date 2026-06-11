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
public class HostConfirmRequest {

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal contingencyPercent;

    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal propertyPrice;

    @Valid
    private List<RoomPriceConfirm> roomPrices;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RoomPriceConfirm {

        @NotNull
        private Long roomId;

        @NotNull
        @DecimalMin(value = "0.0", inclusive = false)
        private BigDecimal price;
    }
}
