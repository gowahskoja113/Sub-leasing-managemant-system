package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.ZoneSummaryProjection;
import com.sep490.slms2026.dto.request.PropertyRequest;
import com.sep490.slms2026.dto.request.RoomRequest;
import com.sep490.slms2026.dto.response.PropertyResponse;
import com.sep490.slms2026.entity.*;
import com.sep490.slms2026.enums.RoomStatus;
import com.sep490.slms2026.enums.PropertyType;
import com.sep490.slms2026.mapper.PropertyMapper;
import com.sep490.slms2026.repository.OperationManagementRepository;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.repository.ZoneRepository;
import com.sep490.slms2026.service.PropertyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PropertyServiceImpl implements PropertyService {

    private final OperationManagementRepository operationManagementRepository;
    private final ZoneRepository zoneRepository;
    private final PropertyRepository propertyRepository;
    private final PropertyMapper propertyMapper;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ZoneSummaryProjection> getManagerDashboard(UUID managerId) {
        return zoneRepository.getZoneSummaryByManager(managerId);
    }

    @Override
    @Transactional
    public PropertyResponse createProperty(PropertyRequest request, UUID managerId) {
        if (propertyRepository.existsByAddressIgnoreCase(request.getAddress().trim())) {
            throw new RuntimeException("Địa chỉ bất động sản này đã tồn tại trên hệ thống!");
        }

        Zone propertyZone = zoneRepository.findById(request.getZoneId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Vùng (Zone) đã chọn cho Bất động sản!"));

        if (propertyZone.getLevel() != 3) {
            throw new RuntimeException("Lỗi cấu trúc địa chỉ: Địa chỉ Bất động sản bắt buộc phải chọn chi tiết đến cấp Phường/Xã (Level 3)!");
        }

        checkPermissionByZoneTree(managerId, propertyZone);

        Property property = propertyMapper.toEntity(request);
        property.setZone(propertyZone);

        if (property.getRooms() != null) {
            property.getRooms().forEach(room -> room.setProperty(null));
            property.getRooms().clear();
        }

        processRoomsLogic(request, property);

        Property savedProperty = propertyRepository.save(property);
        return propertyMapper.toResponse(savedProperty);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PropertyResponse> getPropertiesForManager(UUID managerId, Pageable pageable) {
        return propertyRepository.findAllByManagerZones(managerId, pageable)
                .map(propertyMapper::toResponse);
    }

    @Override
    @Transactional
    public PropertyResponse updateProperty(UUID id, PropertyRequest request, UUID managerId) {
        if (propertyRepository.existsByAddressIgnoreCaseAndIdNot(request.getAddress().trim(), id)) {
            throw new RuntimeException("Địa chỉ này đã bị trùng với một bất động sản khác trên hệ thống!");
        }

        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Bất động sản này"));

        Zone newZone = zoneRepository.findById(request.getZoneId())
                .orElseThrow(() -> new RuntimeException("Zone mới không tồn tại"));

        if (newZone.getLevel() != 3) {
            throw new RuntimeException("Lỗi cấu trúc địa chỉ: Địa chỉ định đổi bắt buộc phải thuộc cấp Phường/Xã (Level 3)!");
        }

        checkPermissionByZoneTree(managerId, property.getZone());
        checkPermissionByZoneTree(managerId, newZone);

        propertyMapper.updateEntityFromRequest(request, property);
        property.setZone(newZone);

        return propertyMapper.toResponse(propertyRepository.save(property));
    }

    @Override
    @Transactional
    public void deleteProperty(UUID id, UUID managerId) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Bất động sản này"));

        checkPermissionByZoneTree(managerId, property.getZone());
        propertyRepository.delete(property);
    }

    private void checkPermissionByZoneTree(UUID managerId, Zone targetZone) {
        var user = userRepository.findById(managerId)
                .orElseThrow(() -> new AccessDeniedException("Tài khoản không tồn tại trên hệ thống!"));

        if (com.sep490.slms2026.enums.Role.ROLE_ADMIN.equals(user.getRole())) {
            return;
        }

        OperationManagement op = operationManagementRepository.findById(managerId)
                .orElseThrow(() -> new AccessDeniedException("Tài khoản không có quyền quản lý vận hành!"));

        List<Zone> managerZones = op.getZones();
        Zone currentZone = targetZone;
        boolean hasPermission = false;

        while (currentZone != null) {
            final UUID currentId = currentZone.getId();
            if (managerZones.stream().anyMatch(mz -> mz.getId().equals(currentId))) {
                hasPermission = true;
                break;
            }
            currentZone = currentZone.getParent();
        }

        if (!hasPermission) {
            throw new AccessDeniedException("Bạn không có quyền thao tác trên Khu Vực địa lý này! [403]");
        }
    }
    // Hàm gọi chính khi Create Property
    private void processRoomsLogic(PropertyRequest request, Property property) {
        if (Boolean.TRUE.equals(request.getWholeHouse())) {
            setupWholeHouse(request, property);
        } else {
            setupIndividualRooms(request, property);
        }
    }

    // 🏠 LOGIC 1: Setup Nhà Nguyên Căn
    private void setupWholeHouse(PropertyRequest request, Property property) {
        if (request.getDefaultPrice() == null || request.getDefaultDeposit() == null || request.getDefaultArea() == null) {
            throw new RuntimeException("Vui lòng nhập giá thuê, tiền cọc và diện tích cho nhà nguyên căn!");
        }

        Room wholeRoom = new Room();
        wholeRoom.setPropertyType(PropertyType.WHOLE_HOUSE);
        wholeRoom.setRoomNumber((request.getTitle() != null && !request.getTitle().isBlank()) ? request.getTitle() : "Nguyên Căn");

        wholeRoom.setPrice(request.getDefaultPrice());
        wholeRoom.setDeposit(request.getDefaultDeposit());
        wholeRoom.setArea(request.getDefaultArea());
        wholeRoom.setMaxOccupants(request.getDefaultMaxOccupants()); // Số người ở
        wholeRoom.setStructureDescription(request.getStructureDescription()); // Gồm những phòng nào
        wholeRoom.setStatus(RoomStatus.AVAILABLE);
        wholeRoom.setProperty(property);

        property.getRooms().add(wholeRoom);
        property.setTotalRooms(1);
    }

    // 🚪 LOGIC 2: Setup Cho Thuê Phòng Lẻ
    private void setupIndividualRooms(PropertyRequest request, Property property) {
        if (request.getRooms() == null || request.getRooms().isEmpty()) {
            throw new RuntimeException("Hình thức cho thuê theo phòng yêu cầu phải có danh sách phòng (rooms).");
        }

        for (int i = 0; i < request.getRooms().size(); i++) {
            RoomRequest roomReq = request.getRooms().get(i);

            if (roomReq.getRoomNumber() == null || roomReq.getRoomNumber().isBlank()) {
                throw new RuntimeException("Phòng thứ " + (i + 1) + " chưa có số phòng!");
            }
            if (roomReq.getPrice() == null || roomReq.getDeposit() == null || roomReq.getArea() == null) {
                throw new RuntimeException("Phòng \"" + roomReq.getRoomNumber() + "\": giá thuê, cọc và diện tích không được trống!");
            }

            Room room = new Room();
            room.setPropertyType(PropertyType.INDIVIDUAL_ROOM);
            room.setRoomNumber(roomReq.getRoomNumber());
            room.setPrice(roomReq.getPrice());
            room.setDeposit(roomReq.getDeposit());
            room.setArea(roomReq.getArea());
            room.setMaxOccupants(roomReq.getMaxOccupants()); // Số người ở
            room.setStatus(RoomStatus.AVAILABLE);
            room.setProperty(property);

            property.getRooms().add(room);
        }

        property.setTotalRooms(request.getRooms().size());
    }
}