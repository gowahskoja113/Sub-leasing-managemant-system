package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.EquipmentSource;
import com.sep490.slms2026.enums.EquipmentStatus;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EquipmentResponse {

    private Long id;
    private Long propertyId;
    private Long roomId;
    private String name;
    private EquipmentSource source;
    private BigDecimal purchasePrice;
    private EquipmentStatus status;
    private String note;
}
