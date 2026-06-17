package com.sep490.slms2026.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RenovationSessionResponse {

    private Integer sessionNumber;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal totalCost;
    private List<RenovationSessionLineResponse> lines;
}
