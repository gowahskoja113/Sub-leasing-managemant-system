package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.response.EquipmentResponse;

import java.util.List;

public interface EquipmentService {

    List<EquipmentResponse> getEquipmentsByProperty(Long propertyId);
}
