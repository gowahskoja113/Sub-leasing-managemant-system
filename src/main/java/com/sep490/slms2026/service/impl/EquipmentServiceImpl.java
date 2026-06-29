package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.ReassignEquipmentRequest;
import com.sep490.slms2026.dto.response.EquipmentResponse;
import com.sep490.slms2026.entity.Equipment;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Room;
import com.sep490.slms2026.enums.PropertyStatus;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.EquipmentRepository;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.RoomRepository;
import com.sep490.slms2026.repository.TenantContractRepository;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.service.EquipmentService;
import com.sep490.slms2026.enums.ContractStatus;
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
    private final TenantContractRepository tenantContractRepository;
    private final com.sep490.slms2026.repository.EquipmentCatalogRepository equipmentCatalogRepository;

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
        if (!property.getStatus().isEquipmentEditable()) {
            throw new BusinessException(
                    "Chỉ có thể xoá thiết bị đã gán khi nhà đang trong quá trình onboarding (quy trình 3)");
        }
        Equipment equipment = equipmentRepository.findByIdAndPropertyId(equipmentId, propertyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy thiết bị ID=" + equipmentId + " trong tòa nhà ID=" + propertyId));
        equipmentRepository.delete(equipment);
    }

    @Override
    @Transactional
    public EquipmentResponse reassignEquipment(Long propertyId,
                                               Long equipmentId,
                                               ReassignEquipmentRequest request) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tòa nhà với ID: " + propertyId));
        if (!property.getStatus().isEquipmentEditable()) {
            throw new BusinessException(
                    "Chỉ có thể gán thiết bị từ kho khi nhà đang trong quá trình onboarding (quy trình 3)");
        }

        Equipment equipment = equipmentRepository.findByIdAndPropertyId(equipmentId, propertyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy thiết bị ID=" + equipmentId + " trong tòa nhà ID=" + propertyId));
        if (equipment.getRoom() != null) {
            throw new BusinessException("Thiết bị đã được gán phòng — chỉ gán lại thiết bị đang ở kho");
        }

        Room room = roomRepository.findByIdAndPropertyIdAndDeletedIsFalse(request.getRoomId(), propertyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy phòng ID=" + request.getRoomId() + " trong tòa nhà này"));

        equipment.setRoom(room);
        equipment.setHouseArea(request.getHouseArea());
        return toResponse(equipmentRepository.save(equipment));
    }

    private EquipmentResponse toResponse(Equipment equipment) {
        Integer sessionNumber = equipment.getRenovationSession() != null
                ? equipment.getRenovationSession().getSessionNumber() : null;
        com.sep490.slms2026.enums.EquipmentOperationalStatus opStatus = equipment.getOperationalStatus() != null
                ? equipment.getOperationalStatus() : com.sep490.slms2026.enums.EquipmentOperationalStatus.ACTIVE;
        return EquipmentResponse.builder()
                .id(equipment.getId())
                .propertyId(equipment.getProperty().getId())
                .roomId(equipment.getRoom() != null ? equipment.getRoom().getId() : null)
                .roomNumber(equipment.getRoom() != null ? equipment.getRoom().getRoomNumber() : null)
                .catalogId(equipment.getCatalog().getId())
                .catalogName(equipment.getCatalog().getName())
                .houseArea(equipment.getHouseArea())
                .source(equipment.getSource())
                .status(equipment.getStatus())
                .price(equipment.getPrice())
                .note(equipment.getNote())
                .warrantyMonths(equipment.getWarrantyMonths())
                .warrantyStartDate(equipment.getWarrantyStartDate())
                .warrantyEndDate(equipment.getWarrantyEndDate())
                .operationalStatus(opStatus.name())
                .currentEffective(opStatus == com.sep490.slms2026.enums.EquipmentOperationalStatus.ACTIVE)
                .renovationSessionNumber(sessionNumber)
                .renovationVersionLabel(sessionNumber != null ? "v" + sessionNumber : null)
                .disabledAt(equipment.getDisabledAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public EquipmentResponse getEquipmentById(Long equipmentId) {
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thiết bị ID: " + equipmentId));

        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        String role = user.getAuthorities().iterator().next().getAuthority();

        if ("ROLE_TENANT".equals(role)) {
            // Kiểm tra xem thiết bị có thuộc phòng/nhà mà tenant đang thuê hay không
            List<com.sep490.slms2026.entity.TenantContract> contracts = tenantContractRepository.findByTenantId(user.getId());
            boolean hasAccess = contracts.stream()
                    .filter(c -> c.getStatus() == ContractStatus.ACTIVE)
                    .anyMatch(c -> {
                        if (c.getRoom() != null) {
                            return equipment.getRoom() != null && equipment.getRoom().getId().equals(c.getRoom().getId());
                        } else {
                            // Whole house
                            return equipment.getProperty() != null && equipment.getProperty().getId().equals(c.getProperty().getId());
                        }
                    });

            if (!hasAccess) {
                throw new BusinessException("Bạn không có quyền xem thông tin thiết bị này");
            }
        }

        return toResponse(equipment);
    }

    @Override
    @Transactional
    public EquipmentResponse createAddedEquipment(Long propertyId, com.sep490.slms2026.dto.request.CreateAddedEquipmentRequest request) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tòa nhà với ID: " + propertyId));

        Room room = null;
        if (request.getRoomId() != null) {
            room = roomRepository.findByIdAndPropertyIdAndDeletedIsFalse(request.getRoomId(), propertyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phòng ID=" + request.getRoomId()));
        }

        com.sep490.slms2026.entity.EquipmentCatalog catalog = equipmentCatalogRepository.findFirstByNameIgnoreCaseAndActiveTrue(request.getEquipmentName())
                .orElseGet(() -> {
                    com.sep490.slms2026.entity.EquipmentCatalog newCatalog = com.sep490.slms2026.entity.EquipmentCatalog.builder()
                            .name(request.getEquipmentName())
                            .description(request.getCategory())
                            .active(true)
                            .build();
                    return equipmentCatalogRepository.save(newCatalog);
                });

        Equipment equipment = Equipment.builder()
                .property(property)
                .room(room)
                .catalog(catalog)
                .source(com.sep490.slms2026.enums.EquipmentSource.ADDED_BY_TENANT)
                .status(com.sep490.slms2026.enums.EquipmentStatus.NEW)
                .operationalStatus(com.sep490.slms2026.enums.EquipmentOperationalStatus.ACTIVE)
                .price(java.math.BigDecimal.ZERO) // Hoặc null nếu không có
                .build();

        return toResponse(equipmentRepository.save(equipment));
    }
}
