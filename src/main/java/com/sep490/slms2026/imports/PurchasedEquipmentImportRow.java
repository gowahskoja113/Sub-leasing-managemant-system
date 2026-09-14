package com.sep490.slms2026.imports;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class PurchasedEquipmentImportRow {

    private int rowNumber;
    private String contractCode;
    private String roomNumber;
    private String houseAreaRaw;
    private String catalogName;
    private String statusRaw;
    private Integer quantity;
    private BigDecimal price;
    private Integer warrantyMonths;
    private LocalDate warrantyStartDate;
    private LocalDate warrantyEndDate;
    /** Mức phạt cố định (VNĐ) khi hết bảo hành — không tính từ đơn giá. */
    private BigDecimal penaltyFee;
    private String note;
    /** THEM_MOI | THAY_THE */
    private String actionRaw;
}
