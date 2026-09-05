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
 * Công thức định giá — prepaid rent.
 * <p>
 * Nhà nguyên căn: tính trực tiếp revenueTarget.
 * Theo phòng: tính giống nguyên căn rồi chia đều cho các phòng.
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
            String roomNumber) {
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

    /**
     * Tính giá theo phòng: cùng công thức nguyên căn, rồi chia đều revenueTarget / số phòng.
     * Phòng cuối nhận phần còn lại để tránh lệch làm tròn.
     */
    public static PropertyResult calculate(
            BigDecimal cRent,
            BigDecimal cRenovation,
            BigDecimal cEquipment,
            int contractMonths,
            BigDecimal oOperation,
            BigDecimal vRate,
            PricingMode mode,
            BigDecimal pDesired,
            BigDecimal roiExpected,
            List<RoomInput> rooms) {

        validateRoomInputs(contractMonths, mode, pDesired, roiExpected, rooms);

        PropertyResult whole = calculateWholeHouse(
                cRent, cRenovation, cEquipment, contractMonths,
                oOperation, vRate, mode, pDesired, roiExpected);

        List<RoomInput> ordered = rooms.stream()
                .sorted(Comparator.comparing(RoomInput::roomId))
                .toList();
        int n = ordered.size();

        BigDecimal allocatedRent = BigDecimal.ZERO;
        BigDecimal allocatedRenovation = BigDecimal.ZERO;
        BigDecimal allocatedEquipment = BigDecimal.ZERO;
        BigDecimal allocatedOpex = BigDecimal.ZERO;
        BigDecimal allocatedPrice = BigDecimal.ZERO;
        BigDecimal allocatedFloor = BigDecimal.ZERO;

        BigDecimal equalRent = equalShare(whole.cRent(), n);
        BigDecimal equalRenovation = equalShare(whole.cRenovation(), n);
        BigDecimal equalEquipment = equalShare(whole.cEquipment(), n);
        BigDecimal equalOpex = equalShare(whole.oOperation(), n);
        BigDecimal equalPrice = equalShare(whole.revenueTarget(), n);
        BigDecimal wholeFloor = whole.rooms().getFirst().roomFloor();
        BigDecimal equalFloor = equalShare(wholeFloor, n);

        List<RoomResult> roomResults = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            RoomInput room = ordered.get(i);
            boolean last = i == n - 1;

            BigDecimal rentShare = last ? money(whole.cRent().subtract(allocatedRent)) : equalRent;
            BigDecimal renovationShare = last
                    ? money(whole.cRenovation().subtract(allocatedRenovation)) : equalRenovation;
            BigDecimal equipmentShare = last
                    ? money(whole.cEquipment().subtract(allocatedEquipment)) : equalEquipment;
            BigDecimal opexShare = last
                    ? money(whole.oOperation().subtract(allocatedOpex)) : equalOpex;
            BigDecimal suggestedPrice = last
                    ? money(whole.revenueTarget().subtract(allocatedPrice)) : equalPrice;
            BigDecimal roomFloor = last
                    ? money(wholeFloor.subtract(allocatedFloor)) : equalFloor;

            BigDecimal capexShare = rentShare.add(renovationShare).add(equipmentShare);
            BigDecimal monthlyRecoveryRoom = divideMoney(capexShare, contractMonths);

            if (!last) {
                allocatedRent = allocatedRent.add(rentShare);
                allocatedRenovation = allocatedRenovation.add(renovationShare);
                allocatedEquipment = allocatedEquipment.add(equipmentShare);
                allocatedOpex = allocatedOpex.add(opexShare);
                allocatedPrice = allocatedPrice.add(suggestedPrice);
                allocatedFloor = allocatedFloor.add(roomFloor);
            }

            roomResults.add(RoomResult.builder()
                    .roomId(room.roomId())
                    .roomNumber(room.roomNumber())
                    .area(0)
                    .effectiveM2(0)
                    .weight(1.0)
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
                .cRent(whole.cRent())
                .cRenovation(whole.cRenovation())
                .cEquipment(whole.cEquipment())
                .capex(whole.capex())
                .contractMonths(whole.contractMonths())
                .monthlyRecovery(whole.monthlyRecovery())
                .fixedOpex(whole.fixedOpex())
                .revenueMin(whole.revenueMin())
                .revenueTarget(whole.revenueTarget())
                .pDesired(whole.pDesired())
                .roiExpected(whole.roiExpected())
                .oOperation(whole.oOperation())
                .vRate(whole.vRate())
                .mode(whole.mode())
                .commonAreaM2(0)
                .totalWeight(n)
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

    private static void validateRoomInputs(
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
    }

    private static BigDecimal applyVacancyBuffer(BigDecimal base, BigDecimal vRate) {
        if (vRate.compareTo(BigDecimal.ONE) >= 0) {
            throw new BusinessException("Biên dự phòng trống (vRate) phải nhỏ hơn 100%");
        }
        return money(base.divide(BigDecimal.ONE.subtract(vRate), MONEY_SCALE, RoundingMode.HALF_UP));
    }

    private static BigDecimal divideMoney(BigDecimal value, int divisor) {
        return value.divide(BigDecimal.valueOf(divisor), MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal equalShare(BigDecimal total, int n) {
        return total.divide(BigDecimal.valueOf(n), MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
