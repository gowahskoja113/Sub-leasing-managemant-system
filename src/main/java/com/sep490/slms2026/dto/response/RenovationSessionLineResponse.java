package com.sep490.slms2026.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RenovationSessionLineResponse {

    private Long id;
    private String categoryName;
    private BigDecimal cost;
    private String note;
}
