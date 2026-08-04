package com.sep490.slms2026.util;

import com.sep490.slms2026.entity.TenantContract;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TenantContractPaymentAmountsTest {

    @Test
    void initialPayment_rentPlusDepositTwoMonths() {
        TenantContract c = TenantContract.builder()
                .rentAmount(new BigDecimal("5000000"))
                .deposit(new BigDecimal("10000000"))
                .depositMonths(2)
                .build();

        assertEquals(0, new BigDecimal("15000000")
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
        assertEquals(0, new BigDecimal("15000000")
                .compareTo(TenantContractPaymentAmounts.resolveInitialPaymentAmount(c)));
    }
}
