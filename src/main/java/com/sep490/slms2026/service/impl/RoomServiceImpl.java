package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.AddRoomRequest;
import com.sep490.slms2026.dto.request.UpdateRoomRequest;
import com.sep490.slms2026.dto.request.UpdateRoomStatusRequest;
import com.sep490.slms2026.dto.response.CurrentTenantResponse;
import com.sep490.slms2026.dto.response.RoomResponse;
import com.sep490.slms2026.entity.Equipment;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Room;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.enums.PropertyStatus;
import com.sep490.slms2026.enums.RoomStatus;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.mapper.RoomMapper;
import com.sep490.slms2026.repository.EquipmentRepository;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.RoomRepository;
import com.sep490.slms2026.repository.TenantContractRepository;
import com.sep490.slms2026.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService {

    private final RoomRepository roomRepository;
    private final PropertyRepository propertyRepository;
    private final EquipmentRepository equipmentRepository;
    private final TenantContractRepository tenantContractRepository;
    private final RoomMapper roomMapper;

    @Override
    @Transactional
    public RoomResponse addRoom(Long propertyId, AddRoomRequest request) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy tòa nhà với ID: " + propertyId));

        if (!property.getStatus().isOnboardingEditable()) {
            throw new BusinessException(
                    "Chỉ có thể thêm phòng khi tòa nhà đang trong quá trình onboarding");
        }

        if (property.getWholeHouse() == null) {
            throw new BusinessException("Phải chọn loại hình thuê (onboarding-options) trước khi thêm phòng");
        }

        // Cho phép thêm phòng đối với cả nhà nguyên căn để phân bổ thiết bị, nhưng không quản lý giá tại phòng
        if (Boolean.TRUE.equals(property.getWholeHouse())) {
            request.setPrice(null);
            request.setDeposit(null);
        }

        if (property.getTotalFloor() != null && request.getFloor() != null && request.getFloor() > property.getTotalFloor()) {
            throw new BusinessException(
                    "Số tầng của phòng (" + request.getFloor() + ") vượt quá tổng số tầng của tòa nhà (" + property.getTotalFloor() + ")");
        }

        long currentCount = roomRepository.countByPropertyIdAndDeletedIsFalse(propertyId);
        if (property.getTotalRooms() != null && currentCount >= property.getTotalRooms()) {
            throw new BusinessException(
                    "Tòa nhà đã đủ " + property.getTotalRooms() + " phòng theo khai báo ban đầu");
        }

        if (roomRepository.existsByPropertyIdAndRoomNumberAndDeletedIsFalse(propertyId, request.getRoomNumber())) {
            throw new BusinessException(
                    "Số phòng '" + request.getRoomNumber() + "' đã tồn tại trong tòa nhà này");
        }

        Room room = roomMapper.toEntity(request);
        room.setProperty(property);
        room.setStatus(RoomStatus.DRAFT);

        Room saved = roomRepository.save(room);
        log.info("Đã tạo phòng {} cho tòa nhà ID={}", saved.getRoomNumber(), propertyId);

        return roomMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomResponse> getRoomsByProperty(Long propertyId) {
        if (!propertyRepository.existsById(propertyId)) {
            throw new ResourceNotFoundException(
                    "Không tìm thấy tòa nhà với ID: " + propertyId);
        }

        Map<Long, TenantContract> activeContractsByRoom = tenantContractRepository
                .findActiveWithTenantByPropertyId(propertyId)
                .stream()
                .filter(contract -> contract.getRoom() != null)
                .collect(Collectors.toMap(
                        contract -> contract.getRoom().getId(),
                        contract -> contract,
                        (left, right) -> left));

        return roomRepository.findByPropertyIdWithProperty(propertyId)
                .stream()
                .map(room -> enrichRoomResponse(room, activeContractsByRoom.get(room.getId())))
                .toList();
    }

    private RoomResponse enrichRoomResponse(Room room, TenantContract activeContract) {
        RoomResponse response = roomMapper.toResponse(room);
        if (room.getStatus() == RoomStatus.RENTED && activeContract != null
                && activeContract.getStatus() == ContractStatus.ACTIVE) {
            response.setCurrentTenant(CurrentTenantResponse.builder()
                    .fullName(activeContract.getTenant().getUser().getFullName())
                    .phone(activeContract.getTenant().getUser().getPhoneNumber())
                    .build());
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public RoomResponse getRoomById(Long propertyId, Long roomId) {
        Room room = roomRepository.findByIdAndPropertyIdAndDeletedIsFalse(roomId, propertyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy phòng ID=" + roomId + " trong tòa nhà ID=" + propertyId));
        return roomMapper.toResponse(room);
    }

    @Override
    @Transactional
    public RoomResponse updateRoomStatus(Long propertyId, Long roomId, UpdateRoomStatusRequest request) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy tòa nhà với ID: " + propertyId));

        PropertyStatus propertyStatus = property.getStatus();
        if (!propertyStatus.isOnboardingEditable() && propertyStatus != PropertyStatus.ACTIVE) {
            throw new BusinessException(
                    "Chỉ cập nhật trạng thái phòng khi tòa nhà đang onboarding hoặc ACTIVE");
        }
        if (Boolean.TRUE.equals(property.getWholeHouse())) {
            throw new BusinessException(
                    "Nhà nguyên căn không cập nhật trạng thái từng phòng — dùng trạng thái tòa nhà");
        }

        Room room = roomRepository.findByIdAndPropertyIdWithProperty(roomId, propertyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy phòng ID=" + roomId + " trong tòa nhà ID=" + propertyId));

        if (room.getStatus() == RoomStatus.RENTED) {
            throw new BusinessException(
                    "Phòng " + room.getRoomNumber() + " đang cho thuê — không thể đổi trạng thái");
        }
        if (room.getStatus() == RoomStatus.DRAFT) {
            throw new BusinessException(
                    "Phòng " + room.getRoomNumber() + " chưa kích hoạt — không thể cập nhật trạng thái");
        }

        RoomStatus newStatus = request.getStatus();
        if (newStatus != RoomStatus.AVAILABLE
                && newStatus != RoomStatus.MAINTENANCE
                && newStatus != RoomStatus.DISABLED) {
            throw new BusinessException(
                    "Chỉ cho phép cập nhật trạng thái AVAILABLE, MAINTENANCE hoặc DISABLED");
        }
        if (!isAllowedStatusTransition(room.getStatus(), newStatus)) {
            throw new BusinessException(
                    "Không thể chuyển trạng thái từ " + room.getStatus() + " sang " + newStatus);
        }

        room.setStatus(newStatus);
        Room saved = roomRepository.save(room);
        log.info("Đã cập nhật trạng thái phòng {} → {} (tòa nhà ID={})",
                saved.getRoomNumber(), newStatus, propertyId);

        return roomMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public RoomResponse updateRoom(Long propertyId, Long roomId, UpdateRoomRequest request) {
        Property property = loadPropertyForRoomManagement(propertyId);
        Room room = loadRoom(propertyId, roomId);

        if (room.getStatus() == RoomStatus.RENTED) {
            throw new BusinessException(
                    "Phòng " + room.getRoomNumber() + " đang cho thuê — không thể sửa");
        }

        if (Boolean.TRUE.equals(property.getWholeHouse())) {
            request.setPrice(null);
            request.setDeposit(null);
        }

        validateFloor(property, request.getFloor());

        if (roomRepository.existsByPropertyIdAndRoomNumberAndIdNotAndDeletedIsFalse(
                propertyId, request.getRoomNumber(), roomId)) {
            throw new BusinessException(
                    "Số phòng '" + request.getRoomNumber() + "' đã tồn tại trong tòa nhà này");
        }

        roomMapper.updateEntity(request, room);
        Room saved = roomRepository.save(room);
        log.info("Đã cập nhật phòng {} (ID={}) cho tòa nhà ID={}", saved.getRoomNumber(), roomId, propertyId);

        return roomMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteRoom(Long propertyId, Long roomId) {
        loadPropertyForRoomManagement(propertyId);
        Room room = roomRepository.findByIdAndPropertyIdAndDeletedIsFalse(roomId, propertyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy phòng ID=" + roomId + " trong tòa nhà ID=" + propertyId));

        if (room.getStatus() == RoomStatus.RENTED) {
            throw new BusinessException(
                    "Phòng " + room.getRoomNumber() + " đang cho thuê — không thể xóa");
        }

        List<Equipment> equipments = equipmentRepository.findByRoomId(roomId);
        equipments.forEach(equipment -> {
            equipment.setRoom(null);
            equipment.setHouseArea(null);
        });
        equipmentRepository.saveAll(equipments);

        room.setDeleted(true);
        roomRepository.save(room);
        log.info("Đã ẩn phòng {} (ID={}) — thiết bị đã chuyển về kho (tòa nhà ID={})",
                room.getRoomNumber(), roomId, propertyId);
    }

    private Property loadPropertyForRoomManagement(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy tòa nhà với ID: " + propertyId));

        PropertyStatus status = property.getStatus();
        if (!status.isOnboardingEditable()) {
            throw new BusinessException(
                    "Chỉ sửa/xóa phòng khi tòa nhà đang trong quá trình onboarding");
        }
        return property;
    }

    private Room loadRoom(Long propertyId, Long roomId) {
        return roomRepository.findByIdAndPropertyIdWithProperty(roomId, propertyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy phòng ID=" + roomId + " trong tòa nhà ID=" + propertyId));
    }

    private void validateFloor(Property property, Integer floor) {
        if (property.getTotalFloor() != null && floor != null && floor > property.getTotalFloor()) {
            throw new BusinessException(
                    "Số tầng của phòng (" + floor + ") vượt quá tổng số tầng của tòa nhà ("
                            + property.getTotalFloor() + ")");
        }
    }

    private boolean isAllowedStatusTransition(RoomStatus current, RoomStatus target) {
        if (current == target) {
            return true;
        }
        return switch (current) {
            case AVAILABLE -> target == RoomStatus.MAINTENANCE || target == RoomStatus.DISABLED;
            case MAINTENANCE -> target == RoomStatus.AVAILABLE || target == RoomStatus.DISABLED;
            case DISABLED -> target == RoomStatus.AVAILABLE || target == RoomStatus.MAINTENANCE;
            default -> false;
        };
    }
}
