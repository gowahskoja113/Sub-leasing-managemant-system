package com.sep490.slms2026.util;

import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.enums.RentEscalationType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class AnnualCalendarEscalationTest {

    @Test
    void graceBoundaryJulyFirstEscalatesJulySecondDefers() {
        assertFalse(AnnualCalendarEscalation.isDeferredByGrace(
                LocalDate.of(2026, 7, 1), 2027, 6));
        assertTrue(AnnualCalendarEscalation.isDeferredByGrace(
                LocalDate.of(2026, 7, 2), 2027, 6));
        assertTrue(AnnualCalendarEscalation.isDeferredByGrace(
                LocalDate.of(2026, 12, 15), 2027, 6));
    }

    @Test
    void quotePriceYearUsesNovemberThresholdForLeadTwo() {
        assertEquals(2026, AnnualCalendarEscalation.quotePriceYear(LocalDate.of(2026, 10, 20), 2));
        assertEquals(2027, AnnualCalendarEscalation.quotePriceYear(LocalDate.of(2026, 11, 1), 2));
        assertEquals(2027, AnnualCalendarEscalation.quotePriceYear(LocalDate.of(2026, 12, 15), 2));
        assertEquals(2027, AnnualCalendarEscalation.quotePriceYear(LocalDate.of(2027, 1, 5), 2));
    }

    @Test
    void compoundFivePercent() {
        BigDecimal base = new BigDecimal("10000000");
        assertEquals(new BigDecimal("10500000"),
                AnnualCalendarEscalation.applyOneYearIncrease(base, new BigDecimal("5")));
        assertEquals(new BigDecimal("11025000"),
                AnnualCalendarEscalation.compound(base, new BigDecimal("5"), 2));
    }

    @Test
    void nextEscalationSkipsGraceYear() {
        TenantContract c = new TenantContract();
        c.setRentEscalationType(RentEscalationType.ANNUAL_CALENDAR);
        c.setRentEscalationPercent(new BigDecimal("5"));
        c.setRentAmount(new BigDecimal("10500000"));
        c.setStartDate(LocalDate.of(2026, 12, 15));

        LocalDate next = AnnualCalendarEscalation.nextEscalationDate(c, 6, LocalDate.of(2026, 12, 20));
        assertEquals(LocalDate.of(2028, 1, 1), next);
        assertEquals(new BigDecimal("11025000"),
                AnnualCalendarEscalation.nextEscalationAmount(c, 6, LocalDate.of(2026, 12, 20)));
    }

    @Test
    void zeroPercentNeverEscalates() {
        TenantContract c = new TenantContract();
        c.setRentEscalationType(RentEscalationType.ANNUAL_CALENDAR);
        c.setRentEscalationPercent(BigDecimal.ZERO);
        c.setStartDate(LocalDate.of(2026, 3, 10));
        assertNull(AnnualCalendarEscalation.nextEscalationDate(c, 6, LocalDate.of(2026, 6, 1)));
    }
}
