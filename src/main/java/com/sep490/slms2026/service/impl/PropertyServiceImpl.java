package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.ZoneSummaryProjection;
import com.sep490.slms2026.dto.request.PropertyRequest;
import com.sep490.slms2026.dto.response.PropertyResponse;
import com.sep490.slms2026.entity.*;
import com.sep490.slms2026.enums.RoomStatus;
import com.sep490.slms2026.mapper.PropertyMapper;
import com.sep490.slms2026.repository.OperationManagementRepository;
import com.sep490.slms2026.repository.OwnerRepository;
import com.sep490.slms2026.repository.PropertyRepository;
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
    private final OwnerRepository ownerRepository;
    private final PropertyRepository propertyRepository;
    private final PropertyMapper propertyMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ZoneSummaryProjection> getManagerDashboard(UUID managerId) {
        return zoneRepository.getZoneSummaryByManager(managerId);
    }

    @Override
    @Transactional
    public PropertyResponse createProperty(PropertyRequest request) {
        // 🚀 BƯỚC 1 CHECK PHÂN QUYỀN CỦA MANAGER ĐÃ ĐƯỢC XÓA BỎ VÌ ĐÂY LÀ ADMIN TẠO

        // 2. Tìm kiếm Zone xem có tồn tại không, không thấy thì quăng lỗi
        Zone zone = zoneRepository.findById(request.getZoneId())
                .orElseThrow(() -> new RuntimeException("Zone không tồn tại"));

        // 3. Map từ DTO Request sang Entity bằng MapStruct
        Property property = propertyMapper.toEntity(request);
        property.setZone(zone);

        // 4. Xử lý Business Logic dựa theo hình thức thuê (isWholeHouse)
        if (request.isWholeHouse()) {
            if (request.getRooms() == null || request.getRooms().isEmpty()) {
                throw new RuntimeException("Vui lòng nhập đầy đủ thông tin giá thuê và diện tích cho nhà nguyên căn!");
            }

            // Thuê nguyên căn: Tự sinh ra 1 Room đại diện duy nhất
            Room wholeRoom = new Room();
            wholeRoom.setRoomNumber("Nguyên Căn");
            wholeRoom.setPrice(request.getRooms().get(0).getPrice());
            wholeRoom.setDeposit(request.getRooms().get(0).getDeposit());
            wholeRoom.setArea(request.getRooms().get(0).getArea());
            wholeRoom.setStatus(RoomStatus.AVAILABLE);
            wholeRoom.setProperty(property);

            property.getRooms().add(wholeRoom);
            property.setTotalRooms(1);
        } else {
            // Thuê theo phòng: Duyệt qua danh sách và nạp toàn bộ list phòng con từ request vào
            if (request.getRooms() != null) {
                request.getRooms().forEach(roomReq -> {
                    Room room = new Room();
                    room.setRoomNumber(roomReq.getRoomNumber());
                    room.setPrice(roomReq.getPrice());
                    room.setDeposit(roomReq.getDeposit());
                    room.setArea(roomReq.getArea());
                    room.setStatus(RoomStatus.AVAILABLE);
                    room.setProperty(property);

                    property.getRooms().add(room);
                });
                property.setTotalRooms(request.getRooms().size());
            }
        }

        // 5. Lưu vào Database và map kết quả trả về DTO Response
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

        // Kiểm tra xem Manager cũ có quyền quản lý vùng này không, và vùng mới định đổi có hợp lệ không
        check(managerId, property.getZone().getId());
        check(managerId, request.getZoneId());

        Zone zone = zoneRepository.findById(request.getZoneId())
                .orElseThrow(() -> new RuntimeException("Zone không tồn tại"));

        propertyMapper.updateEntityFromRequest(request, property);
        property.setZone(zone);

        // Đổi cấu trúc phòng: Xóa sạch room cũ để orphanRemoval tự kích hoạt kích hoạt xóa ngầm dưới DB
        property.getRooms().clear();

        // Nạp lại cấu trúc phòng mới tương tự như hàm Create
        if (request.isWholeHouse()) {
            if (request.getRooms() == null || request.getRooms().isEmpty()) {
                throw new RuntimeException("Vui lòng nhập đầy đủ thông tin giá thuê và diện tích cho nhà nguyên căn!");
            }
            Room wholeRoom = new Room();
            wholeRoom.setRoomNumber("Nguyên Căn");
            wholeRoom.setPrice(request.getRooms().get(0).getPrice());
            wholeRoom.setDeposit(request.getRooms().get(0).getDeposit());
            wholeRoom.setArea(request.getRooms().get(0).getArea());
            wholeRoom.setStatus(RoomStatus.AVAILABLE);
            wholeRoom.setProperty(property);
            property.getRooms().add(wholeRoom);
            property.setTotalRooms(1);
        } else {
            if (request.getRooms() != null) {
                request.getRooms().forEach(roomReq -> {
                    Room room = new Room();
                    room.setRoomNumber(roomReq.getRoomNumber());
                    room.setPrice(roomReq.getPrice());
                    room.setDeposit(roomReq.getDeposit());
                    room.setArea(roomReq.getArea());
                    room.setStatus(RoomStatus.AVAILABLE);
                    room.setProperty(property);
                    property.getRooms().add(room);
                });
                property.setTotalRooms(request.getRooms().size());
            }
        }

        return propertyMapper.toResponse(propertyRepository.save(property));
    }

    @Override
    @Transactional
    public void deleteProperty(UUID id, UUID managerId) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Bất động sản này"));

        check(managerId, property.getZone().getId());
        propertyRepository.delete(property);
    }

    private void check(UUID managerId, UUID zoneId) {
        OperationManagement op = operationManagementRepository.findById(managerId)
                .orElseThrow(() -> new AccessDeniedException("Tài khoản không có quyền quản lý vận hành!"));

        boolean hasPermission = op.getZones().stream()
                .anyMatch(zone -> zone.getId().equals(zoneId));

        if (!hasPermission) {
            throw new AccessDeniedException("Bồ không có quyền thao tác trên Khu Vực (Zone) này! [403]");
        }
    }
}
