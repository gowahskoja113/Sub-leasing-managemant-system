package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.EquipmentAssignRequest;
import com.sep490.slms2026.dto.request.EquipmentRequest;
import com.sep490.slms2026.dto.response.EquipmentResponse;

import java.util.List;
import java.util.UUID;

public interface EquipmentService {

    /** Tạo mới thiết bị, có thể gán ngay vào room hoặc property */
    EquipmentResponse create(EquipmentRequest request);

    /** Lấy chi tiết 1 thiết bị */
    EquipmentResponse getById(UUID id);

    /** Lấy tất cả thiết bị trong 1 phòng */
    List<EquipmentResponse> getByRoom(UUID roomId);

    /** Lấy tất cả thiết bị thuộc 1 property (bao gồm tất cả phòng) */
    List<EquipmentResponse> getByProperty(UUID propertyId);

    /** Cập nhật thông tin thiết bị (không bao gồm gán phòng) */
    EquipmentResponse update(UUID id, EquipmentRequest request);

    /** Gán hoặc chuyển thiết bị sang phòng / property khác */
    EquipmentResponse assign(UUID id, EquipmentAssignRequest assignRequest);

    /** Xóa thiết bị */
    void delete(UUID id);

    /** Lấy thông tin thiết bị qua QR payload (dùng cho màn hình bảo trì) */
    EquipmentResponse getByQrPayload(String qrPayload);
}