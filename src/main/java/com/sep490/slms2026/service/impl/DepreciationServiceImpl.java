package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.CalculateDepreciationRequest;
import com.sep490.slms2026.dto.response.*;
import com.sep490.slms2026.entity.*;
import com.sep490.slms2026.enums.EquipmentOperationalStatus;
import com.sep490.slms2026.enums.EquipmentSource;
import com.sep490.slms2026.enums.PricingCapitalItemKind;
import com.sep490.slms2026.enums.PricingMode;
import com.sep490.slms2026.enums.PricingScope;
import com.sep490.slms2026.enums.PropertyStatus;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.*;
import com.sep490.slms2026.service.DepreciationService;
import com.sep490.slms2026.service.PricingConfigService;
import com.sep490.slms2026.service.pricing.PricingCalculator;
import com.sep490.slms2026.service.pricing.PricingCalculator.PropertyResult;
import com.sep490.slms2026.service.pricing.PricingCalculator.RoomInput;
import com.sep490.slms2026.service.pricing.PricingCalculator.RoomResult;
import com.sep490.slms2026.util.InboundLeaseRules;
import com.sep490.slms2026.util.InboundLeaseRules.RevenueWindow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DepreciationServiceImpl implements DepreciationService {

    private final DepreciationResultRepository depreciationResultRepository;
    private final PricingCapitalItemRepository pricingCapitalItemRepository;
    private final InboundContractRepository inboundContractRepository;
    private final PropertyRepository propertyRepository;
    private final RenovationLineRepository renovationLineRepository;
    private final RoomRepository roomRepository;
    private final EquipmentRepository equipmentRepository;
    private final TenantContractRepository tenantContractRepository;
    private final PricingConfigService pricingConfigService;
    private final RenovationSessionRepository renovationSessionRepository;

    @Override
    @Transactional
    public DepreciationCalculationResponse calculate(Long propertyId, CalculateDepreciationRequest request) {
        Property property = loadDraftProperty(propertyId);
        InboundContract contract = loadContract(propertyId);
        CalculateDepreciationRequest params = normalizeRequest(request);

        Integer maxVersion = depreciationResultRepository.findMaxPricingVersionByPropertyId(propertyId);
        int currentVersion;

        int maxSessionNumber = renovationSessionRepository.findMaxSessionNumberByPropertyId(propertyId);
        RenovationSession currentSession = renovationSessionRepository
                .findByPropertyIdAndSessionNumber(propertyId, maxSessionNumber)
                .orElse(null);

        if (maxVersion == null) {
            currentVersion = 1;
        } else {
            currentVersion = Math.max(maxVersion, maxSessionNumber);
        }

        depreciationResultRepository.deleteByPropertyIdAndPricingVersion(propertyId, currentVersion);
        pricingCapitalItemRepository.deleteByPropertyIdAndPricingVersion(propertyId, currentVersion);

        RevenueWindow window = InboundLeaseRules.resolveRevenueWindow(
                contract, property, LocalDate.now(), params.getHandoverBufferMonths());
        int contractMonths = window.revenueMonths();
        LocalDate rentableFrom = window.rentableFrom();

        PricingConfig config = pricingConfigService.current();
        BigDecimal repairReservePctPerYear = config.getRepairReservePctPerYear() != null 
                ? config.getRepairReservePctPerYear() : new BigDecimal("10");

        List<PricingCapitalItem> items = prepareCapitalItems(property, contract, currentVersion, currentSession, contractMonths, rentableFrom);
        pricingCapitalItemRepository.saveAll(items);

        List<Equipment> activeEquipments = equipmentRepository.findByPropertyId(propertyId).stream()
                .filter(e -> e.getOperationalStatus() == EquipmentOperationalStatus.ACTIVE && e.getSource() == EquipmentSource.PURCHASED)
                .toList();

        DepreciationCalculationResponse response;
        if (Boolean.TRUE.equals(property.getWholeHouse())) {
            response = calculateWholeHouse(property, contract, contractMonths, params, window, currentVersion, items, activeEquipments, repairReservePctPerYear, currentSession);
        } else {
            response = calculatePerRoom(property, contract, contractMonths, params, window, currentVersion, items, activeEquipments, repairReservePctPerYear, currentSession);
        }
        depreciationResultRepository.supersedeOldVersions(propertyId, currentVersion);
        return response;
    }

    private List<PricingCapitalItem> prepareCapitalItems(Property property, InboundContract contract, int currentVersion, RenovationSession currentSession, int contractMonths, LocalDate rentableFrom) {
        List<PricingCapitalItem> items = new ArrayList<>();
        if (currentVersion == 1) {
            items.add(PricingCapitalItem.builder()
                    .propertyId(property.getId())
                    .pricingVersion(1)
                    .kind(PricingCapitalItemKind.RENT)
                    .amount(contract.getTotalRentAmount())
                    .startDate(rentableFrom)
                    .months(contractMonths)
                    .monthlyAmount(divideMoney(contract.getTotalRentAmount(), contractMonths))
                    .createdAt(LocalDateTime.now())
                    .build());

            List<RenovationLine> lines = renovationLineRepository.findByPropertyId(property.getId());
            for (RenovationLine line : lines) {
                items.add(PricingCapitalItem.builder()
                        .propertyId(property.getId())
                        .pricingVersion(1)
                        .kind(PricingCapitalItemKind.RENOVATION)
                        .sourceId(line.getId())
                        .amount(line.getCost())
                        .startDate(rentableFrom)
                        .months(contractMonths)
                        .monthlyAmount(divideMoney(line.getCost(), contractMonths))
                        .createdAt(LocalDateTime.now())
                        .build());
            }

            List<Equipment> equipments = equipmentRepository.findByPropertyId(property.getId()).stream()
                    .filter(e -> e.getSource() == EquipmentSource.PURCHASED && e.getOperationalStatus() == EquipmentOperationalStatus.ACTIVE)
                    .toList();

            for (Equipment eq : equipments) {
                items.add(PricingCapitalItem.builder()
                        .propertyId(property.getId())
                        .pricingVersion(1)
                        .kind(PricingCapitalItemKind.EQUIPMENT)
                        .sourceId(eq.getId())
                        .roomId(eq.getRoom() != null ? eq.getRoom().getId() : null)
                        .houseArea(eq.getHouseArea() != null ? true : null)
                        .amount(eq.getPrice())
                        .startDate(rentableFrom)
                        .months(contractMonths)
                        .monthlyAmount(divideMoney(eq.getPrice(), contractMonths))
                        .createdAt(LocalDateTime.now())
                        .build());
            }
        } else {
            List<PricingCapitalItem> existingItems = pricingCapitalItemRepository.findByPropertyIdAndPricingVersion(property.getId(), currentVersion - 1);
            
            if (existingItems.isEmpty()) {
                int oldContractMonths = contractMonths;
                LocalDate oldStartDate = rentableFrom;
                List<DepreciationResult> oldResults = depreciationResultRepository.findByPropertyIdAndPricingVersion(property.getId(), currentVersion - 1);
                if (!oldResults.isEmpty()) {
                    DepreciationResult oldResult = oldResults.get(0);
                    if (oldResult.getContractMonths() != null) {
                        oldContractMonths = oldResult.getContractMonths();
                    }
                    if (oldResult.getCalculatedAt() != null) {
                        oldStartDate = oldResult.getCalculatedAt().toLocalDate();
                    }
                }

                existingItems.add(PricingCapitalItem.builder()
                        .propertyId(property.getId())
                        .pricingVersion(currentVersion - 1)
                        .kind(PricingCapitalItemKind.RENT)
                        .amount(contract.getTotalRentAmount())
                        .startDate(oldStartDate)
                        .months(oldContractMonths)
                        .monthlyAmount(divideMoney(contract.getTotalRentAmount(), oldContractMonths))
                        .createdAt(LocalDateTime.now())
                        .build());

                List<RenovationLine> lines = renovationLineRepository.findByPropertyId(property.getId());
                for (RenovationLine line : lines) {
                    if (line.getSession() != null && line.getSession().getSessionNumber() >= currentVersion) continue;
                    existingItems.add(PricingCapitalItem.builder()
                            .propertyId(property.getId())
                            .pricingVersion(currentVersion - 1)
                            .kind(PricingCapitalItemKind.RENOVATION)
                            .sourceId(line.getId())
                            .amount(line.getCost())
                            .startDate(oldStartDate)
                            .months(oldContractMonths)
                            .monthlyAmount(divideMoney(line.getCost(), oldContractMonths))
                            .createdAt(LocalDateTime.now())
                            .build());
                }

                List<Long> replacedInCurrentSession = new ArrayList<>();
                if (currentSession != null) {
                    replacedInCurrentSession = equipmentRepository.findByRenovationSessionIdOrderByIdAsc(currentSession.getId()).stream()
                        .filter(e -> e.getReplacedEquipmentId() != null)
                        .map(Equipment::getReplacedEquipmentId)
                        .toList();
                }
                
                final List<Long> replacedIds = replacedInCurrentSession;
                List<Equipment> equipments = equipmentRepository.findByPropertyId(property.getId()).stream()
                        .filter(e -> e.getSource() == EquipmentSource.PURCHASED
                                && (e.getRenovationSession() == null || e.getRenovationSession().getSessionNumber() < currentVersion)
                                && (e.getOperationalStatus() == EquipmentOperationalStatus.ACTIVE
                                    || replacedIds.contains(e.getId())))
                        .toList();

                for (Equipment eq : equipments) {
                    existingItems.add(PricingCapitalItem.builder()
                            .propertyId(property.getId())
                            .pricingVersion(currentVersion - 1)
                            .kind(PricingCapitalItemKind.EQUIPMENT)
                            .sourceId(eq.getId())
                            .roomId(eq.getRoom() != null ? eq.getRoom().getId() : null)
                            .houseArea(eq.getHouseArea() != null ? true : null)
                            .amount(eq.getPrice())
                            .startDate(oldStartDate)
                            .months(oldContractMonths)
                            .monthlyAmount(divideMoney(eq.getPrice(), oldContractMonths))
                            .createdAt(LocalDateTime.now())
                            .build());
                }
            }

            for (PricingCapitalItem oldItem : existingItems) {
                items.add(PricingCapitalItem.builder()
                        .propertyId(oldItem.getPropertyId())
                        .pricingVersion(currentVersion)
                        .kind(oldItem.getKind())
                        .sourceId(oldItem.getSourceId())
                        .roomId(oldItem.getRoomId())
                        .houseArea(oldItem.getHouseArea())
                        .amount(oldItem.getAmount())
                        .startDate(oldItem.getStartDate())
                        .months(oldItem.getMonths())
                        .monthlyAmount(oldItem.getMonthlyAmount())
                        .createdAt(LocalDateTime.now())
                        .build());
            }

            if (currentSession != null) {
                List<RenovationLine> lines = renovationLineRepository.findBySessionIdOrderByIdAsc(currentSession.getId());
                for (RenovationLine line : lines) {
                    items.add(PricingCapitalItem.builder()
                            .propertyId(property.getId())
                            .pricingVersion(currentVersion)
                            .kind(PricingCapitalItemKind.RENOVATION)
                            .sourceId(line.getId())
                            .amount(line.getCost())
                            .startDate(rentableFrom)
                            .months(contractMonths)
                            .monthlyAmount(divideMoney(line.getCost(), contractMonths))
                            .createdAt(LocalDateTime.now())
                            .build());
                }

                List<Equipment> equipments = equipmentRepository.findByRenovationSessionIdOrderByIdAsc(currentSession.getId());
                for (Equipment eq : equipments) {
                    if (eq.getOperationalStatus() != EquipmentOperationalStatus.ACTIVE) continue;
                    if (eq.getSource() != EquipmentSource.PURCHASED) continue;

                    if (eq.getReplacedEquipmentId() != null) {
                        BigDecimal repPrice = eq.getReplacedEquipmentPrice() != null ? eq.getReplacedEquipmentPrice() : BigDecimal.ZERO;
                        BigDecimal upgradeCost = eq.getPrice().subtract(repPrice);
                        if (upgradeCost.compareTo(BigDecimal.ZERO) > 0) {
                            items.add(PricingCapitalItem.builder()
                                    .propertyId(property.getId())
                                    .pricingVersion(currentVersion)
                                    .kind(PricingCapitalItemKind.EQUIPMENT_UPGRADE)
                                    .sourceId(eq.getId())
                                    .roomId(eq.getRoom() != null ? eq.getRoom().getId() : null)
                                    .houseArea(eq.getHouseArea() != null ? true : null)
                                    .amount(upgradeCost)
                                    .startDate(rentableFrom)
                                    .months(contractMonths)
                                    .monthlyAmount(divideMoney(upgradeCost, contractMonths))
                                    .createdAt(LocalDateTime.now())
                                    .build());
                        }
                    } else {
                        items.add(PricingCapitalItem.builder()
                                .propertyId(property.getId())
                                .pricingVersion(currentVersion)
                                .kind(PricingCapitalItemKind.EQUIPMENT)
                                .sourceId(eq.getId())
                                .roomId(eq.getRoom() != null ? eq.getRoom().getId() : null)
                                .houseArea(eq.getHouseArea() != null ? true : null)
                                .amount(eq.getPrice())
                                .startDate(rentableFrom)
                                .months(contractMonths)
                                .monthlyAmount(divideMoney(eq.getPrice(), contractMonths))
                                .createdAt(LocalDateTime.now())
                                .build());
                    }
                }
            }
        }
        return items;
    }

    private BigDecimal calculateRepairReserve(List<Equipment> equipments, BigDecimal pct, int windowMonths, LocalDate windowStart, LocalDate windowEnd) {
        if (pct.compareTo(BigDecimal.ZERO) <= 0 || equipments == null || equipments.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal totalReserve = BigDecimal.ZERO;
        for (Equipment eq : equipments) {
            LocalDate warEnd = eq.getWarrantyEndDate();
            LocalDate startCalc = windowStart;
            if (warEnd != null && warEnd.isAfter(startCalc)) {
                startCalc = warEnd;
            }
            if (!startCalc.isBefore(windowEnd)) {
                continue;
            }
            long monthsNoWar = ChronoUnit.MONTHS.between(startCalc.withDayOfMonth(1), windowEnd.withDayOfMonth(1));
            if (monthsNoWar <= 0) continue;

            BigDecimal eqReserve = eq.getPrice()
                    .multiply(pct)
                    .divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP)
                    .divide(new BigDecimal("12"), 8, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(monthsNoWar));
            totalReserve = totalReserve.add(eqReserve);
        }
        return divideMoney(totalReserve, windowMonths);
    }

    // Include all other existing methods but modified for new logic
    private DepreciationCalculationResponse calculateWholeHouse(
            Property property, InboundContract contract, int contractMonths,
            CalculateDepreciationRequest params, RevenueWindow window, int currentVersion,
            List<PricingCapitalItem> items, List<Equipment> activeEquipments, BigDecimal repairReservePctPerYear, RenovationSession currentSession) {

        BigDecimal totalCapex = items.stream().map(PricingCapitalItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalMonthlyRecovery = items.stream().map(PricingCapitalItem::getMonthlyAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalRepairReserve = calculateRepairReserve(activeEquipments, repairReservePctPerYear, contractMonths, window.rentableFrom(), window.rentableFrom().plusMonths(contractMonths));

        PropertyResult result = PricingCalculator.calculateWholeHouse(
                totalCapex, totalMonthlyRecovery, totalRepairReserve, contractMonths,
                params.getOOperation(), params.getVRate(), params.getMode(), params.getPDesired(), params.getRoiExpected());

        RoomResult room = result.rooms().getFirst();
        DepreciationResult saved = depreciationResultRepository.save(
                buildResult(contract, null, room, result, contractMonths, currentVersion));

        return buildPropertyResponse(property.getId(), PricingScope.WHOLE_HOUSE, result,
                List.of(toResponse(saved, PricingScope.WHOLE_HOUSE)), null, window, items, totalRepairReserve, calculateCompanyAbsorbed(property, currentSession, List.of(room), null), currentVersion);
    }

    private DepreciationCalculationResponse calculatePerRoom(
            Property property, InboundContract contract, int contractMonths,
            CalculateDepreciationRequest params, RevenueWindow window, int currentVersion,
            List<PricingCapitalItem> items, List<Equipment> activeEquipments, BigDecimal repairReservePctPerYear, RenovationSession currentSession) {

        List<Room> rooms = roomRepository.findByPropertyIdAndDeletedIsFalse(property.getId());
        
        BigDecimal capexCommon = items.stream().filter(i -> i.getRoomId() == null).map(PricingCapitalItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal monthlyRecoveryCommon = items.stream().filter(i -> i.getRoomId() == null).map(PricingCapitalItem::getMonthlyAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Equipment> commonEquipments = activeEquipments.stream().filter(e -> e.getRoom() == null).toList();
        BigDecimal repairReserveCommon = calculateRepairReserve(commonEquipments, repairReservePctPerYear, contractMonths, window.rentableFrom(), window.rentableFrom().plusMonths(contractMonths));

        List<RoomInput> inputs = new ArrayList<>();
        for (Room room : rooms) {
            BigDecimal roomCapex = items.stream().filter(i -> room.getId().equals(i.getRoomId())).map(PricingCapitalItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal roomMonthlyRecovery = items.stream().filter(i -> room.getId().equals(i.getRoomId())).map(PricingCapitalItem::getMonthlyAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            List<Equipment> roomEquips = activeEquipments.stream().filter(e -> e.getRoom() != null && e.getRoom().getId().equals(room.getId())).toList();
            BigDecimal roomRepairReserve = calculateRepairReserve(roomEquips, repairReservePctPerYear, contractMonths, window.rentableFrom(), window.rentableFrom().plusMonths(contractMonths));

            inputs.add(RoomInput.builder()
                    .roomId(room.getId())
                    .roomNumber(room.getRoomNumber())
                    .capexPrivate(roomCapex)
                    .monthlyRecoveryPrivate(roomMonthlyRecovery)
                    .repairReservePrivate(roomRepairReserve)
                    .build());
        }

        PropertyResult result = PricingCalculator.calculate(
                capexCommon, monthlyRecoveryCommon, repairReserveCommon, contractMonths,
                params.getOOperation(), params.getVRate(), params.getMode(), params.getPDesired(), params.getRoiExpected(), inputs);

        List<DepreciationResultResponse> roomResults = new ArrayList<>();
        for (RoomResult roomResult : result.rooms()) {
            Room room = rooms.stream().filter(r -> r.getId().equals(roomResult.roomId())).findFirst().orElseThrow();
            DepreciationResult saved = depreciationResultRepository.save(
                    buildResult(contract, room, roomResult, result, contractMonths, currentVersion));
            roomResults.add(toResponse(saved, PricingScope.ROOM));
        }

        BigDecimal totalRepairReserve = result.repairReserve();
        CompanyAbsorbedResponse absorbed = calculateCompanyAbsorbed(property, currentSession, result.rooms(), rooms);

        return buildPropertyResponse(property.getId(), PricingScope.ROOM, result, roomResults, null, window, items, totalRepairReserve, absorbed, currentVersion);
    }

    private CompanyAbsorbedResponse calculateCompanyAbsorbed(Property property, RenovationSession currentSession, List<RoomResult> results, List<Room> rooms) {
        BigDecimal equivalentReplacement = BigDecimal.ZERO;
        if (currentSession != null) {
            List<Equipment> equips = equipmentRepository.findByRenovationSessionIdOrderByIdAsc(currentSession.getId());
            for (Equipment eq : equips) {
                if (eq.getSource() == EquipmentSource.PURCHASED && eq.getReplacedEquipmentId() != null) {
                    BigDecimal eqPrice = eq.getPrice() != null ? eq.getPrice() : BigDecimal.ZERO;
                    BigDecimal repPrice = eq.getReplacedEquipmentPrice() != null ? eq.getReplacedEquipmentPrice() : BigDecimal.ZERO;
                    equivalentReplacement = equivalentReplacement.add(eqPrice.min(repPrice));
                }
            }
        }

        List<TenantOnOldPriceResponse> tenantsOnOldPrice = new ArrayList<>();
        List<TenantContract> activeContracts = tenantContractRepository.findByPropertyIdAndStatusIn(property.getId(), List.of(ContractStatus.ACTIVE));
        for (TenantContract contract : activeContracts) {
            Long roomId = contract.getRoom() != null ? contract.getRoom().getId() : null;
            RoomResult matchingResult = results.stream().filter(r -> (roomId == null && r.roomId() == null) || (roomId != null && roomId.equals(r.roomId()))).findFirst().orElse(null);
            if (matchingResult != null) {
                BigDecimal newFloor = matchingResult.roomFloor();
                BigDecimal oldRent = contract.getRentAmount() != null ? contract.getRentAmount() : BigDecimal.ZERO;
                BigDecimal diff = newFloor.subtract(oldRent);
                if (diff.compareTo(BigDecimal.ZERO) > 0) {
                    LocalDate now = LocalDate.now();
                    LocalDate end = contract.getEndDate();
                    if (end != null && end.isAfter(now)) {
                        long months = ChronoUnit.MONTHS.between(now.withDayOfMonth(1), end.withDayOfMonth(1));
                        if (months > 0) {
                            tenantsOnOldPrice.add(TenantOnOldPriceResponse.builder()
                                    .roomId(roomId)
                                    .roomName(matchingResult.roomNumber())
                                    .contractPrice(oldRent)
                                    .newFloorPrice(newFloor)
                                    .remainingMonths((int) months)
                                    .absorbedAmount(diff.multiply(BigDecimal.valueOf(months)))
                                    .build());
                        }
                    }
                }
            }
        }
        return CompanyAbsorbedResponse.builder()
                .equivalentReplacement(equivalentReplacement)
                .tenantsOnOldPrice(tenantsOnOldPrice)
                .build();
    }

    private DepreciationCalculationResponse buildPropertyResponse(
            Long propertyId, PricingScope scope, PropertyResult result,
            List<DepreciationResultResponse> roomResults, DepreciationResultResponse wholeHouseResult,
            RevenueWindow window, List<PricingCapitalItem> items, BigDecimal repairReservePerMonth, CompanyAbsorbedResponse companyAbsorbed, int currentVersion) {

        BigDecimal cRent = items.stream().filter(i -> i.getKind() == PricingCapitalItemKind.RENT).map(PricingCapitalItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cRenovation = items.stream().filter(i -> i.getKind() == PricingCapitalItemKind.RENOVATION).map(PricingCapitalItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cEquipment = items.stream().filter(i -> i.getKind() == PricingCapitalItemKind.EQUIPMENT || i.getKind() == PricingCapitalItemKind.EQUIPMENT_UPGRADE).map(PricingCapitalItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal previousFloor = BigDecimal.ZERO;
        if (currentVersion > 1) {
            List<DepreciationResult> oldResults = depreciationResultRepository.findByPropertyIdAndPricingVersion(propertyId, currentVersion - 1);
            for (DepreciationResult r : oldResults) {
                if (r.getRoomFloor() != null) {
                    previousFloor = previousFloor.add(r.getRoomFloor());
                }
            }
        }
        
        BigDecimal newFloor = BigDecimal.ZERO;
        if (scope == PricingScope.WHOLE_HOUSE) {
            newFloor = wholeHouseResult != null && wholeHouseResult.getRoomFloor() != null 
                    ? wholeHouseResult.getRoomFloor() 
                    : (roomResults != null && !roomResults.isEmpty() && roomResults.getFirst().getRoomFloor() != null ? roomResults.getFirst().getRoomFloor() : BigDecimal.ZERO);
        } else if (roomResults != null) {
            for (DepreciationResultResponse r : roomResults) {
                if (r.getRoomFloor() != null) {
                    newFloor = newFloor.add(r.getRoomFloor());
                }
            }
        }

        DepreciationCalculationResponse.DepreciationCalculationResponseBuilder builder =
                DepreciationCalculationResponse.builder()
                        .propertyId(propertyId)
                        .pricingScope(scope)
                        .mode(result.mode())
                        .cRent(cRent)
                        .cRenovation(cRenovation)
                        .cEquipment(cEquipment)
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
                        .roomCount(roomResults != null ? roomResults.size() : null)
                        .repairReservePerMonth(repairReservePerMonth)
                        .companyAbsorbed(companyAbsorbed)
                        .previousFloor(previousFloor)
                        .newFloor(newFloor);

        List<PricingCapitalItemResponse> capitalItemResponses = items.stream().map(i -> {
            String itemName = "Vốn";
            if (i.getKind() == PricingCapitalItemKind.RENT) {
                itemName = "Vốn thuê nhà";
            } else if (i.getKind() == PricingCapitalItemKind.RENOVATION && i.getSourceId() != null) {
                itemName = renovationLineRepository.findById(i.getSourceId()).map(r -> r.getCategory() != null ? r.getCategory().getName() : "Cải tạo").orElse("Cải tạo");
            } else if ((i.getKind() == PricingCapitalItemKind.EQUIPMENT || i.getKind() == PricingCapitalItemKind.EQUIPMENT_UPGRADE) && i.getSourceId() != null) {
                itemName = equipmentRepository.findById(i.getSourceId()).map(e -> e.getEquipmentName() != null ? e.getEquipmentName() : (e.getCatalog() != null ? e.getCatalog().getName() : "Thiết bị")).orElse("Thiết bị");
            }
            
            long passed = 0;
            if (i.getStartDate() != null && i.getStartDate().isBefore(LocalDate.now())) {
                passed = ChronoUnit.MONTHS.between(i.getStartDate().withDayOfMonth(1), LocalDate.now().withDayOfMonth(1));
            }
            passed = Math.max(0, Math.min(passed, i.getMonths() != null ? i.getMonths() : 0));
            BigDecimal depreciated = i.getMonthlyAmount() != null ? i.getMonthlyAmount().multiply(BigDecimal.valueOf(passed)) : BigDecimal.ZERO;
            BigDecimal remaining = i.getAmount() != null ? i.getAmount().subtract(depreciated) : BigDecimal.ZERO;

            return PricingCapitalItemResponse.builder()
                .id(i.getId())
                .sourceId(i.getSourceId())
                .itemName(itemName)
                .pricingVersion(i.getPricingVersion())
                .kind(i.getKind())
                .roomId(i.getRoomId())
                .houseArea(i.getHouseArea())
                .amount(i.getAmount())
                .startDate(i.getStartDate())
                .months(i.getMonths())
                .monthlyAmount(i.getMonthlyAmount())
                .depreciatedAmount(depreciated)
                .remainingAmount(remaining)
                .build();
        }).collect(Collectors.toList());
        builder.capitalItems(capitalItemResponses);

        if (scope == PricingScope.WHOLE_HOUSE) {
            builder.wholeHouseResult(wholeHouseResult != null ? wholeHouseResult : roomResults.getFirst());
        } else {
            builder.roomResults(roomResults);
        }
        return applyRevenueWindow(builder.build(), window);
    }

    private DepreciationCalculationResponse applyRevenueWindow(
            DepreciationCalculationResponse response, Property property) {
        InboundContract contract = inboundContractRepository.findFirstByPropertyIdOrderByIdDesc(property.getId())
                .orElse(null);
        if (contract == null) {
            return response;
        }
        try {
            Integer buffer = pricingConfigService.current().getHandoverBufferMonths();
            return applyRevenueWindow(response,
                    InboundLeaseRules.resolveRevenueWindow(contract, property, LocalDate.now(), buffer));
        } catch (BusinessException ignored) {
            return response;
        }
    }

    private DepreciationCalculationResponse applyRevenueWindow(
            DepreciationCalculationResponse response, RevenueWindow window) {
        if (response == null || window == null) {
            return response;
        }
        response.setLeaseMonths(window.leaseMonths());
        response.setRentableFrom(window.rentableFrom());
        response.setRentableMonths(window.rentableMonths());
        response.setHandoverBufferMonths(window.handoverBufferMonths());
        response.setRevenueMonths(window.revenueMonths());
        return response;
    }

    private DepreciationResult buildResult(
            InboundContract contract, Room room, RoomResult roomResult, PropertyResult propertyResult, int contractMonths, int version) {
        return DepreciationResult.builder()
                .inboundContract(contract)
                .room(room)
                .totalRenovationCost(BigDecimal.ZERO)
                .totalEquipmentCost(BigDecimal.ZERO)
                .totalRentAmount(BigDecimal.ZERO)
                .totalInvestment(roomResult.capexShare())
                .contractMonths(contractMonths)
                .monthlyDepreciation(roomResult.monthlyRecovery())
                .opexShare(roomResult.opexShare())
                .suggestedPriceWithProfit(roomResult.suggestedPrice())
                .roomFloor(roomResult.roomFloor())
                .effectiveM2(roomResult.effectiveM2())
                .weight(roomResult.weight())
                .calculatedAt(LocalDateTime.now())
                .pricingVersion(version)
                .build();
    }

    private CalculateDepreciationRequest normalizeRequest(CalculateDepreciationRequest request) {
        CalculateDepreciationRequest params = request != null ? request : CalculateDepreciationRequest.builder().build();
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
        if (params.getHandoverBufferMonths() == null) {
            params.setHandoverBufferMonths(pricingConfigService.current().getHandoverBufferMonths());
        }
        if (params.getMode() == PricingMode.FORWARD && params.getPDesired() == null) {
            params.setPDesired(BigDecimal.ZERO);
        }
        return params;
    }

    private Property loadDraftProperty(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tòa nhà với ID: " + propertyId));
        if (!property.getStatus().isOnboardingEditable()
                && property.getStatus() != PropertyStatus.PENDING_HOST_REVIEW) {
            throw new BusinessException("Chỉ tính giá khi tòa nhà đang trong quá trình onboarding");
        }
        return property;
    }

    private InboundContract loadContract(Long propertyId) {
        return inboundContractRepository.findFirstByPropertyIdOrderByIdDesc(propertyId)
                .orElseThrow(() -> new BusinessException("Phải ký hợp đồng inbound trước khi tính giá"));
    }

    private DepreciationResultResponse toResponse(DepreciationResult result, PricingScope scope) {
        DepreciationResultResponse.DepreciationResultResponseBuilder builder = DepreciationResultResponse.builder()
                .id(result.getId())
                .propertyId(result.getInboundContract().getProperty().getId())
                .inboundContractId(result.getInboundContract().getId())
                .pricingScope(scope)
                .rentShare(BigDecimal.ZERO)
                .renovationShare(BigDecimal.ZERO)
                .equipmentShare(BigDecimal.ZERO)
                .totalRenovationCost(BigDecimal.ZERO)
                .totalEquipmentCost(BigDecimal.ZERO)
                .totalRentAmount(BigDecimal.ZERO)
                .totalInvestment(result.getTotalInvestment())
                .contractMonths(result.getContractMonths())
                .monthlyBreakEven(result.getMonthlyDepreciation())
                .roomFloor(result.getRoomFloor() != null ? result.getRoomFloor() : BigDecimal.ZERO)
                .opexShare(result.getOpexShare())
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
    
    private static BigDecimal divideMoney(BigDecimal value, int divisor) {
        return value.divide(BigDecimal.valueOf(divisor), 0, RoundingMode.HALF_UP);
    }
    
    @Override
    @Transactional(readOnly = true)
    public DepreciationCalculationResponse getByProperty(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tòa nhà với ID: " + propertyId));

        if (Boolean.TRUE.equals(property.getWholeHouse())) {
            DepreciationResult result = depreciationResultRepository.findWholeHouseByPropertyId(propertyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Chưa có kết quả tính giá cho nhà nguyên căn ID: " + propertyId));
            DepreciationResultResponse row = toResponse(result, PricingScope.WHOLE_HOUSE);
            return applyRevenueWindow(DepreciationCalculationResponse.builder()
                    .propertyId(propertyId)
                    .pricingScope(PricingScope.WHOLE_HOUSE)
                    .capex(result.getTotalInvestment())
                    .contractMonths(result.getContractMonths())
                    .monthlyRecovery(result.getMonthlyDepreciation())
                    .revenueTarget(result.getSuggestedPriceWithProfit())
                    .wholeHouseResult(row)
                    .build(), property);
        }

        List<DepreciationResult> persisted = depreciationResultRepository.findAllRoomLevelByPropertyId(propertyId);
        if (persisted.isEmpty()) {
            throw new ResourceNotFoundException("Chưa có kết quả tính giá theo phòng cho tòa nhà ID: " + propertyId);
        }

        List<DepreciationResultResponse> roomResults = persisted.stream().map(r -> toResponse(r, PricingScope.ROOM)).toList();
        DepreciationResult first = persisted.getFirst();
        BigDecimal capex = persisted.stream().map(DepreciationResult::getTotalInvestment).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal monthlyRecovery = persisted.stream().map(DepreciationResult::getMonthlyDepreciation).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal revenueTarget = roomResults.stream().map(DepreciationResultResponse::getSuggestedPriceWithProfit).reduce(BigDecimal.ZERO, BigDecimal::add);

        return applyRevenueWindow(DepreciationCalculationResponse.builder()
                .propertyId(propertyId)
                .pricingScope(PricingScope.ROOM)
                .capex(capex)
                .contractMonths(first.getContractMonths())
                .monthlyRecovery(monthlyRecovery)
                .revenueTarget(revenueTarget)
                .roomCount(roomResults.size())
                .roomResults(roomResults)
                .build(), property);
    }
    
    @Override
    @Transactional(readOnly = true)
    public PricingReconciliationResponse reconcile(Long propertyId, YearMonth month, BigDecimal oOperation, BigDecimal pDesired, BigDecimal vRate) {
        throw new BusinessException("Tính năng đang được phát triển.");
    }
}
