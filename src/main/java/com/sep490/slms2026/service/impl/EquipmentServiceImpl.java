package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.ReassignEquipmentRequest;
import com.sep490.slms2026.dto.response.EquipmentMaintenanceHistoryResponse;
import com.sep490.slms2026.dto.response.EquipmentResponse;
import com.sep490.slms2026.entity.Equipment;
import com.sep490.slms2026.entity.EquipmentMaintenanceHistory;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Room;
import com.sep490.slms2026.enums.EquipmentStatus;
import com.sep490.slms2026.enums.PropertyStatus;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.EquipmentMaintenanceHistoryRepository;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EquipmentServiceImpl implements EquipmentService {

    private final EquipmentRepository equipmentRepository;
    private final PropertyRepository propertyRepository;
    private final EquipmentMaintenanceHistoryRepository equipmentHistoryRepository;
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

    // ========== PRIVATE MAPPERS ==========

    private EquipmentResponse toResponse(Equipment equipment) {
        Integer sessionNumber = equipment.getRenovationSession() != null
                ? equipment.getRenovationSession().getSessionNumber() : null;
        com.sep490.slms2026.enums.EquipmentOperationalStatus opStatus = equipment.getOperationalStatus() != null
                ? equipment.getOperationalStatus() : com.sep490.slms2026.enums.EquipmentOperationalStatus.ACTIVE;
        return EquipmentResponse.builder()
                .id(equipment.getId())
                .propertyId(equipment.getProperty().getId())
                .roomId(equipment.getRoom() != null ? equipment.getRoom().getId() : null)
                .roomName(equipment.getRoom() != null ? equipment.getRoom().getRoomNumber() : null)
                .roomNumber(equipment.getRoom() != null ? equipment.getRoom().getRoomNumber() : null)
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
                .warrantyMonths(equipment.getWarrantyMonths())
                .warrantyStartDate(equipment.getWarrantyStartDate())
                .warrantyEndDate(equipment.getWarrantyEndDate())
                .operationalStatus(opStatus.name())
                .currentEffective(opStatus == com.sep490.slms2026.enums.EquipmentOperationalStatus.ACTIVE)
                .renovationSessionNumber(sessionNumber)
                .renovationVersionLabel(sessionNumber != null ? "v" + sessionNumber : null)
                .disabledAt(equipment.getDisabledAt())
                .disabledReason(equipment.getDisabledReason())
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
                .price(request.getCost() != null ? request.getCost() : java.math.BigDecimal.ZERO)
                .build();

        return toResponse(persistWithQrCode(equipment));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EquipmentResponse> getEquipmentsForCurrentTenant() {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        com.sep490.slms2026.entity.TenantContract activeContract = tenantContractRepository
                .findByTenantId(user.getId()).stream()
                .filter(c -> c.getStatus() == ContractStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy hợp đồng đang hiệu lực"));

        Long propertyId = activeContract.getProperty().getId();
        Long roomId = activeContract.getRoom() != null ? activeContract.getRoom().getId() : null;

        return equipmentRepository.findActiveForTenantPlacement(propertyId, roomId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EquipmentResponse getEquipmentByQrCode(String qrCode) {
        Equipment equipment = equipmentRepository.findByQrCode(qrCode)
                .orElseGet(() -> resolveEquipmentByQrFallback(qrCode));

        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        String role = user.getAuthorities().iterator().next().getAuthority();
        if ("ROLE_TENANT".equals(role)) {
            assertTenantCanAccessEquipment(user.getId(), equipment);
        }
        return toResponse(equipment);
    }

    private Equipment resolveEquipmentByQrFallback(String qrCode) {
        if (qrCode != null && qrCode.toUpperCase().startsWith("EQ-")) {
            try {
                Long id = Long.parseLong(qrCode.substring(3));
                return equipmentRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Không tìm thấy thiết bị với mã QR: " + qrCode));
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        throw new ResourceNotFoundException("Không tìm thấy thiết bị với mã QR: " + qrCode);
    }

    private void assertTenantCanAccessEquipment(UUID tenantUserId, Equipment equipment) {
        boolean allowed = tenantContractRepository.findByTenantId(tenantUserId).stream()
                .filter(c -> c.getStatus() == ContractStatus.ACTIVE)
                .anyMatch(c -> {
                    if (c.getRoom() != null) {
                        return equipment.getProperty().getId().equals(c.getProperty().getId())
                                && (equipment.getRoom() == null
                                || equipment.getRoom().getId().equals(c.getRoom().getId()));
                    }
                    return equipment.getProperty().getId().equals(c.getProperty().getId());
                });
        if (!allowed) {
            throw new BusinessException("Bạn không có quyền xem thiết bị này");
        }
    }

    private Equipment persistWithQrCode(Equipment equipment) {
        Equipment saved = equipmentRepository.save(equipment);
        if (saved.getQrCode() == null) {
            saved.setQrCode("EQ-" + saved.getId());
            saved = equipmentRepository.save(saved);
        }
        return saved;
    }

    @Override
    @Transactional
    public EquipmentResponse updateEquipmentOperationalStatus(Long equipmentId, com.sep490.slms2026.dto.request.UpdateEquipmentOperationalStatusRequest request) {
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thiết bị ID: " + equipmentId));
        
        equipment.setOperationalStatus(request.getOperationalStatus());
        if (request.getOperationalStatus() == com.sep490.slms2026.enums.EquipmentOperationalStatus.DISABLED) {
            equipment.setDisabledAt(java.time.LocalDateTime.now());
            equipment.setDisabledReason(request.getReason());
        } else {
            equipment.setDisabledAt(null);
            equipment.setDisabledReason(null);
        }
        
        return toResponse(equipmentRepository.save(equipment));
    }
}
