package com.sep490.slms2026.util;

import com.sep490.slms2026.entity.TenantContract;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

class RentFirstCycleCalculatorTest {

    @Test
    void proRata_month30_fromDay25() {
        // 25→30 inclusive = 6 days
        var r = RentFirstCycleCalculator.calculate(
                LocalDate.of(2026, 4, 25),
                null,
                new BigDecimal("5000000"),
                YearMonth.of(2026, 4));

        assertEquals("PRO_RATA", r.outcome());
        assertEquals(6, r.billedDays());
        assertEquals(30, r.daysInMonth());
        assertEquals(0, new BigDecimal("1000000").compareTo(r.amount())); // 5tr * 6 / 30
        assertTrue(r.proRated());
        assertFalse(r.deferredToNextMonth());
        assertNotNull(r.formula());
    }

    @Test
    void deferred_whenLastThreeDays() {
        var r = RentFirstCycleCalculator.calculate(
                LocalDate.of(2026, 4, 28),
                null,
                new BigDecimal("5000000"),
                YearMonth.of(2026, 4));

        assertEquals("DEFERRED", r.outcome());
        assertTrue(r.deferredToNextMonth());
        assertEquals(0, BigDecimal.ZERO.compareTo(r.amount()));
    }

    @Test
    void fullMonth_whenStartOnFirst() {
        var r = RentFirstCycleCalculator.calculate(
                LocalDate.of(2026, 4, 1),
                null,
                new BigDecimal("5000000"),
                YearMonth.of(2026, 4));

        assertEquals("FULL", r.outcome());
        assertEquals(0, new BigDecimal("5000000").compareTo(r.amount()));
        assertFalse(r.proRated());
    }

    @Test
    void onboardBreakdown_depositPlusProRata() {
        TenantContract c = TenantContract.builder()
                .rentAmount(new BigDecimal("5000000"))
                .depositMonths(2)
                .moveInDate(LocalDate.of(2026, 4, 25))
                .startDate(LocalDate.of(2026, 4, 25))
                .build();
        var b = PaymentBreakdownBuilder.forDepositOnboard(c);
        assertEquals("DEPOSIT_ONBOARD", b.getKind());
        assertEquals(0, new BigDecimal("11000000").compareTo(b.getTotalAmount()));
        assertNotNull(b.getFormula());
        assertFalse(b.getLines().isEmpty());
        assertTrue(b.getLines().stream().noneMatch(l -> "rentAmount".equals(l.getKey())));
        assertEquals(0, new BigDecimal("5000000").compareTo(b.getRentAmountMonthly()));
    }

    @Test
    void firstRentPreview_fromContract() {
        TenantContract c = TenantContract.builder()
                .rentAmount(new BigDecimal("5000000"))
                .moveInDate(LocalDate.of(2026, 4, 25))
                .startDate(LocalDate.of(2026, 4, 25))
                .build();
        var b = PaymentBreakdownBuilder.forFirstRentPreview(c);
        assertEquals("RENT_FIRST_PRO_RATA", b.getKind());
        assertEquals(Integer.valueOf(6), b.getBilledDays());
        assertEquals(0, new BigDecimal("1000000").compareTo(b.getTotalAmount()));
        assertEquals(Boolean.TRUE, b.getIncludesMoveInDay());
    }

    @Test
    void deferredCarryOver_addedToNextMonth_whenStartInLastThreeDays() {
        // startDate 29/07 → 3 ngày 29–31/07 gộp vào hoá đơn tháng 8
        TenantContract c = TenantContract.builder()
                .rentAmount(new BigDecimal("5000000"))
                .startDate(LocalDate.of(2026, 7, 29))
                .moveInDate(LocalDate.of(2026, 7, 29))
                .build();

        var carry = RentFirstCycleCalculator.deferredCarryOver(c, YearMonth.of(2026, 8));
        assertTrue(carry.present());
        assertEquals(3, carry.days());
        assertEquals(YearMonth.of(2026, 7), carry.fromMonth());
        // 5_000_000 * 3 / 31 = 483_871
        assertEquals(0, new BigDecimal("483871").compareTo(carry.amount()));
        assertEquals(0, new BigDecimal("5483871").compareTo(
                RentFirstCycleCalculator.regularRentAmount(c, YearMonth.of(2026, 8))));
    }

    @Test
    void deferredCarryOver_zero_whenStartNotInPreviousMonth() {
        TenantContract c = TenantContract.builder()
                .rentAmount(new BigDecimal("5000000"))
                .startDate(LocalDate.of(2026, 6, 20))
                .moveInDate(LocalDate.of(2026, 6, 20))
                .build();

        assertFalse(RentFirstCycleCalculator.deferredCarryOver(c, YearMonth.of(2026, 8)).present());
        assertEquals(0, new BigDecimal("5000000").compareTo(
                RentFirstCycleCalculator.regularRentAmount(c, YearMonth.of(2026, 8))));
    }

    @Test
    void deferredCarryOver_zero_whenPreviousMonthWasProRataNotDeferred() {
        TenantContract c = TenantContract.builder()
                .rentAmount(new BigDecimal("5000000"))
                .startDate(LocalDate.of(2026, 6, 20))
                .moveInDate(LocalDate.of(2026, 6, 20))
                .build();

        assertFalse(RentFirstCycleCalculator.deferredCarryOver(c, YearMonth.of(2026, 7)).present());
    }

    @Test
    void deferredCarryOver_zero_whenContractHasEndDate() {
        TenantContract c = TenantContract.builder()
                .rentAmount(new BigDecimal("5000000"))
                .startDate(LocalDate.of(2026, 7, 29))
                .moveInDate(LocalDate.of(2026, 7, 29))
                .endDate(LocalDate.of(2026, 12, 31))
                .build();

        assertFalse(RentFirstCycleCalculator.deferredCarryOver(c, YearMonth.of(2026, 8)).present());
    }

    @Test
    void regularRentDueDate_usesDay5_whenStillInTheFutureOrToday() {
        YearMonth aug = YearMonth.of(2026, 8);
        assertEquals(LocalDate.of(2026, 8, 5),
                RentFirstCycleCalculator.regularRentDueDate(aug, 5, 3, LocalDate.of(2026, 8, 1)));
        assertEquals(LocalDate.of(2026, 8, 5),
                RentFirstCycleCalculator.regularRentDueDate(aug, 5, 3, LocalDate.of(2026, 8, 5)));
    }

    @Test
    void regularRentDueDate_fallsBackToGrace_whenDay5AlreadyPassed() {
        YearMonth aug = YearMonth.of(2026, 8);
        assertEquals(LocalDate.of(2026, 8, 16),
                RentFirstCycleCalculator.regularRentDueDate(aug, 5, 3, LocalDate.of(2026, 8, 13)));
        assertEquals(LocalDate.of(2026, 8, 16),
                RentFirstCycleCalculator.regularRentDueDate(YearMonth.of(2026, 7), 5, 3, LocalDate.of(2026, 8, 13)));
    }
}
