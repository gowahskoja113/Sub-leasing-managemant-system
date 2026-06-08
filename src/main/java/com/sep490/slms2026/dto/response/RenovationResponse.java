package com.sep490.slms2026.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RenovationResponse {

    private Long id;
    private Long propertyId;
    private Long roomId;
    private String description;
    private BigDecimal cost;
    private boolean completed;
}
