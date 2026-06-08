package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.PricingScope;
import com.sep490.slms2026.enums.PropertyStatus;
import com.sep490.slms2026.enums.RoomStatus;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyActivationResponse {

    private Long propertyId;
    private PricingScope pricingScope;
    private PropertyStatus propertyStatus;

    // WHOLE_HOUSE
    private BigDecimal propertyPrice;
    private BigDecimal propertyDeposit;
    private BigDecimal suggestedMinPrice;

    // ROOM
    private List<ActivatedRoom> rooms;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ActivatedRoom {
        private Long roomId;
        private String roomNumber;
        private BigDecimal price;
        private BigDecimal deposit;
        private BigDecimal suggestedMinPrice;
        private RoomStatus status;
    }
}
