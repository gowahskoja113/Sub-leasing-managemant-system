package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.EquipmentSource;
import com.sep490.slms2026.enums.EquipmentStatus;
import com.sep490.slms2026.enums.HouseArea;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EquipmentResponse {

    private Long id;
    private Long propertyId;
    private Long roomId;
    private String roomNumber;
    private Long catalogId;
    private String catalogName;
    private HouseArea houseArea;
    private EquipmentSource source;
    private EquipmentStatus status;
    private java.math.BigDecimal price;
    private String note;
    private Integer warrantyMonths;
    private java.time.LocalDate warrantyStartDate;
    private java.time.LocalDate warrantyEndDate;

    /** ACTIVE | DISABLED — chỉ áp dụng thiết bị PURCHASED vận hành */
    private String operationalStatus;
    private boolean currentEffective;
    private Integer renovationSessionNumber;
    private String renovationVersionLabel;
    private java.time.LocalDateTime disabledAt;
}
