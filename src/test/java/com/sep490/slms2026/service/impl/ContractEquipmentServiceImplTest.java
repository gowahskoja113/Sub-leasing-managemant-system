package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.ContractAddedEquipmentRequest;
import com.sep490.slms2026.entity.Equipment;
import com.sep490.slms2026.entity.EquipmentCatalog;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.enums.EquipmentStatus;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.repository.EquipmentCatalogRepository;
import com.sep490.slms2026.repository.EquipmentRepository;
import com.sep490.slms2026.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractEquipmentServiceImplTest {

  @Mock private EquipmentRepository equipmentRepository;
  @Mock private EquipmentCatalogRepository equipmentCatalogRepository;
  @Mock private RoomRepository roomRepository;

  @InjectMocks private ContractEquipmentServiceImpl service;

  private Property property;
  private TenantContract contract;
  private Equipment bed;
  private Equipment fridge;

  @BeforeEach
  void setUp() {
    property = new Property();
    property.setId(1L);

    contract = TenantContract.builder().property(property).contractCode("HD-MT-2026-00001").build();

    bed = equipment(10L, "Giường", EquipmentStatus.GOOD);
    fridge = equipment(20L, "Tủ lạnh", EquipmentStatus.NEW);
  }

  @Test
  void applyHandover_nullSelection_autoSelectsAllExisting() {
    when(equipmentRepository.findActiveForTenantPlacement(1L, null)).thenReturn(List.of(bed, fridge));

    service.resolveAndApplyHandover(contract, null, null, null, null);

    assertEquals(List.of(10L, 20L), service.getSelectedIds(contract));
    assertEquals("Giường (Tốt) x1, Tủ lạnh (Mới) x1", contract.getEquipmentSnapshot());
  }

  @Test
  void applySelection_buildsSnapshotForSelectedOnly() {
    when(equipmentRepository.findActiveForTenantPlacement(1L, null)).thenReturn(List.of(bed, fridge));

    service.resolveAndApplySelection(contract, List.of(10L), null);

    assertEquals(List.of(10L), service.getSelectedIds(contract));
    assertEquals("Giường (Tốt) x1", contract.getEquipmentSnapshot());
  }

  @Test
  void applySelection_rejectsEquipmentOutsideScope() {
    when(equipmentRepository.findActiveForTenantPlacement(1L, null)).thenReturn(List.of(bed));

    assertThrows(
        BusinessException.class,
        () -> service.resolveAndApplySelection(contract, List.of(99L), null));
  }

  @Test
  void legacyDeclinedIds_computesSelectedAsScopeMinusDeclined() {
    when(equipmentRepository.findActiveForTenantPlacement(1L, null)).thenReturn(List.of(bed, fridge));

    service.resolveAndApplySelection(contract, null, List.of(20L));

    assertEquals(List.of(10L), service.getSelectedIds(contract));
    assertEquals("Giường (Tốt) x1", contract.getEquipmentSnapshot());
  }

  @Test
  void applyHandover_mergesExistingAndAddedIntoSnapshot() {
    when(equipmentRepository.findActiveForTenantPlacement(1L, null)).thenReturn(List.of(bed, fridge));

    ContractAddedEquipmentRequest added = new ContractAddedEquipmentRequest();
    added.setName("Tủ lạnh");
    added.setCategory("Điện lạnh");
    added.setCost(new java.math.BigDecimal("3500000"));

    when(equipmentCatalogRepository.findFirstByNameIgnoreCaseAndActiveTrue("Tủ lạnh"))
        .thenReturn(java.util.Optional.empty());
    when(equipmentCatalogRepository.save(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(
            inv -> {
              EquipmentCatalog c = inv.getArgument(0);
              c.setId(99L);
              return c;
            });
    when(equipmentRepository.save(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(
            inv -> {
              Equipment e = inv.getArgument(0);
              e.setId(55L);
              return e;
            });

    service.resolveAndApplyHandover(contract, List.of(10L), null, List.of(added), null);

    assertEquals(List.of(10L), service.getSelectedExistingIds(contract));
    assertEquals(List.of(55L), service.getSelectedAddedIds(contract));
    assertEquals(
        "Giường (Tốt) x1\nLắp thêm: Tủ lạnh (Mới) x1 — 3.500.000đ",
        contract.getEquipmentSnapshot());
  }

  @Test
  void availableItems_excludeAddedByTenant() {
    Equipment added =
        Equipment.builder()
            .id(30L)
            .catalog(bed.getCatalog())
            .source(com.sep490.slms2026.enums.EquipmentSource.ADDED_BY_TENANT)
            .status(EquipmentStatus.NEW)
            .build();
    when(equipmentRepository.findActiveForTenantPlacement(1L, null))
        .thenReturn(List.of(bed, added));

    var items = service.mapAvailableToItems(1L, null);

    assertEquals(1, items.size());
    assertEquals(10L, items.get(0).getId());
    assertEquals("EXISTING", items.get(0).getSource());
  }

  @Test
  void restoreDisabledByContract_reactivatesEquipment() {
    Equipment declined =
        Equipment.builder()
            .id(40L)
            .catalog(bed.getCatalog())
            .operationalStatus(com.sep490.slms2026.enums.EquipmentOperationalStatus.DISABLED)
            .disabledByContractId(99L)
            .disabledReason("Khách không nhận")
            .build();
    when(equipmentRepository.findByDisabledByContractId(99L)).thenReturn(List.of(declined));

    service.restoreDisabledByContract(99L);

    assertEquals(com.sep490.slms2026.enums.EquipmentOperationalStatus.ACTIVE, declined.getOperationalStatus());
    assertEquals(null, declined.getDisabledByContractId());
  }

  private static Equipment equipment(Long id, String name, EquipmentStatus status) {
    EquipmentCatalog catalog = new EquipmentCatalog();
    catalog.setName(name);
    return Equipment.builder().id(id).catalog(catalog).status(status).build();
  }
}
