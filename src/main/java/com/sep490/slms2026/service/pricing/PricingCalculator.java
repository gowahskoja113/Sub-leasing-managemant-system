package com.sep490.slms2026.service.pricing;

import com.sep490.slms2026.enums.PricingMode;
import com.sep490.slms2026.exception.BusinessException;
import lombok.Builder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
            BigDecimal monthlyRecoveryPrivate,
            BigDecimal repairReservePrivate,
            BigDecimal capexPrivate) {
    }

    @Builder
    public record RoomResult(
            Long roomId,
            String roomNumber,
            double area,
            double effectiveM2,
            double weight,
            BigDecimal capexShare,
            BigDecimal monthlyRecovery,
            BigDecimal opexShare,
            BigDecimal roomFloor,
            BigDecimal suggestedPrice) {
    }

    @Builder
    public record PropertyResult(
            BigDecimal capex,
            int contractMonths,
            BigDecimal monthlyRecovery,
            BigDecimal repairReserve,
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
            BigDecimal capexCommon,
            BigDecimal monthlyRecoveryCommon,
            BigDecimal repairReserveCommon,
            int contractMonths,
            BigDecimal oOperation,
            BigDecimal vRate,
            PricingMode mode,
            BigDecimal pDesired,
            BigDecimal roiExpected,
            List<RoomInput> rooms) {

        validateRoomInputs(contractMonths, mode, pDesired, roiExpected, rooms);

        List<RoomInput> ordered = rooms.stream()
                .sorted(Comparator.comparing(RoomInput::roomId))
                .toList();
        int n = ordered.size();

        BigDecimal safeOpex = nz(oOperation);
        BigDecimal safeVRate = vRate != null ? vRate : DEFAULT_V_RATE;

        BigDecimal equalMonthlyRecoveryCommon = equalShare(nz(monthlyRecoveryCommon), n);
        BigDecimal equalRepairReserveCommon = equalShare(nz(repairReserveCommon), n);
        BigDecimal equalOpex = equalShare(safeOpex, n);
        BigDecimal equalCapexCommon = equalShare(nz(capexCommon), n);

        BigDecimal allocatedMonthlyRecoveryCommon = BigDecimal.ZERO;
        BigDecimal allocatedRepairReserveCommon = BigDecimal.ZERO;
        BigDecimal allocatedOpex = BigDecimal.ZERO;
        BigDecimal allocatedCapexCommon = BigDecimal.ZERO;

        BigDecimal totalCapex = nz(capexCommon);
        BigDecimal totalMonthlyRecovery = nz(monthlyRecoveryCommon);
        BigDecimal totalRepairReserve = nz(repairReserveCommon);

        for (RoomInput r : ordered) {
            totalCapex = totalCapex.add(nz(r.capexPrivate()));
            totalMonthlyRecovery = totalMonthlyRecovery.add(nz(r.monthlyRecoveryPrivate()));
            totalRepairReserve = totalRepairReserve.add(nz(r.repairReservePrivate()));
        }

        BigDecimal totalRevenueMin = BigDecimal.ZERO;
        BigDecimal totalRevenueTarget = BigDecimal.ZERO;

        List<RoomResult> roomResults = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            RoomInput room = ordered.get(i);
            boolean last = i == n - 1;

            BigDecimal opexShare = last ? money(safeOpex.subtract(allocatedOpex)) : equalOpex;
            BigDecimal monthlyRecCommonShare = last ? money(nz(monthlyRecoveryCommon).subtract(allocatedMonthlyRecoveryCommon)) : equalMonthlyRecoveryCommon;
            BigDecimal repairResCommonShare = last ? money(nz(repairReserveCommon).subtract(allocatedRepairReserveCommon)) : equalRepairReserveCommon;
            BigDecimal capexCommonShare = last ? money(nz(capexCommon).subtract(allocatedCapexCommon)) : equalCapexCommon;

            if (!last) {
                allocatedOpex = allocatedOpex.add(opexShare);
                allocatedMonthlyRecoveryCommon = allocatedMonthlyRecoveryCommon.add(monthlyRecCommonShare);
                allocatedRepairReserveCommon = allocatedRepairReserveCommon.add(repairResCommonShare);
                allocatedCapexCommon = allocatedCapexCommon.add(capexCommonShare);
            }

            BigDecimal roomMonthlyRecovery = monthlyRecCommonShare.add(nz(room.monthlyRecoveryPrivate()));
            BigDecimal roomRepairReserve = repairResCommonShare.add(nz(room.repairReservePrivate()));
            BigDecimal roomCapex = capexCommonShare.add(nz(room.capexPrivate()));
            
            BigDecimal roomFixedOpex = roomMonthlyRecovery.add(roomRepairReserve).add(opexShare);
            BigDecimal roomFloor = applyVacancyBuffer(roomFixedOpex, safeVRate);

            BigDecimal roomRevenueMin;
            BigDecimal roomRevenueTarget;

            if (mode == PricingMode.FORWARD) {
                BigDecimal pDesiredShare = equalShare(nz(pDesired), n);
                if (last) {
                    pDesiredShare = nz(pDesired).subtract(equalShare(nz(pDesired), n).multiply(BigDecimal.valueOf(n - 1)));
                }
                roomRevenueMin = roomFixedOpex.add(pDesiredShare);
                roomRevenueTarget = applyVacancyBuffer(roomRevenueMin, safeVRate);
            } else {
                BigDecimal roi = nz(roiExpected);
                BigDecimal years = BigDecimal.valueOf(contractMonths).divide(BigDecimal.valueOf(12), RATIO_SCALE, RoundingMode.HALF_UP);
                BigDecimal totalProfitRoom = roomCapex.multiply(roi).divide(BigDecimal.valueOf(100), RATIO_SCALE, RoundingMode.HALF_UP).multiply(years);
                BigDecimal monthlyProfitRoom = totalProfitRoom.divide(BigDecimal.valueOf(contractMonths), RATIO_SCALE, RoundingMode.HALF_UP);
                roomRevenueMin = roomFixedOpex.add(monthlyProfitRoom);
                roomRevenueTarget = applyVacancyBuffer(roomRevenueMin, safeVRate);
            }

            totalRevenueMin = totalRevenueMin.add(roomRevenueMin);
            totalRevenueTarget = totalRevenueTarget.add(roomRevenueTarget);

            roomResults.add(RoomResult.builder()
                    .roomId(room.roomId())
                    .roomNumber(room.roomNumber())
                    .area(0)
                    .effectiveM2(0)
                    .weight(1.0)
                    .capexShare(roomCapex)
                    .monthlyRecovery(roomMonthlyRecovery)
                    .opexShare(opexShare)
                    .roomFloor(money(roomFloor))
                    .suggestedPrice(money(roomRevenueTarget))
                    .build());
        }

        return PropertyResult.builder()
                .capex(totalCapex)
                .contractMonths(contractMonths)
                .monthlyRecovery(totalMonthlyRecovery)
                .repairReserve(totalRepairReserve)
                .fixedOpex(totalMonthlyRecovery.add(totalRepairReserve).add(safeOpex))
                .revenueMin(money(totalRevenueMin))
                .revenueTarget(money(totalRevenueTarget))
                .pDesired(pDesired)
                .roiExpected(roiExpected)
                .oOperation(safeOpex)
                .vRate(safeVRate)
                .mode(mode)
                .commonAreaM2(0)
                .totalWeight(n)
                .rooms(roomResults)
                .build();
    }

    public static PropertyResult calculateWholeHouse(
            BigDecimal capex,
            BigDecimal monthlyRecovery,
            BigDecimal repairReserve,
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

        BigDecimal safeOpex = nz(oOperation);
        BigDecimal safeVRate = vRate != null ? vRate : DEFAULT_V_RATE;
        BigDecimal safeCapex = nz(capex);
        BigDecimal safeMonthlyRecovery = nz(monthlyRecovery);
        BigDecimal safeRepairReserve = nz(repairReserve);

        BigDecimal fixedOpex = safeMonthlyRecovery.add(safeRepairReserve).add(safeOpex);
        BigDecimal floorPrice = applyVacancyBuffer(fixedOpex, safeVRate);

        BigDecimal revenueMin;
        BigDecimal revenueTarget;
        if (mode == PricingMode.FORWARD) {
            revenueMin = fixedOpex.add(nz(pDesired));
            revenueTarget = applyVacancyBuffer(revenueMin, safeVRate);
        } else {
            BigDecimal roi = nz(roiExpected);
            BigDecimal years = BigDecimal.valueOf(contractMonths)
                    .divide(BigDecimal.valueOf(12), RATIO_SCALE, RoundingMode.HALF_UP);
            BigDecimal totalProfit = safeCapex
                    .multiply(roi)
                    .divide(BigDecimal.valueOf(100), RATIO_SCALE, RoundingMode.HALF_UP)
                    .multiply(years);
            BigDecimal monthlyProfit = totalProfit
                    .divide(BigDecimal.valueOf(contractMonths), RATIO_SCALE, RoundingMode.HALF_UP);
            revenueMin = fixedOpex.add(monthlyProfit);
            revenueTarget = applyVacancyBuffer(revenueMin, safeVRate);
        }

        return PropertyResult.builder()
                .capex(safeCapex)
                .contractMonths(contractMonths)
                .monthlyRecovery(safeMonthlyRecovery)
                .repairReserve(safeRepairReserve)
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
                        .capexShare(safeCapex)
                        .monthlyRecovery(safeMonthlyRecovery)
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
