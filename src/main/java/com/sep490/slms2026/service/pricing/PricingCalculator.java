package com.sep490.slms2026.service.pricing;

import com.sep490.slms2026.enums.PricingMode;
import com.sep490.slms2026.exception.BusinessException;
import lombok.Builder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Công thức định giá theo docs/PRICING_FORMULA.md — prepaid rent, phân bổ theo m².
 */
public final class PricingCalculator {

    public static final BigDecimal DEFAULT_V_RATE = new BigDecimal("0.10");
    private static final int MONEY_SCALE = 0;
    private static final int RATIO_SCALE = 8;

    private PricingCalculator() {
    }

    @Builder
    public record RoomInput(
            Long roomId,
            String roomNumber,
            double area,
            double qualityFactor,
            BigDecimal roomEquipmentCost) {
    }

    @Builder
    public record RoomResult(
            Long roomId,
            String roomNumber,
            double area,
            double effectiveM2,
            double weight,
            BigDecimal rentShare,
            BigDecimal renovationShare,
            BigDecimal equipmentShare,
            BigDecimal capexShare,
            BigDecimal monthlyRecovery,
            BigDecimal opexShare,
            BigDecimal roomFloor,
            BigDecimal suggestedPrice) {
    }

    @Builder
    public record PropertyResult(
            BigDecimal cRent,
            BigDecimal cRenovation,
            BigDecimal cEquipment,
            BigDecimal capex,
            int contractMonths,
            BigDecimal monthlyRecovery,
            BigDecimal fixedOpex,
            BigDecimal revenueMin,
            BigDecimal revenueTarget,
            BigDecimal pDesired,
            BigDecimal roiExpected,
            BigDecimal oOperation,
            BigDecimal vRate,
            PricingMode mode,
            double commonAreaM2,
            double totalWeight,
            List<RoomResult> rooms) {
    }

