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
    private Long catalogId;
    private String catalogName;
    private HouseArea houseArea;
    private EquipmentSource source;
    private EquipmentStatus status;
    private java.math.BigDecimal price;
    private String note;

    // New fields per contract
    private String equipmentName;
    private String category;
    private String qrCode;
    private LocalDate installationDate;
    private LocalDate warrantyExpiredDate;
    private int maintenanceCount;
    private LocalDateTime lastMaintenanceDate;
}
