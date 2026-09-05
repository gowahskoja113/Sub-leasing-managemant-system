package com.sep490.slms2026.util;

import com.sep490.slms2026.entity.CheckoutSettlement;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.enums.CheckoutRequestStatus;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.enums.DepositStatus;
import com.sep490.slms2026.enums.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DepositLedgerStatusResolverTest {

    @Test
    void unpaidDepositIsNotCollectedEvenWhenTerminated() {
        TenantContract contract = TenantContract.builder()
                .status(ContractStatus.TERMINATED)
                .paymentStatus(PaymentStatus.PENDING)
                .deposit(new BigDecimal("20000000"))
                .build();

        assertEquals(DepositStatus.NOT_COLLECTED,
                DepositLedgerStatusResolver.resolve(contract, null, null));
    }

    @Test
    void draftPendingWithoutPaymentIsNotCollected() {
        TenantContract contract = TenantContract.builder()
                .status(ContractStatus.DRAFT)
                .paymentStatus(PaymentStatus.PENDING)
                .deposit(new BigDecimal("7300000"))
                .build();

        assertEquals(DepositStatus.NOT_COLLECTED,
                DepositLedgerStatusResolver.resolve(contract, null, null));
    }

    @Test
    void paidActiveContractIsHeld() {
        TenantContract contract = TenantContract.builder()
                .status(ContractStatus.ACTIVE)
                .paymentStatus(PaymentStatus.PAID)
                .deposit(new BigDecimal("9500000"))
                .build();

        assertEquals(DepositStatus.HELD,
                DepositLedgerStatusResolver.resolve(contract, null, null));
    }

    @Test
    void paidTerminatedWithoutSettlementStaysHeld() {
        TenantContract contract = TenantContract.builder()
                .status(ContractStatus.TERMINATED)
                .paymentStatus(PaymentStatus.PAID)
                .deposit(new BigDecimal("9500000"))
                .build();

        assertEquals(DepositStatus.HELD,
                DepositLedgerStatusResolver.resolve(contract, null, null));
    }

    @Test
    void refundedSettlementMarksRefunded() {
        TenantContract contract = TenantContract.builder()
                .status(ContractStatus.TERMINATED)
                .paymentStatus(PaymentStatus.PAID)
                .build();
        CheckoutSettlement settlement = CheckoutSettlement.builder()
                .refundAmount(new BigDecimal("5000000"))
                .extraChargeAmount(BigDecimal.ZERO)
                .damageTotal(BigDecimal.ZERO)
                .unpaidTotal(BigDecimal.ZERO)
                .refundPaidAt(LocalDateTime.now())
                .build();

        assertEquals(DepositStatus.REFUNDED,
                DepositLedgerStatusResolver.resolve(contract, settlement, CheckoutRequestStatus.COMPLETED));
    }

    @Test
    void completedSettlementWithDamageMarksForfeited() {
        TenantContract contract = TenantContract.builder()
                .status(ContractStatus.TERMINATED)
                .paymentStatus(PaymentStatus.PAID)
                .build();
        CheckoutSettlement settlement = CheckoutSettlement.builder()
                .depositAmount(new BigDecimal("9500000"))
                .refundAmount(BigDecimal.ZERO)
                .extraChargeAmount(BigDecimal.ZERO)
                .damageTotal(new BigDecimal("2000000"))
                .unpaidTotal(BigDecimal.ZERO)
                .build();

        assertEquals(DepositStatus.FORFEITED,
                DepositLedgerStatusResolver.resolve(contract, settlement, CheckoutRequestStatus.COMPLETED));
    }

    @Test
    void completedSettlementWithExtraChargeMarksForfeited() {
        TenantContract contract = TenantContract.builder()
                .status(ContractStatus.TERMINATED)
                .paymentStatus(PaymentStatus.PAID)
                .build();
        CheckoutSettlement settlement = CheckoutSettlement.builder()
                .depositAmount(new BigDecimal("9500000"))
                .refundAmount(BigDecimal.ZERO)
                .extraChargeAmount(new BigDecimal("1500000"))
                .damageTotal(new BigDecimal("9500000"))
                .unpaidTotal(BigDecimal.ZERO)
                .build();

        assertEquals(DepositStatus.FORFEITED,
                DepositLedgerStatusResolver.resolve(contract, settlement, CheckoutRequestStatus.COMPLETED));
    }
}
