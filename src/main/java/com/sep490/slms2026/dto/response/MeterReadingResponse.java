package com.sep490.slms2026.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeterReadingResponse {
    private BigDecimal reading;
    private String period;
    private String recordedAt;
    private String type;
    private String imageUrl;
}
