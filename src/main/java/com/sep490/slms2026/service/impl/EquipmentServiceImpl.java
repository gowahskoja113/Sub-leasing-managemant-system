package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.response.EquipmentMaintenanceHistoryResponse;
import com.sep490.slms2026.dto.response.EquipmentResponse;
import com.sep490.slms2026.entity.Equipment;
import com.sep490.slms2026.entity.EquipmentMaintenanceHistory;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.enums.EquipmentStatus;
import com.sep490.slms2026.enums.PropertyStatus;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.EquipmentMaintenanceHistoryRepository;
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
    private final EquipmentMaintenanceHistoryRepository equipmentHistoryRepository;

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

    @Override
    @Transactional(readOnly = true)
    public EquipmentResponse getEquipmentById(Long id) {
        Equipment equipment = equipmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thiết bị ID=" + id));
        return toResponse(equipment);
    }

    @Override
    @Transactional
    public EquipmentResponse updateEquipment(Long id, EquipmentResponse dto) {
        Equipment equipment = equipmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thiết bị ID=" + id));

        if (dto.getEquipmentName() != null) {
            equipment.setEquipmentName(dto.getEquipmentName());
        }
        if (dto.getCategory() != null) {
            equipment.setEquipmentCategory(dto.getCategory());
        }
        if (dto.getQrCode() != null) {
            equipment.setQrCode(dto.getQrCode());
        }
        if (dto.getInstallationDate() != null) {
            equipment.setInstallationDate(dto.getInstallationDate());
        }
        if (dto.getWarrantyExpiredDate() != null) {
            equipment.setWarrantyExpiredDate(dto.getWarrantyExpiredDate());
        }
        if (dto.getNote() != null) {
            equipment.setNote(dto.getNote());
        }

        return toResponse(equipmentRepository.save(equipment));
    }

    @Override
    @Transactional
    public EquipmentResponse updateEquipmentStatus(Long id, EquipmentStatus status) {
        Equipment equipment = equipmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thiết bị ID=" + id));
        equipment.setStatus(status);
        return toResponse(equipmentRepository.save(equipment));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EquipmentResponse> getEquipmentsByRoom(Long roomId) {
        return equipmentRepository.findByRoomId(roomId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EquipmentMaintenanceHistoryResponse> getEquipmentMaintenanceHistory(Long equipmentId) {
        if (!equipmentRepository.existsById(equipmentId)) {
            throw new ResourceNotFoundException("Không tìm thấy thiết bị ID=" + equipmentId);
        }
        return equipmentHistoryRepository.findByEquipmentIdOrderByMaintenanceDateDesc(equipmentId)
                .stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    // ========== PRIVATE MAPPERS ==========

    private EquipmentResponse toResponse(Equipment equipment) {
        return EquipmentResponse.builder()
                .id(equipment.getId())
                .propertyId(equipment.getProperty().getId())
                .roomId(equipment.getRoom() != null ? equipment.getRoom().getId() : null)
                .roomName(equipment.getRoom() != null ? equipment.getRoom().getRoomNumber() : null)
                .catalogId(equipment.getCatalog().getId())
                .catalogName(equipment.getCatalog().getName())
                .houseArea(equipment.getHouseArea())
                .source(equipment.getSource())
                .status(equipment.getStatus())
                .price(equipment.getPrice())
                .note(equipment.getNote())
                .equipmentName(equipment.getEquipmentName())
                .category(equipment.getEquipmentCategory())
                .qrCode(equipment.getQrCode())
                .installationDate(equipment.getInstallationDate())
                .warrantyExpiredDate(equipment.getWarrantyExpiredDate())
                .maintenanceCount(equipment.getMaintenanceCount())
                .lastMaintenanceDate(equipment.getLastMaintenanceDate())
                .build();
    }

    private EquipmentMaintenanceHistoryResponse toHistoryResponse(EquipmentMaintenanceHistory history) {
        return EquipmentMaintenanceHistoryResponse.builder()
                .id(history.getId())
                .equipmentId(history.getEquipment().getId())
                .maintenanceRequestId(history.getMaintenanceRequest().getId())
                .requestCode(history.getMaintenanceRequest().getRequestCode())
                .maintenanceDate(history.getMaintenanceDate())
                .repairCost(history.getRepairCost())
                .note(history.getNote())
                .build();
    }
}
