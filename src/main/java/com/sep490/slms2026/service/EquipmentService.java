package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.ReassignEquipmentRequest;
import com.sep490.slms2026.dto.response.EquipmentResponse;

import java.util.List;

public interface EquipmentService {

    List<EquipmentResponse> getEquipmentsByProperty(Long propertyId);

    void unassignEquipment(Long propertyId, Long equipmentId);

    EquipmentResponse reassignEquipment(Long propertyId, Long equipmentId, ReassignEquipmentRequest request);

    EquipmentResponse getEquipmentById(Long equipmentId);

    EquipmentResponse createAddedEquipment(Long propertyId, com.sep490.slms2026.dto.request.CreateAddedEquipmentRequest request);

    List<EquipmentResponse> getEquipmentsForCurrentTenant();

    EquipmentResponse getEquipmentByQrCode(String qrCode);
}
