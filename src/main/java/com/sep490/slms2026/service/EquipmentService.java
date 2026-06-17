package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.response.EquipmentResponse;

import java.util.List;
import java.util.UUID;

public interface EquipmentService {

    public List<EquipmentResponse> getEquipmentsByProperty(Long propertyId);

    void unassignEquipment(Long propertyId, Long equipmentId);
}
