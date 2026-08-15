package com.sep490.slms2026.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class RentScheduleItemRequest {
    private Integer fromMonth;
    private BigDecimal amount;
}
