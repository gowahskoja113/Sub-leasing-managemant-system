package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.CalculateDepreciationRequest;
import com.sep490.slms2026.dto.response.DepreciationCalculationResponse;
import com.sep490.slms2026.dto.response.DepreciationResultResponse;
import com.sep490.slms2026.dto.response.PricingReconciliationResponse;
import com.sep490.slms2026.entity.DepreciationResult;
import com.sep490.slms2026.entity.InboundContract;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Room;
import com.sep490.slms2026.enums.PricingMode;
import com.sep490.slms2026.enums.PricingScope;
import com.sep490.slms2026.enums.PropertyStatus;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.DepreciationResultRepository;
import com.sep490.slms2026.repository.EquipmentRepository;
import com.sep490.slms2026.repository.InboundContractRepository;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.RenovationLineRepository;
import com.sep490.slms2026.repository.RoomRepository;
import com.sep490.slms2026.repository.TenantContractRepository;
import com.sep490.slms2026.service.DepreciationService;
import com.sep490.slms2026.service.pricing.PricingCalculator;
import com.sep490.slms2026.service.pricing.PricingCalculator.PropertyResult;
import com.sep490.slms2026.service.pricing.PricingCalculator.RoomInput;
import com.sep490.slms2026.service.pricing.PricingCalculator.RoomResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DepreciationServiceImpl implements DepreciationService {

    private final DepreciationResultRepository depreciationResultRepository;
    private final InboundContractRepository inboundContractRepository;
    private final PropertyRepository propertyRepository;
    private final RenovationLineRepository renovationLineRepository;
    private final RoomRepository roomRepository;
    private final EquipmentRepository equipmentRepository;
    private final TenantContractRepository tenantContractRepository;

    @Override
    @Transactional
    public DepreciationCalculationResponse calculate(Long propertyId, CalculateDepreciationRequest request) {
        Property property = loadDraftProperty(propertyId);
        InboundContract contract = loadContract(propertyId);
        CalculateDepreciationRequest params = normalizeRequest(request);

        depreciationResultRepository.deleteByPropertyId(propertyId);

        BigDecimal totalRenovationCost = renovationLineRepository.sumCostByPropertyId(propertyId);
        BigDecimal totalEquipmentCost = equipmentRepository.sumPurchasedEquipmentCostByPropertyId(propertyId);
        int contractMonths = resolveContractMonths(contract);
        BigDecimal totalRentAmount = contract.getTotalRentAmount();

        if (Boolean.TRUE.equals(property.getWholeHouse())) {
            return calculateWholeHouse(property, contract, totalRentAmount, totalRenovationCost,
                    totalEquipmentCost, contractMonths, params);
        }
        return calculatePerRoom(property, contract, totalRentAmount, totalRenovationCost,
                totalEquipmentCost, contractMonths, params);
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
            DepreciationResultResponse row = toResponse(result, PricingScope.WHOLE_HOUSE);
            return DepreciationCalculationResponse.builder()
                    .propertyId(propertyId)
                    .pricingScope(PricingScope.WHOLE_HOUSE)
                    .capex(result.getTotalInvestment())
                    .contractMonths(result.getContractMonths())
                    .monthlyRecovery(result.getMonthlyDepreciation())
                    .revenueTarget(result.getSuggestedPriceWithProfit())
                    .wholeHouseResult(row)
                    .build();
        }

        List<DepreciationResult> persisted = depreciationResultRepository.findAllRoomLevelByPropertyId(propertyId);
        if (persisted.isEmpty()) {
            throw new ResourceNotFoundException(
                    "Chưa có kết quả tính giá theo phòng cho tòa nhà ID: " + propertyId);
        }

        List<DepreciationResultResponse> roomResults = persisted.stream()
                .map(r -> toResponse(r, PricingScope.ROOM))
                .toList();

        DepreciationResult first = persisted.getFirst();
        BigDecimal capex = persisted.stream()
                .map(DepreciationResult::getTotalInvestment)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal monthlyRecovery = persisted.stream()
                .map(DepreciationResult::getMonthlyDepreciation)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal revenueTarget = roomResults.stream()
                .map(DepreciationResultResponse::getSuggestedPriceWithProfit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return DepreciationCalculationResponse.builder()
                .propertyId(propertyId)
                .pricingScope(PricingScope.ROOM)
                .capex(capex)
                .contractMonths(first.getContractMonths())
                .monthlyRecovery(monthlyRecovery)
                .revenueTarget(revenueTarget)
                .roomCount(roomResults.size())
                .roomResults(roomResults)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PricingReconciliationResponse reconcile(
            Long propertyId,
            YearMonth month,
            BigDecimal oOperation,
            BigDecimal pDesired,
            BigDecimal vRate) {

        propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy tòa nhà với ID: " + propertyId));

        DepreciationCalculationResponse pricing = getByProperty(propertyId);
        BigDecimal safeOpex = oOperation != null ? oOperation : BigDecimal.ZERO;
        BigDecimal safePDesired = pDesired != null ? pDesired : BigDecimal.ZERO;
        BigDecimal safeVRate = vRate != null ? vRate : PricingCalculator.DEFAULT_V_RATE;

        BigDecimal monthlyRecovery = pricing.getMonthlyRecovery() != null
                ? pricing.getMonthlyRecovery()
                : BigDecimal.ZERO;
        BigDecimal fixedOpex = safeOpex.add(monthlyRecovery);
        BigDecimal revenueTarget = pricing.getRevenueTarget() != null
                ? pricing.getRevenueTarget()
                : BigDecimal.ZERO;

        BigDecimal actualRevenue = tenantContractRepository.sumPaidRentByPropertyAndMonth(
                propertyId,
                month.atDay(1).atStartOfDay(),
                month.plusMonths(1).atDay(1).atStartOfDay());

        int roomCount = pricing.getRoomCount() != null
                ? pricing.getRoomCount()
                : (pricing.getPricingScope() == PricingScope.WHOLE_HOUSE ? 1 : 0);
        if (roomCount == 0 && pricing.getRoomResults() != null) {
            roomCount = pricing.getRoomResults().size();
        }

        LocalDateTime asOf = month.atEndOfMonth().atTime(23, 59);
        long occupied;
        if (pricing.getPricingScope() == PricingScope.WHOLE_HOUSE) {
            occupied = tenantContractRepository.hasActiveWholeHouseTenant(propertyId, asOf.toLocalDate()) ? 1 : 0;
            roomCount = 1;
        } else {
            occupied = tenantContractRepository.countOccupiedRooms(propertyId, asOf.toLocalDate());
        }

        BigDecimal occupancyRate = roomCount > 0
                ? BigDecimal.valueOf(occupied)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(roomCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal actualProfit = actualRevenue.subtract(fixedOpex);
        BigDecimal actualCashFlow = actualRevenue.subtract(safeOpex);
        BigDecimal revenueTargetAtOccupancy = revenueTarget
                .multiply(occupancyRate)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);

        return PricingReconciliationResponse.builder()
                .propertyId(propertyId)
                .month(month)
                .actualRevenue(actualRevenue)
                .occupancyRate(occupancyRate)
                .actualProfit(actualProfit)
                .actualCashFlow(actualCashFlow)
                .fixedOpex(fixedOpex)
                .revenueTarget(revenueTarget)
                .revenueTargetAtOccupancy(revenueTargetAtOccupancy)
                .pDesired(safePDesired)
                .profitTargetMet(actualProfit.compareTo(safePDesired) >= 0)
                .revenueTargetMet(actualRevenue.compareTo(revenueTargetAtOccupancy) >= 0)
                .build();
    }

    private DepreciationCalculationResponse calculateWholeHouse(
            Property property,
            InboundContract contract,
            BigDecimal totalRentAmount,
            BigDecimal totalRenovationCost,
            BigDecimal totalEquipmentCost,
            int contractMonths,
            CalculateDepreciationRequest params) {

        PropertyResult result = PricingCalculator.calculateWholeHouse(
                totalRentAmount,
                totalRenovationCost,
                totalEquipmentCost,
                contractMonths,
                params.getOOperation(),
                params.getVRate(),
                params.getMode(),
                params.getPDesired(),
                params.getRoiExpected());

        RoomResult room = result.rooms().getFirst();
        DepreciationResult saved = depreciationResultRepository.save(
                buildResult(contract, null, room, result, contractMonths));

        return buildPropertyResponse(property.getId(), PricingScope.WHOLE_HOUSE, result,
                List.of(toResponse(saved, PricingScope.WHOLE_HOUSE)), null);
    }

    private DepreciationCalculationResponse calculatePerRoom(
            Property property,
            InboundContract contract,
            BigDecimal totalRentAmount,
            BigDecimal totalRenovationCost,
            BigDecimal totalEquipmentCost,
            int contractMonths,
            CalculateDepreciationRequest params) {

        List<Room> rooms = roomRepository.findByPropertyIdAndDeletedIsFalse(property.getId());
        List<RoomInput> inputs = rooms.stream()
                .map(room -> RoomInput.builder()
                        .roomId(room.getId())
                        .roomNumber(room.getRoomNumber())
                        .area(room.getArea())
                        .qualityFactor(resolveQualityFactor(room.getId(), params.getRoomQualityFactors()))
                        .roomEquipmentCost(equipmentRepository.sumPurchasedEquipmentCostByRoomId(room.getId()))
                        .build())
                .toList();

        PropertyResult result = PricingCalculator.calculate(
                totalRentAmount,
                totalRenovationCost,
                totalEquipmentCost,
                contractMonths,
                property.getAreaSize(),
                params.getOOperation(),
                params.getVRate(),
                params.getMode(),
                params.getPDesired(),
                params.getRoiExpected(),
                inputs);

        List<DepreciationResultResponse> roomResults = new ArrayList<>();
        for (RoomResult roomResult : result.rooms()) {
            Room room = rooms.stream()
                    .filter(r -> r.getId().equals(roomResult.roomId()))
                    .findFirst()
                    .orElseThrow();
            DepreciationResult saved = depreciationResultRepository.save(
                    buildResult(contract, room, roomResult, result, contractMonths));
            roomResults.add(toResponse(saved, PricingScope.ROOM));
        }

        return buildPropertyResponse(property.getId(), PricingScope.ROOM, result, roomResults, null);
    }

    private DepreciationCalculationResponse buildPropertyResponse(
            Long propertyId,
            PricingScope scope,
            PropertyResult result,
            List<DepreciationResultResponse> roomResults,
            DepreciationResultResponse wholeHouseResult) {

        DepreciationCalculationResponse.DepreciationCalculationResponseBuilder builder =
                DepreciationCalculationResponse.builder()
                        .propertyId(propertyId)
                        .pricingScope(scope)
                        .mode(result.mode())
                        .cRent(result.cRent())
                        .cRenovation(result.cRenovation())
                        .cEquipment(result.cEquipment())
                        .capex(result.capex())
                        .contractMonths(result.contractMonths())
                        .monthlyRecovery(result.monthlyRecovery())
                        .fixedOpex(result.fixedOpex())
                        .revenueMin(result.revenueMin())
                        .revenueTarget(result.revenueTarget())
                        .pDesired(result.pDesired())
                        .roiExpected(result.roiExpected())
                        .oOperation(result.oOperation())
                        .vRate(result.vRate())
                        .commonAreaM2(result.commonAreaM2())
                        .totalWeight(result.totalWeight())
                        .roomCount(roomResults != null ? roomResults.size() : null);

        if (scope == PricingScope.WHOLE_HOUSE) {
            builder.wholeHouseResult(wholeHouseResult != null ? wholeHouseResult : roomResults.getFirst());
        } else {
            builder.roomResults(roomResults);
        }
        return builder.build();
    }

    private DepreciationResult buildResult(
            InboundContract contract,
            Room room,
            RoomResult roomResult,
            PropertyResult propertyResult,
            int contractMonths) {

        return DepreciationResult.builder()
                .inboundContract(contract)
                .room(room)
                .totalRenovationCost(roomResult.renovationShare())
                .totalEquipmentCost(roomResult.equipmentShare())
                .totalRentAmount(roomResult.rentShare())
                .totalInvestment(roomResult.capexShare())
                .contractMonths(contractMonths)
                .monthlyDepreciation(roomResult.monthlyRecovery())
                .suggestedMinPrice(roomResult.roomFloor())
                .suggestedPriceWithProfit(roomResult.suggestedPrice())
                .roomFloor(roomResult.roomFloor())
                .effectiveM2(roomResult.effectiveM2())
                .weight(roomResult.weight())
                .calculatedAt(LocalDateTime.now())
                .build();
    }

    private CalculateDepreciationRequest normalizeRequest(CalculateDepreciationRequest request) {
        CalculateDepreciationRequest params = request != null
                ? request
                : CalculateDepreciationRequest.builder().build();

        if (params.getMode() == null) {
            if (params.getRoiExpected() != null) {
                params.setMode(PricingMode.REVERSE);
            } else {
                params.setMode(PricingMode.FORWARD);
            }
        }
        if (params.getOOperation() == null) {
            params.setOOperation(BigDecimal.ZERO);
        }
        if (params.getVRate() == null) {
            params.setVRate(PricingCalculator.DEFAULT_V_RATE);
        }
        if (params.getMode() == PricingMode.FORWARD && params.getPDesired() == null) {
            params.setPDesired(BigDecimal.ZERO);
        }
        return params;
    }

    private double resolveQualityFactor(Long roomId, Map<Long, BigDecimal> factors) {
        if (factors == null || !factors.containsKey(roomId) || factors.get(roomId) == null) {
            return 1.0;
        }
        return factors.get(roomId).doubleValue();
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
        return inboundContractRepository.findFirstByPropertyIdOrderByIdDesc(propertyId)
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
                .rentShare(result.getTotalRentAmount())
                .renovationShare(result.getTotalRenovationCost())
                .equipmentShare(result.getTotalEquipmentCost())
                .totalRenovationCost(result.getTotalRenovationCost())
                .totalEquipmentCost(result.getTotalEquipmentCost())
                .totalRentAmount(result.getTotalRentAmount())
                .totalInvestment(result.getTotalInvestment())
                .contractMonths(result.getContractMonths())
                .monthlyBreakEven(result.getMonthlyDepreciation())
                .roomFloor(result.getRoomFloor() != null ? result.getRoomFloor() : result.getSuggestedMinPrice())
                .suggestedMinPrice(result.getSuggestedMinPrice())
                .suggestedPriceWithProfit(result.getSuggestedPriceWithProfit())
                .effectiveM2(result.getEffectiveM2())
                .weight(result.getWeight())
                .calculatedAt(result.getCalculatedAt());

        if (result.getSuggestedPriceWithProfit() != null && result.getRoomFloor() != null) {
            builder.belowFloor(result.getSuggestedPriceWithProfit().compareTo(result.getRoomFloor()) < 0);
        }

        if (result.getRoom() != null) {
            builder.roomId(result.getRoom().getId())
                    .roomNumber(result.getRoom().getRoomNumber())
                    .area(result.getRoom().getArea());
        }
        return builder.build();
    }
}
