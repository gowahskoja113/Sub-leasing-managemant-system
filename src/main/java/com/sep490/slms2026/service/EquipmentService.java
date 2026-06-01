package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.EquipmentRequest;
import com.sep490.slms2026.dto.response.EquipmentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface EquipmentService {

    // Gán thiết bị vào phòng (tự động sinh QR)
    EquipmentResponse createEquipment(EquipmentRequest request, UUID managerId);

    // Lấy danh sách thiết bị theo phòng
    Page<EquipmentResponse> getEquipmentByRoom(UUID roomId, UUID managerId, Pageable pageable);

    // Lấy danh sách thiết bị theo property
    Page<EquipmentResponse> getEquipmentByProperty(UUID propertyId, UUID managerId, Pageable pageable);

    // Cập nhật thông tin thiết bị
    EquipmentResponse updateEquipment(UUID id, EquipmentRequest request, UUID managerId);

    // Xoá thiết bị
    void deleteEquipment(UUID id, UUID managerId);

    // Lấy thông tin QR (trả về base64 PNG)
    EquipmentResponse getEquipmentDetail(UUID id, UUID managerId);
}