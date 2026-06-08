package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.AddEquipmentRequest;
import com.sep490.slms2026.dto.response.EquipmentResponse;

import java.util.List;

public interface EquipmentService {

    EquipmentResponse addEquipment(Long propertyId, AddEquipmentRequest request);

    List<EquipmentResponse> getEquipmentsByProperty(Long propertyId);
}
