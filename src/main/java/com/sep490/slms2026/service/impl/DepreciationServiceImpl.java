package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.CalculateDepreciationRequest;
import com.sep490.slms2026.dto.response.DepreciationCalculationResponse;
import com.sep490.slms2026.dto.response.DepreciationResultResponse;
import com.sep490.slms2026.entity.DepreciationResult;
import com.sep490.slms2026.entity.InboundContract;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Room;
import com.sep490.slms2026.enums.EquipmentSource;
import com.sep490.slms2026.enums.PricingScope;
import com.sep490.slms2026.enums.PropertyStatus;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.DepreciationResultRepository;
import com.sep490.slms2026.repository.EquipmentRepository;
import com.sep490.slms2026.repository.InboundContractRepository;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.RenovationRepository;
import com.sep490.slms2026.repository.RoomRepository;
import com.sep490.slms2026.service.DepreciationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DepreciationServiceImpl implements DepreciationService {

    private static final BigDecimal PROFIT_MARGIN = new BigDecimal("1.10");

    private final DepreciationResultRepository depreciationResultRepository;
    private final InboundContractRepository inboundContractRepository;
    private final PropertyRepository propertyRepository;
    private final RenovationRepository renovationRepository;
    private final EquipmentRepository equipmentRepository;
    private final RoomRepository roomRepository;

    @Override
    @Transactional
    public DepreciationCalculationResponse calculate(Long propertyId, CalculateDepreciationRequest request) {
        Property property = loadDraftProperty(propertyId);
        InboundContract contract = loadContract(propertyId);

        depreciationResultRepository.deleteByPropertyId(propertyId);

        if (Boolean.TRUE.equals(property.getWholeHouse())) {
            return calculateWholeHouse(property, contract);
        }
        return calculatePerRoom(property, contract);
    }

    @Override
    @Transactional(readOnly = true)
    public DepreciationCalculationResponse getByProperty(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy tòa nhà với ID: " + propertyId));

        if (Boolean.TRUE.equals(property.getWholeHouse())) {
            DepreciationResult result = depreciationResultRepository.findWholeHouseByPropertyId(propertyId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Chưa có kết quả khấu hao cho nhà nguyên căn ID: " + propertyId));
            return DepreciationCalculationResponse.builder()
                    .propertyId(propertyId)
                    .pricingScope(PricingScope.WHOLE_HOUSE)
                    .wholeHouseResult(toResponse(result, PricingScope.WHOLE_HOUSE))
                    .build();
        }

        List<DepreciationResultResponse> roomResults = depreciationResultRepository
                .findAllRoomLevelByPropertyId(propertyId)
                .stream()
                .map(r -> toResponse(r, PricingScope.ROOM))
                .toList();

        if (roomResults.isEmpty()) {
            throw new ResourceNotFoundException(
                    "Chưa có kết quả khấu hao theo phòng cho tòa nhà ID: " + propertyId);
        }

        return DepreciationCalculationResponse.builder()
                .propertyId(propertyId)
                .pricingScope(PricingScope.ROOM)
                .roomResults(roomResults)
                .build();
    }

    private DepreciationCalculationResponse calculateWholeHouse(Property property, InboundContract contract) {
        Long propertyId = property.getId();
        validateNoRoomLevelCosts(propertyId);
        validatePurchasedEquipmentHasPrice(propertyId);
        validateRenovationHasCost(propertyId);

        BigDecimal totalRenovationCost = renovationRepository.sumCostByPropertyIdAndRoomIsNull(propertyId);
        BigDecimal totalEquipmentCost = equipmentRepository
                .sumPurchasePriceByPropertyIdAndRoomIsNullAndSource(propertyId, EquipmentSource.PURCHASED);
        BigDecimal totalRentAmount = contract.getTotalRentAmount();

        PricingBreakdown breakdown = computePricing(
                totalRentAmount, totalRenovationCost, totalEquipmentCost, resolveContractMonths(contract));

        DepreciationResult saved = depreciationResultRepository.save(buildResult(
                contract, null, totalRenovationCost, totalEquipmentCost, totalRentAmount, breakdown));

        return DepreciationCalculationResponse.builder()
                .propertyId(propertyId)
                .pricingScope(PricingScope.WHOLE_HOUSE)
                .wholeHouseResult(toResponse(saved, PricingScope.WHOLE_HOUSE))
                .build();
    }

    private DepreciationCalculationResponse calculatePerRoom(Property property, InboundContract contract) {
        Long propertyId = property.getId();
        List<Room> rooms = roomRepository.findByPropertyId(propertyId);
        if (rooms.isEmpty()) {
            throw new BusinessException("Phải có ít nhất một phòng trước khi tính khấu hao theo phòng");
        }

        validatePurchasedEquipmentHasPrice(propertyId);
        validateRenovationHasCost(propertyId);

        double totalArea = rooms.stream().mapToDouble(Room::getArea).sum();
        if (totalArea <= 0) {
            throw new BusinessException("Tổng diện tích các phòng phải lớn hơn 0");
        }

        BigDecimal sharedRenovation = renovationRepository.sumCostByPropertyIdAndRoomIsNull(propertyId);
        BigDecimal sharedEquipment = equipmentRepository
                .sumPurchasePriceByPropertyIdAndRoomIsNullAndSource(propertyId, EquipmentSource.PURCHASED);
        int contractMonths = resolveContractMonths(contract);

        List<DepreciationResultResponse> roomResults = new ArrayList<>();

        for (Room room : rooms) {
            BigDecimal areaRatio = BigDecimal.valueOf(room.getArea() / totalArea)
                    .setScale(6, RoundingMode.HALF_UP);

            BigDecimal roomRenovation = renovationRepository.sumCostByRoomId(room.getId());
            BigDecimal roomEquipment = equipmentRepository
                    .sumPurchasePriceByRoomIdAndSource(room.getId(), EquipmentSource.PURCHASED);

            BigDecimal allocatedSharedRenovation = sharedRenovation.multiply(areaRatio)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal allocatedSharedEquipment = sharedEquipment.multiply(areaRatio)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal allocatedTotalRent = contract.getTotalRentAmount().multiply(areaRatio)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal totalRenovationCost = roomRenovation.add(allocatedSharedRenovation);
            BigDecimal totalEquipmentCost = roomEquipment.add(allocatedSharedEquipment);

            PricingBreakdown breakdown = computePricing(
                    allocatedTotalRent, totalRenovationCost, totalEquipmentCost, contractMonths);

            DepreciationResult saved = depreciationResultRepository.save(buildResult(
                    contract, room, totalRenovationCost, totalEquipmentCost, allocatedTotalRent, breakdown));

            roomResults.add(toResponse(saved, PricingScope.ROOM));
        }

        return DepreciationCalculationResponse.builder()
                .propertyId(propertyId)
                .pricingScope(PricingScope.ROOM)
                .roomResults(roomResults)
                .build();
    }

    private PricingBreakdown computePricing(BigDecimal totalRentAmount,
                                            BigDecimal totalRenovationCost,
                                            BigDecimal totalEquipmentCost,
                                            int contractMonths) {
        BigDecimal totalInvestment = totalRentAmount.add(totalRenovationCost).add(totalEquipmentCost);
        BigDecimal monthlyBreakEven = totalInvestment
                .divide(BigDecimal.valueOf(contractMonths), 2, RoundingMode.HALF_UP);
        BigDecimal suggestedPriceWithProfit = monthlyBreakEven.multiply(PROFIT_MARGIN)
                .setScale(2, RoundingMode.HALF_UP);

        return new PricingBreakdown(
                totalInvestment, contractMonths, monthlyBreakEven, monthlyBreakEven, suggestedPriceWithProfit);
    }

    private DepreciationResult buildResult(InboundContract contract,
                                           Room room,
                                           BigDecimal totalRenovationCost,
                                           BigDecimal totalEquipmentCost,
                                           BigDecimal totalRentAmount,
                                           PricingBreakdown breakdown) {
        return DepreciationResult.builder()
                .inboundContract(contract)
                .room(room)
                .totalRenovationCost(totalRenovationCost)
                .totalEquipmentCost(totalEquipmentCost)
                .totalRentAmount(totalRentAmount)
                .totalInvestment(breakdown.totalInvestment())
                .contractMonths(breakdown.contractMonths())
                .monthlyDepreciation(breakdown.monthlyBreakEven())
                .suggestedMinPrice(breakdown.suggestedMinPrice())
                .suggestedPriceWithProfit(breakdown.suggestedPriceWithProfit())
                .calculatedAt(LocalDateTime.now())
                .build();
    }

    private Property loadDraftProperty(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy tòa nhà với ID: " + propertyId));
        if (property.getStatus() != PropertyStatus.DRAFT) {
            throw new BusinessException("Chỉ tính khấu hao khi tòa nhà đang ở trạng thái DRAFT");
        }
        return property;
    }

    private InboundContract loadContract(Long propertyId) {
        return inboundContractRepository.findByPropertyId(propertyId)
                .orElseThrow(() -> new BusinessException(
                        "Phải ký hợp đồng inbound trước khi tính khấu hao"));
    }

    private void validateNoRoomLevelCosts(Long propertyId) {
        if (equipmentRepository.existsByPropertyIdAndRoomIsNotNull(propertyId)) {
            throw new BusinessException(
                    "Nhà nguyên căn không được gắn thiết bị theo phòng — chỉ thêm ở cấp tòa nhà");
        }
        if (renovationRepository.existsByPropertyIdAndRoomIsNotNull(propertyId)) {
            throw new BusinessException(
                    "Nhà nguyên căn không được gắn cải tạo theo phòng — chỉ thêm ở cấp tòa nhà");
        }
    }

    private void validatePurchasedEquipmentHasPrice(Long propertyId) {
        boolean missingPrice = equipmentRepository.findByPropertyId(propertyId).stream()
                .filter(e -> e.getSource() == EquipmentSource.PURCHASED)
                .anyMatch(e -> e.getPurchasePrice() == null);
        if (missingPrice) {
            throw new BusinessException(
                    "Thiết bị PURCHASED phải có purchasePrice trước khi tính khấu hao");
        }
    }

    private void validateRenovationHasCost(Long propertyId) {
        boolean missingCost = renovationRepository.findByPropertyId(propertyId).stream()
                .anyMatch(r -> r.getCost() == null);
        if (missingCost) {
            throw new BusinessException("Cải tạo phải có cost trước khi tính khấu hao");
        }
    }

    private int resolveContractMonths(InboundContract contract) {
        long months = ChronoUnit.MONTHS.between(contract.getStartDate(), contract.getEndDate());
        if (months <= 0) {
            throw new BusinessException("Thời hạn hợp đồng phải ít nhất 1 tháng");
        }
        return (int) months;
    }

    private DepreciationResultResponse toResponse(DepreciationResult result, PricingScope scope) {
        DepreciationResultResponse.DepreciationResultResponseBuilder builder = DepreciationResultResponse.builder()
                .id(result.getId())
                .propertyId(result.getInboundContract().getProperty().getId())
                .inboundContractId(result.getInboundContract().getId())
                .pricingScope(scope)
                .totalRenovationCost(result.getTotalRenovationCost())
                .totalEquipmentCost(result.getTotalEquipmentCost())
                .totalRentAmount(result.getTotalRentAmount())
                .totalInvestment(result.getTotalInvestment())
                .contractMonths(result.getContractMonths())
                .monthlyBreakEven(result.getMonthlyDepreciation())
                .suggestedMinPrice(result.getSuggestedMinPrice())
                .suggestedPriceWithProfit(result.getSuggestedPriceWithProfit())
                .calculatedAt(result.getCalculatedAt());

        if (result.getRoom() != null) {
            builder.roomId(result.getRoom().getId())
                    .roomNumber(result.getRoom().getRoomNumber());
        }
        return builder.build();
    }

    private record PricingBreakdown(
            BigDecimal totalInvestment,
            int contractMonths,
            BigDecimal monthlyBreakEven,
            BigDecimal suggestedMinPrice,
            BigDecimal suggestedPriceWithProfit) {
    }
}