    public static PropertyResult calculate(
            BigDecimal cRent,
            BigDecimal cRenovation,
            BigDecimal cEquipment,
            int contractMonths,
            Double propertyAreaSize,
            BigDecimal oOperation,
            BigDecimal vRate,
            PricingMode mode,
            BigDecimal pDesired,
            BigDecimal roiExpected,
            List<RoomInput> rooms) {

        validateInputs(contractMonths, mode, pDesired, roiExpected, rooms);

        BigDecimal safeRent = nz(cRent);
        BigDecimal safeRenovation = nz(cRenovation);
        BigDecimal safeEquipment = nz(cEquipment);
        BigDecimal safeOpex = nz(oOperation);
        BigDecimal safeVRate = vRate != null ? vRate : DEFAULT_V_RATE;

        BigDecimal capex = safeRent.add(safeRenovation).add(safeEquipment);
        BigDecimal monthlyRecovery = divideMoney(capex, contractMonths);
        BigDecimal fixedOpex = safeOpex.add(monthlyRecovery);

        BigDecimal revenueMin;
        BigDecimal revenueTarget;
        if (mode == PricingMode.FORWARD) {
            BigDecimal profit = nz(pDesired);
            revenueMin = fixedOpex.add(profit);
            revenueTarget = applyVacancyBuffer(revenueMin, safeVRate);
        } else {
            BigDecimal roi = nz(roiExpected);
            BigDecimal years = BigDecimal.valueOf(contractMonths)
                    .divide(BigDecimal.valueOf(12), RATIO_SCALE, RoundingMode.HALF_UP);
            BigDecimal totalProfit = capex
                    .multiply(roi)
                    .divide(BigDecimal.valueOf(100), RATIO_SCALE, RoundingMode.HALF_UP)
                    .multiply(years);
            BigDecimal monthlyGoal = capex.add(totalProfit)
                    .divide(BigDecimal.valueOf(contractMonths), RATIO_SCALE, RoundingMode.HALF_UP);
            revenueMin = monthlyGoal.add(safeOpex);
            revenueTarget = applyVacancyBuffer(revenueMin, safeVRate);
        }

        double sumRoomArea = rooms.stream().mapToDouble(RoomInput::area).sum();
        double commonArea = 0;
        if (propertyAreaSize != null && propertyAreaSize > sumRoomArea) {
            commonArea = propertyAreaSize - sumRoomArea;
        }

        List<WeightedRoom> weightedRooms = new ArrayList<>();
        double totalWeight = 0;
        for (RoomInput room : rooms) {
            double effectiveM2 = room.area() + commonArea * (room.area() / sumRoomArea);
            double weight = effectiveM2 * room.qualityFactor();
            totalWeight += weight;
            weightedRooms.add(new WeightedRoom(room, effectiveM2, weight));
        }

        BigDecimal assignedEquipment = rooms.stream()
                .map(r -> nz(r.roomEquipmentCost()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sharedEquipment = safeEquipment.subtract(assignedEquipment).max(BigDecimal.ZERO);

        List<RoomResult> roomResults = new ArrayList<>();
        BigDecimal allocatedPrice = BigDecimal.ZERO;

        weightedRooms.sort(Comparator.comparing(w -> w.room().roomId()));

        for (int i = 0; i < weightedRooms.size(); i++) {
            WeightedRoom wr = weightedRooms.get(i);
            BigDecimal weightRatio = ratio(wr.weight(), totalWeight);

            BigDecimal rentShare = money(safeRent.multiply(weightRatio));
            BigDecimal renovationShare = money(safeRenovation.multiply(weightRatio));
            BigDecimal equipmentShare = money(nz(wr.room().roomEquipmentCost())
                    .add(sharedEquipment.multiply(weightRatio)));
            BigDecimal capexShare = rentShare.add(renovationShare).add(equipmentShare);
            BigDecimal monthlyRecoveryRoom = divideMoney(capexShare, contractMonths);
            BigDecimal opexShare = money(safeOpex.multiply(weightRatio));
            BigDecimal roomFloor = applyVacancyBuffer(monthlyRecoveryRoom.add(opexShare), safeVRate);

            BigDecimal suggestedPrice;
            if (i == weightedRooms.size() - 1) {
                suggestedPrice = money(revenueTarget.subtract(allocatedPrice));
            } else {
                suggestedPrice = money(revenueTarget.multiply(weightRatio));
                allocatedPrice = allocatedPrice.add(suggestedPrice);
            }

            roomResults.add(RoomResult.builder()
                    .roomId(wr.room().roomId())
                    .roomNumber(wr.room().roomNumber())
                    .area(wr.room().area())
                    .effectiveM2(round2(wr.effectiveM2()))
                    .weight(round2(wr.weight()))
                    .rentShare(rentShare)
                    .renovationShare(renovationShare)
                    .equipmentShare(equipmentShare)
                    .capexShare(capexShare)
                    .monthlyRecovery(monthlyRecoveryRoom)
                    .opexShare(opexShare)
                    .roomFloor(roomFloor)
                    .suggestedPrice(suggestedPrice)
                    .build());
        }

        return PropertyResult.builder()
                .cRent(safeRent)
                .cRenovation(safeRenovation)
                .cEquipment(safeEquipment)
                .capex(capex)
                .contractMonths(contractMonths)
                .monthlyRecovery(monthlyRecovery)
                .fixedOpex(fixedOpex)
                .revenueMin(money(revenueMin))
                .revenueTarget(money(revenueTarget))
                .pDesired(pDesired)
                .roiExpected(roiExpected)
                .oOperation(safeOpex)
                .vRate(safeVRate)
                .mode(mode)
                .commonAreaM2(round2(commonArea))
                .totalWeight(round2(totalWeight))
                .rooms(roomResults)
                .build();
    }

    public static PropertyResult calculateWholeHouse(
            BigDecimal cRent,
            BigDecimal cRenovation,
            BigDecimal cEquipment,
            int contractMonths,
            BigDecimal oOperation,
            BigDecimal vRate,
            PricingMode mode,
            BigDecimal pDesired,
            BigDecimal roiExpected) {

        if (contractMonths < 1) {
            throw new BusinessException("Thời hạn hợp đồng phải ít nhất 1 tháng");
        }
        if (mode == PricingMode.FORWARD && pDesired == null) {
            throw new BusinessException("Luồng xuôi (FORWARD) yêu cầu pDesired");
        }
        if (mode == PricingMode.REVERSE && roiExpected == null) {
            throw new BusinessException("Luồng ngược (REVERSE) yêu cầu roiExpected");
        }

        BigDecimal safeRent = nz(cRent);
        BigDecimal safeRenovation = nz(cRenovation);
        BigDecimal safeEquipment = nz(cEquipment);
        BigDecimal safeOpex = nz(oOperation);
        BigDecimal safeVRate = vRate != null ? vRate : DEFAULT_V_RATE;

        BigDecimal capex = safeRent.add(safeRenovation).add(safeEquipment);
        BigDecimal monthlyRecovery = divideMoney(capex, contractMonths);
        BigDecimal fixedOpex = safeOpex.add(monthlyRecovery);
        BigDecimal floorPrice = applyVacancyBuffer(monthlyRecovery.add(safeOpex), safeVRate);

        BigDecimal revenueMin;
        BigDecimal revenueTarget;
        if (mode == PricingMode.FORWARD) {
            revenueMin = fixedOpex.add(nz(pDesired));
            revenueTarget = applyVacancyBuffer(revenueMin, safeVRate);
        } else {
            BigDecimal roi = nz(roiExpected);
            BigDecimal years = BigDecimal.valueOf(contractMonths)
                    .divide(BigDecimal.valueOf(12), RATIO_SCALE, RoundingMode.HALF_UP);
            BigDecimal totalProfit = capex
                    .multiply(roi)
                    .divide(BigDecimal.valueOf(100), RATIO_SCALE, RoundingMode.HALF_UP)
                    .multiply(years);
            BigDecimal monthlyGoal = capex.add(totalProfit)
                    .divide(BigDecimal.valueOf(contractMonths), RATIO_SCALE, RoundingMode.HALF_UP);
            revenueMin = monthlyGoal.add(safeOpex);
            revenueTarget = applyVacancyBuffer(revenueMin, safeVRate);
        }

        return PropertyResult.builder()
                .cRent(safeRent)
                .cRenovation(safeRenovation)
                .cEquipment(safeEquipment)
                .capex(capex)
                .contractMonths(contractMonths)
                .monthlyRecovery(monthlyRecovery)
                .fixedOpex(fixedOpex)
                .revenueMin(money(revenueMin))
                .revenueTarget(money(revenueTarget))
                .pDesired(pDesired)
                .roiExpected(roiExpected)
                .oOperation(safeOpex)
                .vRate(safeVRate)
                .mode(mode)
                .commonAreaM2(0)
                .totalWeight(0)
                .rooms(List.of(RoomResult.builder()
                        .rentShare(safeRent)
                        .renovationShare(safeRenovation)
                        .equipmentShare(safeEquipment)
                        .capexShare(capex)
                        .monthlyRecovery(monthlyRecovery)
                        .opexShare(safeOpex)
                        .roomFloor(floorPrice)
                        .suggestedPrice(money(revenueTarget))
                        .build()))
                .build();
    }

    private static void validateInputs(
            int contractMonths,
            PricingMode mode,
            BigDecimal pDesired,
            BigDecimal roiExpected,
            List<RoomInput> rooms) {
        if (contractMonths < 1) {
            throw new BusinessException("Thời hạn hợp đồng phải ít nhất 1 tháng");
        }
        if (rooms == null || rooms.isEmpty()) {
            throw new BusinessException("Phải có ít nhất một phòng trước khi tính giá theo phòng");
        }
        if (mode == PricingMode.FORWARD && pDesired == null) {
            throw new BusinessException("Luồng xuôi (FORWARD) yêu cầu pDesired");
        }
        if (mode == PricingMode.REVERSE && roiExpected == null) {
            throw new BusinessException("Luồng ngược (REVERSE) yêu cầu roiExpected");
        }
        for (RoomInput room : rooms) {
            if (room.area() <= 0) {
                throw new BusinessException(
                        "Phòng " + room.roomNumber() + " chưa có diện tích (m²) hợp lệ");
            }
            if (room.qualityFactor() <= 0) {
                throw new BusinessException("Hệ số chất lượng phòng phải > 0");
            }
        }
    }

    private static BigDecimal applyVacancyBuffer(BigDecimal base, BigDecimal vRate) {
        return money(base.multiply(BigDecimal.ONE.add(vRate)));
    }

    private static BigDecimal divideMoney(BigDecimal value, int divisor) {
        return value.divide(BigDecimal.valueOf(divisor), MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal ratio(double part, double total) {
        return BigDecimal.valueOf(part)
                .divide(BigDecimal.valueOf(total), RATIO_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private record WeightedRoom(RoomInput room, double effectiveM2, double weight) {
    }
}
