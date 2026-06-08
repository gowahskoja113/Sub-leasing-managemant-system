package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.AddEquipmentRequest;
import com.sep490.slms2026.dto.response.EquipmentResponse;
import com.sep490.slms2026.entity.Equipment;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Room;
import com.sep490.slms2026.enums.EquipmentSource;
import com.sep490.slms2026.enums.EquipmentStatus;
import com.sep490.slms2026.enums.PropertyStatus;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.EquipmentRepository;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.RoomRepository;
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
    private final RoomRepository roomRepository;

    @Override
    @Transactional
    public EquipmentResponse addEquipment(Long propertyId, AddEquipmentRequest request) {
        Property property = findDraftProperty(propertyId);
        Room room = resolveRoom(property, propertyId, request.getRoomId());

        if (request.getSource() == EquipmentSource.PURCHASED
                && request.getPurchasePrice() == null) {
            // Cho phép tạo nháp không có giá — sẽ cập nhật trước khi tính khấu hao
        }

        Equipment equipment = Equipment.builder()
                .property(property)
                .room(room)
                .name(request.getName())
                .source(request.getSource())
                .purchasePrice(request.getPurchasePrice())
                .status(request.getStatus() != null ? request.getStatus() : EquipmentStatus.NEW)
                .note(request.getNote())
                .build();

        Equipment saved = equipmentRepository.save(equipment);
        return toResponse(saved);
    }

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

    private Property findDraftProperty(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy tòa nhà với ID: " + propertyId));
        if (property.getStatus() != PropertyStatus.DRAFT) {
            throw new BusinessException(
                    "Chỉ có thể thêm thiết bị khi tòa nhà đang ở trạng thái DRAFT");
        }
        return property;
    }

    private Room resolveRoom(Property property, Long propertyId, Long roomId) {
        if (Boolean.TRUE.equals(property.getWholeHouse())) {
            if (roomId != null) {
                throw new BusinessException(
                        "Nhà nguyên căn không gắn thiết bị theo phòng — chỉ thêm ở cấp tòa nhà");
            }
            return null;
        }
        if (roomId == null) {
            return null;
        }
        return roomRepository.findByIdAndPropertyId(roomId, propertyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy phòng ID=" + roomId + " trong tòa nhà ID=" + propertyId));
    }

    private EquipmentResponse toResponse(Equipment equipment) {
        return EquipmentResponse.builder()
                .id(equipment.getId())
                .propertyId(equipment.getProperty().getId())
                .roomId(equipment.getRoom() != null ? equipment.getRoom().getId() : null)
                .name(equipment.getName())
                .source(equipment.getSource())
                .purchasePrice(equipment.getPurchasePrice())
                .status(equipment.getStatus())
                .note(equipment.getNote())
                .build();
    }
}
