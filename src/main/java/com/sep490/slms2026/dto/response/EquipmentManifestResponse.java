package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.EquipmentStatus;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EquipmentManifestResponse {

    private Long id;
    private Long catalogId;
    private String catalogName;
    private Integer quantity;
    private EquipmentStatus status;
    private com.sep490.slms2026.enums.EquipmentSource source;
    private java.math.BigDecimal price;
    private long assignedCount;
}
