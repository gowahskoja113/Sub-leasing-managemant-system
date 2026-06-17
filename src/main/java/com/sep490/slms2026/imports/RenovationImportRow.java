package com.sep490.slms2026.imports;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class RenovationImportRow {

    private int rowNumber;
    private String contractCode;
    private String categoryCode;
    private String categoryName;
    private BigDecimal cost;
    private String note;
}
