package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.RoomRequest;
import com.sep490.slms2026.dto.response.RoomResponse;
import com.sep490.slms2026.entity.OperationManagement;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Room;
import com.sep490.slms2026.entity.Zone;
import com.sep490.slms2026.enums.RoomStatus;
import com.sep490.slms2026.mapper.RoomMapper;
import com.sep490.slms2026.repository.OperationManagementRepository;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.RoomRepository;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.service.RoomService;
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
public class RoomServiceImpl implements RoomService {

    private final RoomRepository roomRepository;
    private final PropertyRepository propertyRepository;
    private final OperationManagementRepository operationManagementRepository;
    private final UserRepository userRepository;
    private final RoomMapper roomMapper;

    @Override
    @Transactional
    public RoomResponse addRoomToProperty(UUID propertyId, RoomRequest request, UUID managerId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Bất động sản chỉ định!"));

        if (property.isWholeHouse()) {
            throw new RuntimeException("Bất động sản này thuê theo hình thức Nguyên Căn, không thể thêm phòng lẻ!");
        }

        // Kiểm tra quyền của Manager đối với khu vực của tòa nhà
        checkPermissionByZoneTree(managerId, property.getZone());

        if (roomRepository.existsByRoomNumberAndPropertyId(request.getRoomNumber(), propertyId)) {
            throw new RuntimeException("Số phòng '" + request.getRoomNumber() + "' đã tồn tại trong tòa nhà này!");
        }

        Room room = roomMapper.toEntity(request);
        room.setStatus(RoomStatus.AVAILABLE);
        room.setProperty(property);

        // Tăng tổng số lượng phòng tự động của tòa nhà lên 1
        property.setTotalRooms(property.getTotalRooms() + 1);
        propertyRepository.save(property);

        return roomMapper.toResponse(roomRepository.save(room));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RoomResponse> getRoomsByProperty(UUID propertyId, Pageable pageable) {
        // Kiểm tra xem tòa nhà có tồn tại không
        if (!propertyRepository.existsById(propertyId)) {
            throw new RuntimeException("Không tìm thấy Bất động sản!");
        }
        return roomRepository.findAllByPropertyId(propertyId, pageable).map(roomMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public RoomResponse getRoomDetail(UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin phòng trọ này!"));
        return roomMapper.toResponse(room);
    }

    @Override
    @Transactional
    public RoomResponse updateRoom(UUID roomId, RoomRequest request, UUID managerId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng trọ cần cập nhật!"));

        Property property = room.getProperty();
        checkPermissionByZoneTree(managerId, property.getZone());

        if (property.isWholeHouse()) {
            // Đối với nhà nguyên căn, không cho đổi số phòng bậy bạ
            request.setRoomNumber("Nguyên Căn");
        } else {
            if (roomRepository.existsByRoomNumberAndPropertyIdAndIdNot(request.getRoomNumber(), property.getId(), roomId)) {
                throw new RuntimeException("Số phòng mới bị trùng với một phòng khác sẵn có trong tòa nhà!");
            }
        }

        roomMapper.updateEntityFromRequest(request, room);
        return roomMapper.toResponse(roomRepository.save(room));
    }

    @Override
    @Transactional
    public void deleteRoom(UUID roomId, UUID managerId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin phòng trọ!"));

        Property property = room.getProperty();
        checkPermissionByZoneTree(managerId, property.getZone());

        if (property.isWholeHouse()) {
            throw new RuntimeException("Không thể xóa phòng của hình thức nhà Nguyên Căn! (Bắt buộc phải xóa toàn bộ Property)");
        }

        if (room.getStatus() == RoomStatus.RENTED) {
            throw new RuntimeException("Phòng này đang có khách thuê, không được phép xóa khỏi hệ thống!");
        }

        // Trừ bớt số phòng tổng của tòa nhà đi 1
        property.setTotalRooms(Math.max(0, property.getTotalRooms() - 1));
        propertyRepository.save(property);

        roomRepository.delete(room);
    }

    private void checkPermissionByZoneTree(UUID managerId, Zone targetZone) {
        var user = userRepository.findById(managerId)
                .orElseThrow(() -> new AccessDeniedException("Tài khoản không tồn tại!"));

        if ("ROLE_ADMIN".equals(user.getRole().name())) {
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
            throw new AccessDeniedException("Bạn không có quyền quản lý khu vực địa lý chứa phòng này! [403]");
        }
    }
}