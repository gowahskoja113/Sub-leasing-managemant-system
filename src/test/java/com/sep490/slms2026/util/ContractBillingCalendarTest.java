package com.sep490.slms2026.util;

import com.sep490.slms2026.entity.TenantContract;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

class ContractBillingCalendarTest {

    @Test
    void billingDay_fromStartDate() {
        TenantContract c = TenantContract.builder()
                .startDate(LocalDate.of(2026, 4, 15))
                .moveInDate(LocalDate.of(2026, 4, 15))
                .build();
        assertEquals(15, ContractBillingCalendar.billingDayOfMonth(c));
    }

    @Test
    void issueAndDue_midMonth() {
        YearMonth aug = YearMonth.of(2026, 8);
        assertEquals(LocalDate.of(2026, 8, 12),
                ContractBillingCalendar.issueDate(aug, 15, 3));
        assertEquals(LocalDate.of(2026, 8, 17),
                ContractBillingCalendar.dueDate(aug, 15, 2));
    }

    @Test
    void clamp_february31() {
        YearMonth feb = YearMonth.of(2026, 2);
        assertEquals(28, ContractBillingCalendar.clampDay(feb, 31));
        assertEquals(LocalDate.of(2026, 2, 25),
                ContractBillingCalendar.issueDate(feb, 31, 3));
        assertEquals(LocalDate.of(2026, 2, 28),
                ContractBillingCalendar.dueDate(feb, 31, 2));
    }

    @Test
    void issueDate_clampsToFirstWhenLeadExceedsBillingDay() {
        YearMonth aug = YearMonth.of(2026, 8);
        assertEquals(LocalDate.of(2026, 8, 1),
                ContractBillingCalendar.issueDate(aug, 2, 3));
    }

    @Test
    void shouldIssue_skipsStartMonth() {
        TenantContract c = TenantContract.builder()
                .startDate(LocalDate.of(2026, 8, 15))
                .moveInDate(LocalDate.of(2026, 8, 15))
                .build();
        assertFalse(ContractBillingCalendar.shouldIssueRegularRent(
                LocalDate.of(2026, 8, 20), YearMonth.of(2026, 8), c, 3));
        assertTrue(ContractBillingCalendar.shouldIssueRegularRent(
                LocalDate.of(2026, 9, 12), YearMonth.of(2026, 9), c, 3));
        assertFalse(ContractBillingCalendar.shouldIssueRegularRent(
                LocalDate.of(2026, 9, 10), YearMonth.of(2026, 9), c, 3));
    }

    @Test
    void parsePeriod_isoAndVn() {
        assertEquals(YearMonth.of(2026, 8), ContractBillingCalendar.parsePeriod("2026-08").orElseThrow());
        assertEquals(YearMonth.of(2026, 8), ContractBillingCalendar.parsePeriod("08/2026").orElseThrow());
        assertEquals(YearMonth.of(2026, 8), ContractBillingCalendar.parsePeriod("2026/8").orElseThrow());
    }
}
