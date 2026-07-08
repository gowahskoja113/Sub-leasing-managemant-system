package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.ExpenseCategory;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ExpenseResponse {
    private Long id;
    private Long propertyId;
    private String propertyName;
    private ExpenseCategory category;
    private BigDecimal amount;
    private String month;
    private String note;
    private String createdBy;
    private LocalDateTime createdAt;
}
