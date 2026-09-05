package com.sep490.slms2026.util;

import com.sep490.slms2026.entity.InboundContract;
import com.sep490.slms2026.entity.Property;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InboundLeaseRulesRevenueWindowTest {

    @Test
    void endOfMonthKeepsTwentyFourAfterBuffer() {
        InboundContract lease = lease(LocalDate.of(2026, 5, 1), LocalDate.of(2028, 5, 31));
        var window = InboundLeaseRules.resolveRevenueWindow(lease, new Property(), LocalDate.of(2026, 5, 1));
        assertEquals(25, window.leaseMonths());
        assertEquals(25, window.rentableMonths());
        assertEquals(1, window.handoverBufferMonths());
        assertEquals(24, window.revenueMonths());
    }

    @Test
    void firstOfMonthGetsExplicitHandoverBuffer() {
        InboundContract lease = lease(LocalDate.of(2026, 5, 1), LocalDate.of(2028, 5, 1));
        var window = InboundLeaseRules.resolveRevenueWindow(lease, new Property(), LocalDate.of(2026, 5, 1));
        assertEquals(24, window.leaseMonths());
        assertEquals(24, window.rentableMonths());
        assertEquals(1, window.handoverBufferMonths());
        assertEquals(23, window.revenueMonths());
    }

    @Test
    void elapsedRenovationAndReviewAreExcludedFromDenominator() {
        InboundContract lease = lease(LocalDate.of(2026, 5, 1), LocalDate.of(2028, 5, 1));
        Property property = new Property();
        property.setRenovationEndDate(LocalDate.of(2026, 7, 1));
        var window = InboundLeaseRules.resolveRevenueWindow(lease, property, LocalDate.of(2026, 8, 1));
        assertEquals(LocalDate.of(2026, 8, 1), window.rentableFrom());
        assertEquals(21, window.rentableMonths());
        assertEquals(1, window.handoverBufferMonths());
        assertEquals(20, window.revenueMonths());
    }

    @Test
    void shortLeaseDoesNotApplyHandoverBuffer() {
        InboundContract lease = lease(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 9, 1));
        var window = InboundLeaseRules.resolveRevenueWindow(lease, new Property(), LocalDate.of(2026, 5, 1));
        assertEquals(4, window.leaseMonths());
        assertEquals(0, window.handoverBufferMonths());
        assertEquals(4, window.revenueMonths());
    }

    @Test
    void bufferOverrideIsAppliedWhenLeaseLongEnough() {
        InboundContract lease = lease(LocalDate.of(2026, 5, 1), LocalDate.of(2028, 5, 1));
        var window = InboundLeaseRules.resolveRevenueWindow(
                lease, new Property(), LocalDate.of(2026, 5, 1), 2);
        assertEquals(24, window.rentableMonths());
        assertEquals(2, window.handoverBufferMonths());
        assertEquals(22, window.revenueMonths());
    }

    @Test
    void bufferOverrideIgnoredOnShortLease() {
        InboundContract lease = lease(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 9, 1));
        var window = InboundLeaseRules.resolveRevenueWindow(
                lease, new Property(), LocalDate.of(2026, 5, 1), 3);
        assertEquals(0, window.handoverBufferMonths());
        assertEquals(4, window.revenueMonths());
    }

    private static InboundContract lease(LocalDate start, LocalDate end) {
        InboundContract contract = new InboundContract();
        contract.setStartDate(start);
        contract.setEndDate(end);
        return contract;
    }
}
