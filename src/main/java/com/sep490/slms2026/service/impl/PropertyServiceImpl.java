package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.ZoneSummaryProjection;
import com.sep490.slms2026.dto.request.PropertyRequest;
import com.sep490.slms2026.dto.request.RoomRequest;
import com.sep490.slms2026.dto.response.PropertyResponse;
import com.sep490.slms2026.entity.*;
import com.sep490.slms2026.enums.RoomStatus;
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
        Zone propertyZone = zoneRepository.findById(request.getZoneId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Vùng (Zone) đã chọn cho Bất động sản!"));

        if (propertyZone.getLevel() != 3) {
            throw new RuntimeException("Lỗi cấu trúc địa chỉ: Địa chỉ Bất động sản bắt buộc phải chọn chi tiết đến cấp Phường/Xã (Level 3)!");
        }

        checkPermissionByZoneTree(managerId, propertyZone);

        // 1. Map từ DTO sang Entity như bình thường
        Property property = propertyMapper.toEntity(request);
        property.setZone(propertyZone);

        // 🔥 BƯỚC QUAN TRỌNG: Diệt tận gốc mối quan hệ 2 chiều của các Room rác do MapStruct tự map
        if (property.getRooms() != null) {
            property.getRooms().forEach(room -> room.setProperty(null)); // Chặt đứt liên kết ngược
            property.getRooms().clear(); // Xoá sạch list xuôi
        }

        // 2. Xử lý nạp Room sạch dựa theo hình thức thuê (isWholeHouse)
        processRoomsLogic(request, property);

        // 3. Lưu vào Database và map kết quả trả về
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
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Bất động sản này"));

        Zone newZone = zoneRepository.findById(request.getZoneId())
                .orElseThrow(() -> new RuntimeException("Zone mới không tồn tại"));

        if (newZone.getLevel() != 3) {
            throw new RuntimeException("Lỗi cấu trúc địa chỉ: Địa chỉ định đổi bắt buộc phải thuộc cấp Phường/Xã (Level 3)!");
        }

        // Kiểm tra quyền trên cả vùng cũ của BĐS lẫn vùng mới định cập nhật
        checkPermissionByZoneTree(managerId, property.getZone());
        checkPermissionByZoneTree(managerId, newZone);

        propertyMapper.updateEntityFromRequest(request, property);
        property.setZone(newZone);

        if (property.getRooms() != null) {
            property.getRooms().forEach(room -> room.setProperty(null));
            property.getRooms().clear();
        }

        processRoomsLogic(request, property);

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

    /**
     * KIỂM TRA PHÂN QUYỀN THEO CÂY ĐỆ QUY (Zone Tree Verification)
     * Đảm bảo nếu quản lý cấp Tỉnh hoặc Quận vẫn có quyền thao tác trên các Phường con/cháu.
     */
    private void checkPermissionByZoneTree(UUID managerId, Zone targetZone) {
        // Tìm thông tin User để xác định Role
        var user = userRepository.findById(managerId)
                .orElseThrow(() -> new AccessDeniedException("Tài khoản không tồn tại trên hệ thống!"));

        // BƯỚC ĐỆM: Nếu là ADMIN thì luôn luôn có quyền, bypass qua tất cả các tầng check địa lý bên dưới
        if (com.sep490.slms2026.enums.Role.ROLE_ADMIN.equals(user.getRole())) {
            return;
        }

        // Nếu không phải ADMIN (tức là MANAGER), tiến hành check theo cây phân cấp địa lý như cũ
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

    private void processRoomsLogic(PropertyRequest request, Property property) {
        if (request.isWholeHouse()) {
            // Nha nguyen can: chi can price, deposit, area - roomNumber tu dong = "Nguyen Can"
            if (request.getRooms() == null || request.getRooms().isEmpty()) {
                throw new RuntimeException("Vui long nhap gia thue, tien coc va dien tich cho nha nguyen can!");
            }
            RoomRequest wholeRoomReq = request.getRooms().get(0);
            if (wholeRoomReq.getPrice() == null || wholeRoomReq.getDeposit() == null || wholeRoomReq.getArea() == null) {
                throw new RuntimeException("Gia thue, tien coc va dien tich khong duoc de trong!");
            }
            Room wholeRoom = new Room();
            wholeRoom.setRoomNumber("Nguyen Can");
            wholeRoom.setPrice(wholeRoomReq.getPrice());
            wholeRoom.setDeposit(wholeRoomReq.getDeposit());
            wholeRoom.setArea(wholeRoomReq.getArea());
            wholeRoom.setStatus(RoomStatus.AVAILABLE);
            wholeRoom.setProperty(property);
            property.getRooms().add(wholeRoom);
            property.setTotalRooms(1);
        } else {
            // Cho thue phong: bat buoc phai co roomNumber cho tung phong
            if (request.getRooms() == null || request.getRooms().isEmpty()) {
                throw new RuntimeException("Vui long them it nhat 1 phong cho bat dong san cho thue theo phong!");
            }
            for (int i = 0; i < request.getRooms().size(); i++) {
                RoomRequest roomReq = request.getRooms().get(i);
                if (roomReq.getRoomNumber() == null || roomReq.getRoomNumber().isBlank()) {
                    throw new RuntimeException("Phong thu " + (i + 1) + " chua co so phong (roomNumber)!");
                }
                if (roomReq.getPrice() == null || roomReq.getDeposit() == null || roomReq.getArea() == null) {
                    throw new RuntimeException("Phong \"" + roomReq.getRoomNumber() + "\": gia thue, tien coc va dien tich khong duoc de trong!");
                }
                Room room = new Room();
                room.setRoomNumber(roomReq.getRoomNumber());
                room.setPrice(roomReq.getPrice());
                room.setDeposit(roomReq.getDeposit());
                room.setArea(roomReq.getArea());
                room.setStatus(RoomStatus.AVAILABLE);
                room.setProperty(property);
                property.getRooms().add(room);
            }
            property.setTotalRooms(request.getRooms().size());
        }
    }
}