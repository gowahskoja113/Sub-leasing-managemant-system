package com.sep490.slms2026.dto.request;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompleteCheckoutRequest {

    /** Ngày trả phòng thực tế. Mặc định hôm nay. */
    private LocalDate actualMoveOutDate;

    /** Ghi chú khi hoàn tất (biên bản bàn giao, hiện trạng...). */
    private String note;
}
