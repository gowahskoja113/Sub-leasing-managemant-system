package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.EquipmentSource;
import com.sep490.slms2026.enums.EquipmentStatus;
import com.sep490.slms2026.enums.HouseArea;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EquipmentResponse {

    private Long id;
    private Long propertyId;
    private Long roomId;
    private String roomName;
    private String roomNumber;
    private Long catalogId;
    private String catalogName;
    private HouseArea houseArea;
    private EquipmentSource source;
    private EquipmentStatus status;
    private java.math.BigDecimal price;
    private String note;

    // New fields per contract (maintenance)
    private String equipmentName;
    private String category;
    private LocalDate installationDate;
    /** Ngày mua / lắp (installationDate, fallback warrantyStartDate). */
    private LocalDate purchasedAt;
    private LocalDate warrantyExpiredDate;
    private int maintenanceCount;
    private LocalDateTime lastMaintenanceDate;

    // Fields from main
    private Integer warrantyMonths;
    private java.time.LocalDate warrantyStartDate;
    private java.time.LocalDate warrantyEndDate;
    /** Mức phạt cố định (VNĐ) khi hết bảo hành. */
    private java.math.BigDecimal penaltyFee;

    /** Giá trị còn lại sau khấu hao đường thẳng theo thời hạn bảo hành. */
    private java.math.BigDecimal remainingDepreciationAmount;
    /** Tổng số tháng bảo hành còn lại (≥ 0). */
    private Integer remainingWarrantyMonths;
    /** Phần năm của thời hạn còn lại (remainingWarrantyMonths / 12). */
    private Integer remainingWarrantyYears;
    /** Ví dụ: "Còn 1 năm 3 tháng", "Còn 8 tháng", "Hết bảo hành". */
    private String remainingWarrantyLabel;


    /** ACTIVE | DISABLED — áp dụng cho các thiết bị vận hành */
    private String operationalStatus;
    private boolean currentEffective;
    private Integer renovationSessionNumber;
    private String renovationVersionLabel;
    private java.time.LocalDateTime disabledAt;
    private String disabledReason;
    private String qrCode;
}
