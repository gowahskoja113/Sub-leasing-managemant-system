package com.sep490.slms2026.util;

import com.sep490.slms2026.entity.TenantContract;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TenantContractPaymentAmountsTest {

    @Test
    void initialPayment_depositOnlyWhenNoMoveInDate() {
        TenantContract c = TenantContract.builder()
                .rentAmount(new BigDecimal("5000000"))
                .deposit(new BigDecimal("10000000"))
                .depositMonths(2)
                .build();

        assertEquals(0, new BigDecimal("10000000")
                .compareTo(TenantContractPaymentAmounts.resolveInitialPaymentAmount(c)));
    }

    @Test
    void initialPayment_computesDepositFromMonthsWhenDepositMissing() {
        TenantContract c = TenantContract.builder()
                .rentAmount(new BigDecimal("5000000"))
                .depositMonths(2)
                .build();

        assertEquals(0, new BigDecimal("10000000")
                .compareTo(TenantContractPaymentAmounts.resolveDepositAmount(c)));
        assertEquals(0, new BigDecimal("10000000")
                .compareTo(TenantContractPaymentAmounts.resolveInitialPaymentAmount(c)));
    }

    @Test
    void initialPayment_includesProRataFirstRent() {
        TenantContract c = TenantContract.builder()
                .rentAmount(new BigDecimal("5000000"))
                .deposit(new BigDecimal("10000000"))
                .depositMonths(2)
                .moveInDate(LocalDate.of(2026, 4, 25))
                .startDate(LocalDate.of(2026, 4, 25))
                .build();

        assertEquals(0, new BigDecimal("1000000")
                .compareTo(TenantContractPaymentAmounts.resolveFirstRentAmount(c)));
        assertEquals(0, new BigDecimal("11000000")
                .compareTo(TenantContractPaymentAmounts.resolveInitialPaymentAmount(c)));
    }

    @Test
    void initialPayment_deferredFirstRent_depositOnly() {
        TenantContract c = TenantContract.builder()
                .rentAmount(new BigDecimal("5000000"))
                .deposit(new BigDecimal("10000000"))
                .moveInDate(LocalDate.of(2026, 4, 28))
                .startDate(LocalDate.of(2026, 4, 28))
                .build();

        assertEquals(0, BigDecimal.ZERO.compareTo(TenantContractPaymentAmounts.resolveFirstRentAmount(c)));
        assertEquals(0, new BigDecimal("10000000")
                .compareTo(TenantContractPaymentAmounts.resolveInitialPaymentAmount(c)));
    }
}
