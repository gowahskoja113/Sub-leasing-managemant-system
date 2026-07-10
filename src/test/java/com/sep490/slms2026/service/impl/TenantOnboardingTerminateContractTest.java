package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.TerminateContractRequest;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Room;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.enums.ContractTerminationType;
import com.sep490.slms2026.enums.PropertyStatus;
import com.sep490.slms2026.enums.RoomStatus;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.repository.EquipmentRepository;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.RoomRepository;
import com.sep490.slms2026.repository.TenantContractRepository;
import com.sep490.slms2026.service.ContractEquipmentService;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenantOnboardingTerminateContractTest {

  @Mock private com.sep490.slms2026.repository.UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private PropertyRepository propertyRepository;
  @Mock private RoomRepository roomRepository;
  @Mock private TenantContractRepository tenantContractRepository;
  @Mock private com.sep490.slms2026.service.PayosService payosService;
  @Mock private com.sep490.slms2026.service.OtpService otpService;
  @Mock private EquipmentRepository equipmentRepository;
  @Mock private ContractEquipmentService contractEquipmentService;
  @Mock private com.sep490.slms2026.repository.NotificationRepository notificationRepository;
  @Mock private com.sep490.slms2026.service.PushNotificationService pushNotificationService;

  @InjectMocks private TenantOnboardingServiceImpl service;

  private TenantContract contract;
  private Room room;
  private Property property;

  @BeforeEach
  void setUp() {
    property = new Property();
    property.setId(1L);
    property.setWholeHouse(false);

    room = new Room();
    room.setId(5L);
    room.setStatus(RoomStatus.RENTED);
    room.setProperty(property);

    contract = TenantContract.builder()
        .id(42L)
        .property(property)
        .room(room)
        .contractCode("HD-MT-2026-00042")
        .status(ContractStatus.ACTIVE)
        .startDate(LocalDate.of(2026, 1, 1))
        .endDate(LocalDate.of(2027, 1, 1))
        .build();

    when(tenantContractRepository.findById(42L)).thenReturn(Optional.of(contract));
    when(tenantContractRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(equipmentRepository.findByDisabledByContractId(42L)).thenReturn(java.util.List.of());
    when(contractEquipmentService.mapSelectedToItems(any())).thenReturn(java.util.List.of());
    when(contractEquipmentService.mapAvailableToItems(any(), any())).thenReturn(java.util.List.of());
    when(contractEquipmentService.getSelectedIds(any())).thenReturn(java.util.List.of());
    when(contractEquipmentService.getSelectedExistingIds(any())).thenReturn(java.util.List.of());
    when(contractEquipmentService.getSelectedAddedIds(any())).thenReturn(java.util.List.of());
  }

  @Test
  void terminateActiveContract_freesRoomAndSetsTerminated() {
    TerminateContractRequest request = new TerminateContractRequest();
    request.setType(ContractTerminationType.EARLY_MOVE_OUT);
    request.setReason("Khách trả phòng sớm");
    request.setEffectiveDate(LocalDate.of(2026, 7, 10));

    var response = service.terminateActiveContract(42L, request);

    assertEquals(ContractStatus.TERMINATED, contract.getStatus());
    assertEquals(ContractTerminationType.EARLY_MOVE_OUT, contract.getTerminationType());
    assertEquals(LocalDate.of(2026, 7, 10), contract.getEndDate());
    assertEquals(RoomStatus.AVAILABLE, room.getStatus());
    assertEquals(ContractStatus.TERMINATED, response.getStatus());
    verify(roomRepository).save(room);
  }

  @Test
  void terminateActiveContract_rejectsDraft() {
    contract.setStatus(ContractStatus.DRAFT);
    TerminateContractRequest request = new TerminateContractRequest();
    request.setType(ContractTerminationType.VIOLATION);
    request.setReason("Vi phạm");

    assertThrows(BusinessException.class, () -> service.terminateActiveContract(42L, request));
  }

  @Test
  void cancelContract_rejectsActive() {
    assertThrows(BusinessException.class, () -> service.cancelContract(42L));
  }
}
