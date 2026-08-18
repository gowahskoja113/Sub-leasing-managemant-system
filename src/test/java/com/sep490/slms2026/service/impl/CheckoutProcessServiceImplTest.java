package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.CheckoutRefundRequest;
import com.sep490.slms2026.entity.CheckoutRequest;
import com.sep490.slms2026.entity.CheckoutSettlement;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.enums.CheckoutRequestStatus;
import com.sep490.slms2026.enums.DepositStatus;
import com.sep490.slms2026.enums.Role;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.repository.CheckoutRequestRepository;
import com.sep490.slms2026.repository.CheckoutSettlementRepository;
import com.sep490.slms2026.repository.NotificationRepository;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheckoutProcessServiceImplTest {

    @Mock private CheckoutRequestRepository checkoutRequestRepository;
    @Mock private CheckoutSettlementRepository checkoutSettlementRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private CheckoutProcessServiceImpl service;

    private CheckoutRequest checkoutRequest;
    private CheckoutSettlement settlement;
    private TenantContract contract;

    @BeforeEach
    void setUp() {
        authenticate(Role.ROLE_OWNER);

        Property property = new Property();
        property.setId(1L);
        property.setPropertyName("Nha A");

        contract = TenantContract.builder()
                .id(10L)
                .property(property)
                .deposit(new BigDecimal("5000000"))
                .build();

        checkoutRequest = CheckoutRequest.builder()
                .id(5L)
                .tenantUserId(UUID.randomUUID())
                .tenantContract(contract)
                .status(CheckoutRequestStatus.COMPLETED)
                .expectedMoveOutDate(LocalDate.of(2026, 8, 1))
                .reason("Tra phong")
                .build();

        settlement = CheckoutSettlement.builder()
                .id(1L)
                .checkoutRequest(checkoutRequest)
                .depositAmount(new BigDecimal("5000000"))
                .unpaidTotal(BigDecimal.ZERO)
                .damageTotal(BigDecimal.ZERO)
                .adjustmentTotal(BigDecimal.ZERO)
                .refundAmount(new BigDecimal("5000000"))
                .extraChargeAmount(BigDecimal.ZERO)
                .build();

        when(checkoutRequestRepository.findById(5L)).thenReturn(Optional.of(checkoutRequest));
        when(checkoutSettlementRepository.findByCheckoutRequestId(5L)).thenReturn(Optional.of(settlement));
        when(checkoutSettlementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(any())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void refund_completedCheckout_setsRefundPaidAt() {
        CheckoutRefundRequest body = refundBody();

        var response = service.refund(5L, body);

        assertEquals(DepositStatus.REFUNDED.name(), response.getStatus());
        assertEquals(LocalDate.of(2026, 8, 18), response.getRefundedAt());
        assertEquals(new BigDecimal("4800000"), settlement.getRefundAmount());
        assertEquals("BANK_TRANSFER", settlement.getRefundMethod());
        assertEquals("https://cdn/proof.jpg", settlement.getRefundProofUrl());
        assertNotNull(settlement.getRefundPaidAt());
        verify(notificationRepository).save(any());
    }

    @Test
    void refund_settlingCheckout_allowed() {
        checkoutRequest.setStatus(CheckoutRequestStatus.SETTLING);

        var response = service.refund(5L, refundBody());

        assertEquals(DepositStatus.REFUNDED.name(), response.getStatus());
        assertEquals(10L, response.getContractId());
    }

    @Test
    void refundByContractId_usesLatestCompletedCheckout() {
        when(checkoutRequestRepository.findByTenantContractIdOrderByCreatedAtDesc(10L))
                .thenReturn(List.of(checkoutRequest));

        var response = service.refundByContractId(10L, refundBody());

        assertEquals(5L, response.getCheckoutRequestId());
        assertEquals(DepositStatus.REFUNDED.name(), response.getStatus());
    }

    @Test
    void refund_inspectingStatus_rejected() {
        checkoutRequest.setStatus(CheckoutRequestStatus.INSPECTING);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.refund(5L, refundBody()));
        assertEquals("REFUND_NOT_ALLOWED", ex.getCode());
        verify(checkoutSettlementRepository, never()).save(any());
    }

    @Test
    void refund_alreadyRecorded_rejected() {
        settlement.setRefundPaidAt(java.time.LocalDateTime.now());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.refund(5L, refundBody()));
        assertEquals("REFUND_ALREADY_RECORDED", ex.getCode());
    }

    @Test
    void refund_zeroRemaining_rejected() {
        settlement.setRefundAmount(BigDecimal.ZERO);
        settlement.setExtraChargeAmount(new BigDecimal("1000000"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.refund(5L, refundBody()));
        assertEquals("REFUND_NOT_ALLOWED", ex.getCode());
    }

    @Test
    void refund_managerRole_denied() {
        authenticate(Role.ROLE_MANAGER);

        assertThrows(AccessDeniedException.class, () -> service.refund(5L, refundBody()));
        verify(checkoutSettlementRepository, never()).save(any());
    }

    private static CheckoutRefundRequest refundBody() {
        return CheckoutRefundRequest.builder()
                .amount(new BigDecimal("4800000"))
                .method("BANK_TRANSFER")
                .proofUrl("https://cdn/proof.jpg")
                .paidAt(LocalDate.of(2026, 8, 18))
                .build();
    }

    private static void authenticate(Role role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setRole(role);
        user.setUsername("tester");
        user.setPassword("x");
        user.setPhoneNumber("0900000000");
        user.setFullName("Tester");
        CustomUserDetails details = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }
}
