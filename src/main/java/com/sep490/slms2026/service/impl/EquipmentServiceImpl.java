package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.response.EquipmentResponse;
import com.sep490.slms2026.entity.Equipment;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.enums.PropertyStatus;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.EquipmentRepository;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.service.EquipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EquipmentServiceImpl implements EquipmentService {

    private final EquipmentRepository equipmentRepository;
    private final PropertyRepository propertyRepository;

    @Override
    @Transactional(readOnly = true)
    public List<EquipmentResponse> getEquipmentsByProperty(Long propertyId) {
        if (!propertyRepository.existsById(propertyId)) {
            throw new ResourceNotFoundException("Không tìm thấy tòa nhà với ID: " + propertyId);
        }
        return equipmentRepository.findByPropertyId(propertyId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void unassignEquipment(Long propertyId, Long equipmentId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tòa nhà với ID: " + propertyId));
        if (property.getStatus() != PropertyStatus.DRAFT && property.getStatus() != PropertyStatus.UNDER_RENOVATION) {
            throw new BusinessException("Chỉ có thể xoá thiết bị đã gán khi tòa nhà đang ở trạng thái DRAFT hoặc UNDER_RENOVATION");
        }
        Equipment equipment = equipmentRepository.findByIdAndPropertyId(equipmentId, propertyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy thiết bị ID=" + equipmentId + " trong tòa nhà ID=" + propertyId));
        equipmentRepository.delete(equipment);
    }

    private EquipmentResponse toResponse(Equipment equipment) {
        return EquipmentResponse.builder()
                .id(equipment.getId())
                .propertyId(equipment.getProperty().getId())
                .roomId(equipment.getRoom() != null ? equipment.getRoom().getId() : null)
                .catalogId(equipment.getCatalog().getId())
                .catalogName(equipment.getCatalog().getName())
                .houseArea(equipment.getHouseArea())
                .source(equipment.getSource())
                .status(equipment.getStatus())
                .price(equipment.getPrice())
                .note(equipment.getNote())
                .build();
    }
}
