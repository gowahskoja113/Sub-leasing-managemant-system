package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.EquipmentSource;
import com.sep490.slms2026.enums.EquipmentStatus;
import com.sep490.slms2026.enums.HouseArea;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionEquipmentResponse {

    private Long id;
    private Long catalogId;
    private String catalogName;
    private Long roomId;
    private String roomNumber;
    private HouseArea houseArea;
    private EquipmentSource source;
    private EquipmentStatus status;
    /** ACTIVE | DISABLED */
    private String operationalStatus;
    private boolean currentEffective;
    private BigDecimal price;
    private String note;
    private Integer warrantyMonths;
    private LocalDate warrantyStartDate;
    private LocalDate warrantyEndDate;
    private LocalDateTime disabledAt;
}
