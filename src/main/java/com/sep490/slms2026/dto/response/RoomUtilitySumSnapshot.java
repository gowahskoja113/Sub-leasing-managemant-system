package com.sep490.slms2026.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** Tổng tiêu thụ hoá đơn phòng còn hiệu lực — dùng chặn trần + đối soát. */
@Data
@Builder
public class RoomUtilitySumSnapshot {
    private BigDecimal sum;
    private List<RoomLine> rooms;

    @Data
    @Builder
    public static class RoomLine {
        private Long roomId;
        private String roomNumber;
        private BigDecimal consumption;
    }
}
