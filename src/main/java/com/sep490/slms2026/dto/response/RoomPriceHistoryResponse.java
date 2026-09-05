package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.RoomPriceChangeType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class RoomPriceHistoryResponse {

    private Long id;
    private Long propertyId;
    private Long roomId;
    private String roomNumber;
    private RoomPriceChangeType changeType;
    private String changeTypeLabel;
    private BigDecimal oldPrice;
    private BigDecimal newPrice;
    private Long contractId;
    private String reason;
    private UUID changedBy;
    private String changedByName;
    private LocalDateTime changedAt;
}
