package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.AddRenovationRequest;
import com.sep490.slms2026.dto.response.RenovationResponse;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Renovation;
import com.sep490.slms2026.entity.Room;
import com.sep490.slms2026.enums.PropertyStatus;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.RenovationRepository;
import com.sep490.slms2026.repository.RoomRepository;
import com.sep490.slms2026.service.RenovationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RenovationServiceImpl implements RenovationService {

    private final RenovationRepository renovationRepository;
    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;

    @Override
    @Transactional
    public RenovationResponse addRenovation(Long propertyId, AddRenovationRequest request) {
        Property property = findDraftProperty(propertyId);
        Room room = resolveRoom(property, propertyId, request.getRoomId());

        Renovation renovation = Renovation.builder()
                .property(property)
                .room(room)
                .description(request.getDescription())
                .cost(request.getCost())
                .completed(Boolean.TRUE.equals(request.getCompleted()))
                .build();

        Renovation saved = renovationRepository.save(renovation);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RenovationResponse> getRenovationsByProperty(Long propertyId) {
        if (!propertyRepository.existsById(propertyId)) {
            throw new ResourceNotFoundException("Không tìm thấy tòa nhà với ID: " + propertyId);
        }
        return renovationRepository.findByPropertyId(propertyId)
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
                    "Chỉ có thể thêm hạng mục cải tạo khi tòa nhà đang ở trạng thái DRAFT");
        }
        return property;
    }

    private Room resolveRoom(Property property, Long propertyId, Long roomId) {
        if (Boolean.TRUE.equals(property.getWholeHouse())) {
            if (roomId != null) {
                throw new BusinessException(
                        "Nhà nguyên căn không gắn cải tạo theo phòng — chỉ thêm ở cấp tòa nhà");
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

    private RenovationResponse toResponse(Renovation renovation) {
        return RenovationResponse.builder()
                .id(renovation.getId())
                .propertyId(renovation.getProperty().getId())
                .roomId(renovation.getRoom() != null ? renovation.getRoom().getId() : null)
                .description(renovation.getDescription())
                .cost(renovation.getCost())
                .completed(renovation.isCompleted())
                .build();
    }
}
