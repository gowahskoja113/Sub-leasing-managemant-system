package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.AddRoomRequest;
import com.sep490.slms2026.dto.response.RoomResponse;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Room;
import com.sep490.slms2026.enums.PropertyStatus;
import com.sep490.slms2026.enums.RoomStatus;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.mapper.RoomMapper;
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
    private final RoomMapper roomMapper;

    @Override
    @Transactional
    public RoomResponse addRoom(Long propertyId, AddRoomRequest request) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy tòa nhà với ID: " + propertyId));

        if (property.getStatus() != PropertyStatus.DRAFT) {
            throw new BusinessException(
                    "Chỉ có thể thêm phòng khi tòa nhà đang ở trạng thái DRAFT");
        }

        if (property.getWholeHouse() == null) {
            throw new BusinessException("Phải chọn loại hình thuê (onboarding-options) trước khi thêm phòng");
        }
        if (Boolean.TRUE.equals(property.getWholeHouse())) {
            throw new BusinessException(
                    "Nhà nguyên căn không thêm phòng riêng lẻ — giá được quản lý ở cấp tòa nhà");
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
}
