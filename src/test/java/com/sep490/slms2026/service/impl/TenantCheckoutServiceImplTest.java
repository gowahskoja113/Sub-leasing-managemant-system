package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.CompleteCheckoutRequest;
import com.sep490.slms2026.dto.request.RejectCheckoutRequest;
import com.sep490.slms2026.dto.request.TerminateContractRequest;
import com.sep490.slms2026.entity.CheckoutRequest;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Tenant;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.enums.CheckoutRequestStatus;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.enums.ContractTerminationType;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.repository.CheckoutRequestRepository;
import com.sep490.slms2026.repository.TenantContractRepository;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.service.TenantOnboardingService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenantCheckoutServiceImplTest {

  @Mock private CheckoutRequestRepository checkoutRequestRepository;
  @Mock private TenantContractRepository tenantContractRepository;
  @Mock private UserRepository userRepository;
  @Mock private TenantOnboardingService tenantOnboardingService;
  @Mock private com.sep490.slms2026.repository.NotificationRepository notificationRepository;
  @Mock private com.sep490.slms2026.service.PushNotificationService pushNotificationService;
  @Mock private com.sep490.slms2026.repository.CheckoutSettlementRepository checkoutSettlementRepository;
  @Mock private com.sep490.slms2026.repository.TenantInvoiceRepository tenantInvoiceRepository;
  @Mock private com.sep490.slms2026.repository.DepositAuditLogRepository depositAuditLogRepository;

  @InjectMocks private TenantCheckoutServiceImpl service;

  private UUID tenantId;
  private UUID managerId;
  private TenantContract contract;
  private CheckoutRequest checkoutRequest;

  @BeforeEach
  void setUp() {
    tenantId = UUID.randomUUID();
    managerId = UUID.randomUUID();

    User tenantUser = new User();
    tenantUser.setId(tenantId);
    tenantUser.setFullName("Nguyen Van A");
    tenantUser.setPhoneNumber("0901111111");

    Tenant tenant = new Tenant();
    tenant.setId(tenantId);
    tenant.setUser(tenantUser);

    Property property = new Property();
    property.setId(1L);
    property.setPropertyName("Nha A");

    contract = TenantContract.builder()
        .id(10L)
        .contractCode("HD-MT-2026-00010")
        .property(property)
        .tenant(tenant)
        .status(ContractStatus.ACTIVE)
        .startDate(LocalDate.of(2026, 1, 1))
        .endDate(LocalDate.of(2027, 1, 1))
        .build();

    checkoutRequest = CheckoutRequest.builder()
        .id(5L)
        .tenantUserId(tenantId)
        .tenantContract(contract)
        .expectedMoveOutDate(LocalDate.of(2026, 7, 20))
        .reason("Chuyen cong tac")
        .status(CheckoutRequestStatus.APPROVED)
        .createdAt(LocalDateTime.now())
        .build();

    when(checkoutRequestRepository.findById(5L)).thenReturn(Optional.of(checkoutRequest));
    when(checkoutRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(userRepository.findById(managerId)).thenReturn(Optional.of(new User()));
    when(checkoutSettlementRepository.findByCheckoutRequestId(5L)).thenReturn(Optional.empty());
    when(tenantInvoiceRepository.findByTenantContractIdAndStatusNotIn(eq(10L), any()))
        .thenReturn(List.of());
  }

  @Test
  void completeRequest_terminatesContractAndMarksCompleted() {
    CompleteCheckoutRequest body = new CompleteCheckoutRequest();
    body.setActualMoveOutDate(LocalDate.of(2026, 7, 15));
    body.setNote("Da ban giao phong");

    var response = service.completeRequest(5L, managerId, body);

    ArgumentCaptor<TerminateContractRequest> captor = ArgumentCaptor.forClass(TerminateContractRequest.class);
    verify(tenantOnboardingService).terminateActiveContract(eq(10L), captor.capture());

    TerminateContractRequest terminate = captor.getValue();
    assertEquals(ContractTerminationType.EARLY_MOVE_OUT, terminate.getType());
    assertEquals(LocalDate.of(2026, 7, 15), terminate.getEffectiveDate());
    assertEquals(CheckoutRequestStatus.COMPLETED.name(), response.getStatus());
    assertEquals(CheckoutRequestStatus.COMPLETED, checkoutRequest.getStatus());
  }

  @Test
  void completeRequest_settlingWithoutRefund_stillCompletes() {
    checkoutRequest.setStatus(CheckoutRequestStatus.SETTLING);
    CompleteCheckoutRequest body = new CompleteCheckoutRequest();
    body.setActualMoveOutDate(LocalDate.of(2026, 7, 15));

    var response = service.completeRequest(5L, managerId, body);

    verify(tenantOnboardingService).terminateActiveContract(eq(10L), any());
    assertEquals(CheckoutRequestStatus.COMPLETED.name(), response.getStatus());
  }

  @Test
  void completeRequest_rejectsWhenUnpaidChargesRemain() {
    com.sep490.slms2026.entity.TenantInvoice unpaid = com.sep490.slms2026.entity.TenantInvoice.builder()
        .id(99L)
        .grandTotal(new java.math.BigDecimal("1500000"))
        .status(com.sep490.slms2026.enums.TenantInvoiceStatus.PENDING)
        .build();
    when(tenantInvoiceRepository.findByTenantContractIdAndStatusNotIn(eq(10L), any()))
        .thenReturn(List.of(unpaid));

    CompleteCheckoutRequest body = new CompleteCheckoutRequest();
    body.setActualMoveOutDate(LocalDate.of(2026, 7, 15));

    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.completeRequest(5L, managerId, body));
    assertEquals(
        "Khách còn nợ 1500000 — không thanh lý được hợp đồng khi chưa thu đủ.",
        ex.getMessage());
  }

  @Test
  void rejectRequest_requiresPending() {
    checkoutRequest.setStatus(CheckoutRequestStatus.APPROVED);
    RejectCheckoutRequest body = new RejectCheckoutRequest();
    body.setReason("Chua du dieu kien");

    assertThrows(BusinessException.class, () -> service.rejectRequest(5L, managerId, body));
  }

  @Test
  void approveRequest_movesPendingToApproved() {
    checkoutRequest.setStatus(CheckoutRequestStatus.PENDING);
    var response = service.approveRequest(5L, managerId, null);

    assertEquals(CheckoutRequestStatus.APPROVED.name(), response.getStatus());
    assertEquals(CheckoutRequestStatus.APPROVED, checkoutRequest.getStatus());
  }
}
