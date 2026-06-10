package com.sep490.slms2026.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RenovationLineResponse {

    private Long id;
    private Long categoryId;
    private String categoryCode;
    private String categoryName;
    private BigDecimal cost;
    private String note;
}
