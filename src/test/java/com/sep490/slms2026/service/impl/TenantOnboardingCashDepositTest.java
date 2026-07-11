package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Room;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.enums.OtpPurpose;
import com.sep490.slms2026.enums.PaymentStatus;
import com.sep490.slms2026.enums.RoomStatus;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.RoomRepository;
import com.sep490.slms2026.repository.TenantContractRepository;
import com.sep490.slms2026.service.ContractEquipmentService;
import com.sep490.slms2026.service.OtpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenantOnboardingCashDepositTest {

    @Mock private com.sep490.slms2026.repository.UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PropertyRepository propertyRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private TenantContractRepository tenantContractRepository;
    @Mock private com.sep490.slms2026.service.PayosService payosService;
    @Mock private OtpService otpService;
    @Mock private ContractEquipmentService contractEquipmentService;
    @Mock private com.sep490.slms2026.repository.NotificationRepository notificationRepository;
    @Mock private com.sep490.slms2026.service.PushNotificationService pushNotificationService;

    @InjectMocks private TenantOnboardingServiceImpl service;

    private TenantContract contract;

    @BeforeEach
    void setUp() {
        Property property = new Property();
        property.setId(1L);

        Room room = new Room();
        room.setId(5L);
        room.setStatus(RoomStatus.AVAILABLE);
        room.setProperty(property);

        contract = TenantContract.builder()
                .id(42L)
                .property(property)
                .room(room)
                .contractCode("HD-MT-2026-00042")
                .status(ContractStatus.DRAFT)
                .paymentStatus(PaymentStatus.PENDING)
                .rentAmount(new BigDecimal("5000000"))
                .deposit(new BigDecimal("5000000"))
                .moveInDate(LocalDate.now())
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusYears(1))
                .draftTenantPhone("0352393203")
                .build();

        when(tenantContractRepository.findById(42L)).thenReturn(Optional.of(contract));
        when(tenantContractRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tenantContractRepository.existsByRoomIdAndStatus(5L, ContractStatus.ACTIVE)).thenReturn(false);
        when(contractEquipmentService.mapSelectedToItems(any())).thenReturn(java.util.List.of());
        when(contractEquipmentService.mapAvailableToItems(any(), any())).thenReturn(java.util.List.of());
        when(contractEquipmentService.getSelectedIds(any())).thenReturn(java.util.List.of());
        when(contractEquipmentService.getSelectedExistingIds(any())).thenReturn(java.util.List.of());
        when(contractEquipmentService.getSelectedAddedIds(any())).thenReturn(java.util.List.of());
    }

    @Test
    void confirmDepositCashByTenant_rejectsWrongPhone() {
        assertThrows(BusinessException.class, () ->
                service.confirmDepositCashByTenant(42L, "0900000000"));
    }

    @Test
    void confirmDepositCashByTenant_setsTimestampOnly() {
        var response = service.confirmDepositCashByTenant(42L, "0352393203");

        assertNotNull(contract.getDepositCashTenantConfirmedAt());
        assertEquals(PaymentStatus.PENDING, contract.getPaymentStatus());
        assertEquals(false, response.getDepositCashManagerConfirmed());
        verify(otpService, never()).sendOtp(any(), any(), any());
    }

    @Test
    void confirmDepositCashByManager_afterTenant_marksPaidAndSendsOtp() {
        service.confirmDepositCashByTenant(42L, "0352393203");

        var response = service.confirmDepositCashByManager(42L);

        assertEquals(PaymentStatus.PAID, contract.getPaymentStatus());
        assertNotNull(contract.getPaidAt());
        assertEquals(ContractStatus.PENDING, contract.getStatus());
        assertEquals(true, response.getDepositCashTenantConfirmed());
        assertEquals(true, response.getDepositCashManagerConfirmed());
        verify(otpService).sendOtp(eq("0352393203"), eq(OtpPurpose.CONTRACT_CONFIRM), eq(42L));
    }

    @Test
    void confirmDepositCashByManager_beforeTenant_doesNotMarkPaid() {
        service.confirmDepositCashByManager(42L);

        assertNotNull(contract.getDepositCashManagerConfirmedAt());
        assertEquals(PaymentStatus.PENDING, contract.getPaymentStatus());
        verify(otpService, never()).sendOtp(any(), any(), any());
    }
}
