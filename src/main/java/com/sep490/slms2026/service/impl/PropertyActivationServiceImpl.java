package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.ConfirmPropertyActivationRequest;
import com.sep490.slms2026.dto.request.ConfirmPropertyActivationRequest.RoomPriceConfirm;
import com.sep490.slms2026.dto.response.PropertyActivationResponse;
import com.sep490.slms2026.dto.response.PropertyActivationResponse.ActivatedRoom;
import com.sep490.slms2026.entity.DepreciationResult;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Room;
import com.sep490.slms2026.enums.PricingScope;
import com.sep490.slms2026.enums.PropertyStatus;
import com.sep490.slms2026.enums.RoomStatus;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.DepreciationResultRepository;
import com.sep490.slms2026.repository.InboundContractRepository;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.RenovationRepository;
import com.sep490.slms2026.repository.RoomRepository;
import com.sep490.slms2026.service.PropertyActivationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PropertyActivationServiceImpl implements PropertyActivationService {

    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final DepreciationResultRepository depreciationResultRepository;
    private final InboundContractRepository inboundContractRepository;
    private final RenovationRepository renovationRepository;

    @Override
    @Transactional
    public PropertyActivationResponse confirmActivation(Long propertyId,
                                                      ConfirmPropertyActivationRequest request) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy tòa nhà với ID: " + propertyId));

        if (property.getStatus() != PropertyStatus.DRAFT) {
            throw new BusinessException("Chỉ có thể confirm giá khi tòa nhà đang ở trạng thái DRAFT");
        }

        if (!inboundContractRepository.existsByPropertyId(propertyId)) {
            throw new BusinessException("Phải ký hợp đồng inbound trước khi confirm giá");
        }

        boolean ongoingRenovation = Boolean.TRUE.equals(request.getHasOngoingRenovation())
                || renovationRepository.existsByPropertyIdAndCompletedFalse(propertyId);

        if (Boolean.TRUE.equals(property.getWholeHouse())) {
            return confirmWholeHouse(property, propertyId, request, ongoingRenovation);
        }
        return confirmRooms(property, propertyId, request, ongoingRenovation);
    }

    private PropertyActivationResponse confirmWholeHouse(Property property,
                                                         Long propertyId,
                                                         ConfirmPropertyActivationRequest request,
                                                         boolean ongoingRenovation) {
        if (request.getPropertyPrice() == null || request.getPropertyDeposit() == null) {
            throw new BusinessException(
                    "Nhà nguyên căn phải gửi propertyPrice và propertyDeposit khi confirm giá");
        }
        if (request.getRoomPrices() != null && !request.getRoomPrices().isEmpty()) {
            throw new BusinessException(
                    "Nhà nguyên căn không confirm giá theo phòng — dùng propertyPrice/propertyDeposit");
        }

        DepreciationResult depreciation = depreciationResultRepository.findWholeHouseByPropertyId(propertyId)
                .orElseThrow(() -> new BusinessException(
                        "Phải tính khấu hao cấp nhà nguyên căn trước khi confirm giá"));

        if (!ongoingRenovation
                && request.getPropertyPrice().compareTo(depreciation.getSuggestedMinPrice()) < 0) {
            throw new BusinessException(
                    "Giá thuê xác nhận (" + request.getPropertyPrice()
                            + ") thấp hơn giá tối thiểu gợi ý ("
                            + depreciation.getSuggestedMinPrice() + ")");
        }

        property.setPrice(request.getPropertyPrice());
        property.setDeposit(request.getPropertyDeposit());
        property.setStatus(ongoingRenovation ? PropertyStatus.MAINTENANCE : PropertyStatus.ACTIVE);
        propertyRepository.save(property);

        return PropertyActivationResponse.builder()
                .propertyId(propertyId)
                .pricingScope(PricingScope.WHOLE_HOUSE)
                .propertyStatus(property.getStatus())
                .propertyPrice(property.getPrice())
                .propertyDeposit(property.getDeposit())
                .suggestedMinPrice(depreciation.getSuggestedMinPrice())
                .build();
    }

    private PropertyActivationResponse confirmRooms(Property property,
                                                    Long propertyId,
                                                    ConfirmPropertyActivationRequest request,
                                                    boolean ongoingRenovation) {
        if (request.getPropertyPrice() != null || request.getPropertyDeposit() != null) {
            throw new BusinessException(
                    "Nhà chia phòng không confirm giá ở cấp tòa — dùng roomPrices");
        }
        if (request.getRoomPrices() == null || request.getRoomPrices().isEmpty()) {
            throw new BusinessException("Phải gửi roomPrices khi confirm giá nhà chia phòng");
        }

        List<Room> draftRooms = roomRepository.findByPropertyIdAndStatus(propertyId, RoomStatus.DRAFT);
        if (draftRooms.isEmpty()) {
            throw new BusinessException("Tòa nhà chưa có phòng nào ở trạng thái DRAFT để confirm giá");
        }

        Map<Long, RoomPriceConfirm> priceByRoomId = request.getRoomPrices().stream()
                .collect(Collectors.toMap(RoomPriceConfirm::getRoomId, Function.identity()));

        if (priceByRoomId.size() != draftRooms.size()) {
            throw new BusinessException(
                    "Phải xác nhận giá cho đủ " + draftRooms.size() + " phòng DRAFT");
        }

        List<ActivatedRoom> activatedRooms = new ArrayList<>();

        for (Room room : draftRooms) {
            RoomPriceConfirm priceConfirm = priceByRoomId.get(room.getId());
            if (priceConfirm == null) {
                throw new BusinessException("Thiếu giá xác nhận cho phòng ID=" + room.getId());
            }

            DepreciationResult roomDepreciation = depreciationResultRepository.findByRoomId(room.getId())
                    .orElseThrow(() -> new BusinessException(
                            "Phòng " + room.getRoomNumber() + " chưa có kết quả khấu hao — hãy tính lại"));

            if (!ongoingRenovation
                    && priceConfirm.getPrice().compareTo(roomDepreciation.getSuggestedMinPrice()) < 0) {
                throw new BusinessException(
                        "Phòng " + room.getRoomNumber() + ": giá xác nhận ("
                                + priceConfirm.getPrice() + ") thấp hơn giá tối thiểu gợi ý ("
                                + roomDepreciation.getSuggestedMinPrice() + ")");
            }

            room.setPrice(priceConfirm.getPrice());
            room.setDeposit(priceConfirm.getDeposit());
            room.setStatus(ongoingRenovation ? RoomStatus.MAINTENANCE : RoomStatus.AVAILABLE);

            activatedRooms.add(ActivatedRoom.builder()
                    .roomId(room.getId())
                    .roomNumber(room.getRoomNumber())
                    .price(room.getPrice())
                    .deposit(room.getDeposit())
                    .suggestedMinPrice(roomDepreciation.getSuggestedMinPrice())
                    .status(room.getStatus())
                    .build());
        }

        property.setStatus(ongoingRenovation ? PropertyStatus.MAINTENANCE : PropertyStatus.ACTIVE);
        propertyRepository.save(property);
        roomRepository.saveAll(draftRooms);

        return PropertyActivationResponse.builder()
                .propertyId(propertyId)
                .pricingScope(PricingScope.ROOM)
                .propertyStatus(property.getStatus())
                .rooms(activatedRooms)
                .build();
    }
}
