package com.sep490.slms2026.dto.request;

import com.sep490.slms2026.enums.ExpenseCategory;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ExpenseRequest {
    private Long propertyId;
    private ExpenseCategory category;
    private BigDecimal amount;
    private String month;
    private String note;
}
