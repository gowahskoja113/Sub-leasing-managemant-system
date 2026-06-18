package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.response.EquipmentMaintenanceHistoryResponse;
import com.sep490.slms2026.dto.response.EquipmentResponse;
import com.sep490.slms2026.enums.EquipmentStatus;

import java.util.List;

public interface EquipmentService {

    /** GET /api/v1/properties/{propertyId}/equipments — thiết bị theo tòa nhà */
    List<EquipmentResponse> getEquipmentsByProperty(Long propertyId);

    /** DELETE /api/v1/properties/{propertyId}/equipments/{equipmentId} — xóa thiết bị đã gán */
    void unassignEquipment(Long propertyId, Long equipmentId);

    /** GET /api/v1/equipment/{id} — chi tiết 1 thiết bị */
    EquipmentResponse getEquipmentById(Long id);

    /** PUT /api/v1/equipment/{id} — sửa thông tin thiết bị */
    EquipmentResponse updateEquipment(Long id, EquipmentResponse dto);

    /** PATCH /api/v1/equipment/{id}/status — đổi lifecycle */
    EquipmentResponse updateEquipmentStatus(Long id, EquipmentStatus status);

    /** GET /api/v1/equipment?roomId= — thiết bị theo phòng (cho OM mobile) */
    List<EquipmentResponse> getEquipmentsByRoom(Long roomId);

    /** GET /api/v1/equipment/{id}/maintenance-history — lịch sử bảo trì thiết bị */
    List<EquipmentMaintenanceHistoryResponse> getEquipmentMaintenanceHistory(Long equipmentId);
}
