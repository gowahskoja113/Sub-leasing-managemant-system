package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.AddRoomRequest;
import com.sep490.slms2026.dto.request.UpdateRoomRequest;
import com.sep490.slms2026.dto.request.UpdateRoomStatusRequest;
import com.sep490.slms2026.dto.response.RoomResponse;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Room;
import com.sep490.slms2026.enums.PropertyStatus;
import com.sep490.slms2026.enums.RoomStatus;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.mapper.RoomMapper;
import com.sep490.slms2026.repository.DepreciationResultRepository;
import com.sep490.slms2026.repository.EquipmentRepository;
import com.sep490.slms2026.repository.MonthlyReadingRepository;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.RoomRepository;
import com.sep490.slms2026.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService {

    private final RoomRepository roomRepository;
    private final PropertyRepository propertyRepository;
    private final EquipmentRepository equipmentRepository;
    private final DepreciationResultRepository depreciationResultRepository;
    private final MonthlyReadingRepository monthlyReadingRepository;
    private final RoomMapper roomMapper;

    @Override
    @Transactional
    public RoomResponse addRoom(Long propertyId, AddRoomRequest request) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy tòa nhà với ID: " + propertyId));

        if (property.getStatus() != PropertyStatus.DRAFT && property.getStatus() != PropertyStatus.UNDER_RENOVATION) {
            throw new BusinessException(
                    "Chỉ có thể thêm phòng khi tòa nhà đang ở trạng thái DRAFT hoặc UNDER_RENOVATION");
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

        long currentCount = roomRepository.countByPropertyId(propertyId);
        if (property.getTotalRooms() != null && currentCount >= property.getTotalRooms()) {
            throw new BusinessException(
                    "Tòa nhà đã đủ " + property.getTotalRooms() + " phòng theo khai báo ban đầu");
        }

        if (roomRepository.existsByPropertyIdAndRoomNumber(propertyId, request.getRoomNumber())) {
            throw new BusinessException(
                    "Số phòng '" + request.getRoomNumber() + "' đã tồn tại trong tòa nhà này");
        }

        Room room = roomMapper.toEntity(request);
        room.setProperty(property);
        room.setStatus(property.getStatus() == PropertyStatus.UNDER_RENOVATION
                ? RoomStatus.AVAILABLE
                : RoomStatus.DRAFT);

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

        return roomRepository.findByPropertyIdWithProperty(propertyId)
                .stream()
                .map(roomMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RoomResponse getRoomById(Long propertyId, Long roomId) {
        Room room = roomRepository.findByIdAndPropertyId(roomId, propertyId)
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
        if (propertyStatus != PropertyStatus.DRAFT && propertyStatus != PropertyStatus.ACTIVE) {
            throw new BusinessException(
                    "Chỉ cập nhật trạng thái phòng khi tòa nhà đang DRAFT hoặc ACTIVE");
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
        if (room.getStatus() == RoomStatus.DRAFT && propertyStatus != PropertyStatus.DRAFT) {
            throw new BusinessException(
                    "Phòng " + room.getRoomNumber() + " chưa kích hoạt — không thể cập nhật trạng thái");
        }

        RoomStatus newStatus = request.getStatus();
        if (newStatus != RoomStatus.AVAILABLE && newStatus != RoomStatus.MAINTENANCE) {
            throw new BusinessException(
                    "Chỉ cho phép cập nhật trạng thái AVAILABLE hoặc MAINTENANCE");
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

        if (roomRepository.existsByPropertyIdAndRoomNumberAndIdNot(
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
        Room room = loadRoom(propertyId, roomId);

        if (room.getStatus() == RoomStatus.RENTED) {
            throw new BusinessException(
                    "Phòng " + room.getRoomNumber() + " đang cho thuê — không thể xóa");
        }
        if (equipmentRepository.countByRoomId(roomId) > 0) {
            throw new BusinessException(
                    "Phòng " + room.getRoomNumber() + " còn thiết bị được gán — không thể xóa");
        }
        if (monthlyReadingRepository.existsByRoomId(roomId)) {
            throw new BusinessException(
                    "Phòng " + room.getRoomNumber() + " đã có lịch sử chỉ số điện nước — không thể xóa");
        }

        depreciationResultRepository.findByRoomId(roomId)
                .ifPresent(depreciationResultRepository::delete);

        roomRepository.delete(room);
        log.info("Đã xóa phòng {} (ID={}) khỏi tòa nhà ID={}", room.getRoomNumber(), roomId, propertyId);
    }

    private Property loadPropertyForRoomManagement(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy tòa nhà với ID: " + propertyId));

        PropertyStatus status = property.getStatus();
        if (status != PropertyStatus.DRAFT && status != PropertyStatus.UNDER_RENOVATION) {
            throw new BusinessException(
                    "Chỉ sửa/xóa phòng khi tòa nhà đang DRAFT hoặc UNDER_RENOVATION");
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
}
