package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.CalculateDepreciationRequest;
import com.sep490.slms2026.dto.response.DepreciationCalculationResponse;
import com.sep490.slms2026.dto.response.DepreciationResultResponse;
import com.sep490.slms2026.entity.DepreciationResult;
import com.sep490.slms2026.entity.InboundContract;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Room;
import com.sep490.slms2026.enums.PricingScope;
import com.sep490.slms2026.enums.PropertyStatus;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.DepreciationResultRepository;
import com.sep490.slms2026.repository.InboundContractRepository;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.RenovationLineRepository;
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

    private final DepreciationResultRepository depreciationResultRepository;
    private final InboundContractRepository inboundContractRepository;
    private final PropertyRepository propertyRepository;
    private final RenovationLineRepository renovationLineRepository;
    private final RoomRepository roomRepository;
    private final com.sep490.slms2026.repository.EquipmentRepository equipmentRepository;

    @Override
    @Transactional
    public DepreciationCalculationResponse calculate(Long propertyId, CalculateDepreciationRequest request) {
        Property property = loadDraftProperty(propertyId);
        InboundContract contract = loadContract(propertyId);

        depreciationResultRepository.deleteByPropertyId(propertyId);

        BigDecimal totalRenovationCost = renovationLineRepository.sumCostByPropertyId(propertyId);
        BigDecimal totalEquipmentCost = equipmentRepository.sumPurchasedEquipmentCostByPropertyId(propertyId);
        int contractMonths = resolveContractMonths(contract);
        BigDecimal totalRentAmount = contract.getTotalRentAmount();

        if (Boolean.TRUE.equals(property.getWholeHouse())) {
            return calculateWholeHouse(property, contract, totalRentAmount, totalRenovationCost, totalEquipmentCost, contractMonths);
        }
        return calculatePerRoom(property, contract, totalRentAmount, totalRenovationCost, totalEquipmentCost, contractMonths);
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
                            "Chưa có kết quả tính giá cho nhà nguyên căn ID: " + propertyId));
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
                    "Chưa có kết quả tính giá theo phòng cho tòa nhà ID: " + propertyId);
        }

        return DepreciationCalculationResponse.builder()
                .propertyId(propertyId)
                .pricingScope(PricingScope.ROOM)
                .roomResults(roomResults)
                .build();
    }

    private DepreciationCalculationResponse calculateWholeHouse(Property property,
                                                                InboundContract contract,
                                                                BigDecimal totalRentAmount,
                                                                BigDecimal totalRenovationCost,
                                                                BigDecimal totalEquipmentCost,
                                                                int contractMonths) {
        PricingBreakdown breakdown = computePricing(totalRentAmount, totalRenovationCost, totalEquipmentCost, contractMonths);

        DepreciationResult saved = depreciationResultRepository.save(buildResult(
                contract, null, totalRenovationCost, totalEquipmentCost, totalRentAmount, breakdown));

        return DepreciationCalculationResponse.builder()
                .propertyId(property.getId())
                .pricingScope(PricingScope.WHOLE_HOUSE)
                .wholeHouseResult(toResponse(saved, PricingScope.WHOLE_HOUSE))
                .build();
    }

    private DepreciationCalculationResponse calculatePerRoom(Property property,
                                                             InboundContract contract,
                                                             BigDecimal totalRentAmount,
                                                             BigDecimal totalRenovationCost,
                                                             BigDecimal totalEquipmentCost,
                                                             int contractMonths) {
        List<Room> rooms = roomRepository.findByPropertyIdAndDeletedIsFalse(property.getId());
        if (rooms.isEmpty()) {
            throw new BusinessException("Phải có ít nhất một phòng trước khi tính giá theo phòng");
        }

        PricingBreakdown wholeBreakdown = computePricing(totalRentAmount, totalRenovationCost, totalEquipmentCost, contractMonths);
        BigDecimal perRoomMonthly = wholeBreakdown.suggestedMinPrice()
                .divide(BigDecimal.valueOf(rooms.size()), 2, RoundingMode.HALF_UP);

        List<DepreciationResultResponse> roomResults = new ArrayList<>();

        for (Room room : rooms) {
            PricingBreakdown roomBreakdown = new PricingBreakdown(
                    wholeBreakdown.totalInvestment(),
                    contractMonths,
                    perRoomMonthly,
                    perRoomMonthly);

            DepreciationResult saved = depreciationResultRepository.save(buildResult(
                    contract, room, totalRenovationCost, totalEquipmentCost, totalRentAmount, roomBreakdown));

            roomResults.add(toResponse(saved, PricingScope.ROOM));
        }

        return DepreciationCalculationResponse.builder()
                .propertyId(property.getId())
                .pricingScope(PricingScope.ROOM)
                .roomResults(roomResults)
                .build();
    }

    private PricingBreakdown computePricing(BigDecimal totalRentAmount,
                                            BigDecimal totalRenovationCost,
                                            BigDecimal totalEquipmentCost,
                                            int contractMonths) {
        BigDecimal totalInvestment = totalRentAmount.add(totalRenovationCost).add(totalEquipmentCost);
        BigDecimal monthlySuggested = totalInvestment
                .divide(BigDecimal.valueOf(contractMonths), 2, RoundingMode.HALF_UP);

        return new PricingBreakdown(totalInvestment, contractMonths, monthlySuggested, monthlySuggested);
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
                .monthlyDepreciation(breakdown.monthlySuggested())
                .suggestedMinPrice(breakdown.suggestedMinPrice())
                .suggestedPriceWithProfit(breakdown.suggestedMinPrice())
                .calculatedAt(LocalDateTime.now())
                .build();
    }

    private Property loadDraftProperty(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy tòa nhà với ID: " + propertyId));
        if (!property.getStatus().isOnboardingEditable()
                && property.getStatus() != PropertyStatus.PENDING_HOST_REVIEW) {
            throw new BusinessException("Chỉ tính giá khi tòa nhà đang trong quá trình onboarding");
        }
        return property;
    }

    private InboundContract loadContract(Long propertyId) {
        return inboundContractRepository.findByPropertyId(propertyId)
                .orElseThrow(() -> new BusinessException(
                        "Phải ký hợp đồng inbound trước khi tính giá"));
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
            BigDecimal monthlySuggested,
            BigDecimal suggestedMinPrice) {
    }
}
