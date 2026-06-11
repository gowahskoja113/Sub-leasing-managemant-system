package com.sep490.slms2026.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

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

    /** Tuỳ chọn — nếu gửi kèm sẽ gán OM và kích hoạt tòa nhà ngay trong cùng request */
    @JsonAlias({"id", "managerId"})
    private UUID operationManagerId;

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
