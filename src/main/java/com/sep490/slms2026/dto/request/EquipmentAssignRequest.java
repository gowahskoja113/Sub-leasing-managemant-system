package com.sep490.slms2026.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class EquipmentAssignRequest {

    /**
     * Cung cấp roomId nếu muốn gán vào phòng cụ thể.
     * Cung cấp propertyId nếu muốn gán vào nhà nguyên căn.
     * Cả hai null = bỏ gán (unassign).
     */
    private UUID roomId;

    private UUID propertyId;
}