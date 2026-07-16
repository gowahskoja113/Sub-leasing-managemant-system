package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.ContractAddedEquipmentRequest;
import com.sep490.slms2026.dto.response.TenantContractDetailResponse.EquipmentItem;
import com.sep490.slms2026.entity.Equipment;
import com.sep490.slms2026.entity.EquipmentCatalog;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Room;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.entity.TenantContractEquipment;
import com.sep490.slms2026.enums.ContractEquipmentSource;
import com.sep490.slms2026.enums.EquipmentOperationalStatus;
import com.sep490.slms2026.enums.EquipmentPlacementScope;
import com.sep490.slms2026.enums.EquipmentSource;
import com.sep490.slms2026.enums.EquipmentStatus;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.EquipmentCatalogRepository;
import com.sep490.slms2026.repository.EquipmentRepository;
import com.sep490.slms2026.repository.RoomRepository;
import com.sep490.slms2026.service.ContractEquipmentService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContractEquipmentServiceImpl implements ContractEquipmentService {

  private final EquipmentRepository equipmentRepository;
  private final EquipmentCatalogRepository equipmentCatalogRepository;
  private final RoomRepository roomRepository;

  @Override
  public List<Equipment> findExistingInventory(Long propertyId, Long roomId) {
    return findInScope(propertyId, roomId).stream()
        .filter(eq -> eq.getSource() != EquipmentSource.ADDED_BY_TENANT)
        .toList();
  }

  @Override
  public List<Equipment> findInScope(Long propertyId, Long roomId) {
    return equipmentRepository.findActiveForTenantPlacement(propertyId, roomId);
  }

  @Override
  public void resolveAndApplyHandover(
      TenantContract contract,
      List<Long> selectedEquipmentIds,
      List<Long> declinedEquipmentIds,
      List<ContractAddedEquipmentRequest> addedEquipments,
      List<Long> addedEquipmentIds) {
    boolean touchAdded = addedEquipments != null || addedEquipmentIds != null;

    Long propertyId = contract.getProperty().getId();
    Long roomId = contract.getRoom() != null ? contract.getRoom().getId() : null;
    List<Equipment> existingInventory = findExistingInventory(propertyId, roomId);
    Map<Long, Equipment> existingById =
        existingInventory.stream().collect(Collectors.toMap(Equipment::getId, e -> e, (a, b) -> a));

    List<TenantContractEquipment> previousAdded = collectAddedSelections(contract);

    // EXISTING: bỏ checkbox → mặc định lấy hết nội thất ACTIVE trong nhà/phòng.
    // FE vẫn có thể gửi selectedEquipmentIds / declinedEquipmentIds (legacy).
    List<Long> resolvedExisting =
        resolveExistingSelected(selectedEquipmentIds, declinedEquipmentIds, existingInventory);
    validateSelectedIds(resolvedExisting, existingById.keySet());

    List<TenantContractEquipment> nextExisting = new ArrayList<>();
    for (Long id : resolvedExisting) {
      Equipment eq = existingById.get(id);
      nextExisting.add(linkEquipment(contract, eq, eq.getStatus()));
    }

    List<TenantContractEquipment> nextAdded = new ArrayList<>();
    List<Equipment> orphanAddedToDelete = List.of();
    if (touchAdded) {
      if (addedEquipments != null) {
        for (ContractAddedEquipmentRequest req : addedEquipments) {
          Equipment created = createAddedEquipment(contract, req);
          nextAdded.add(linkEquipment(contract, created, resolveCondition(req)));
        }
      }
      if (addedEquipmentIds != null) {
        for (Equipment eq : loadAddedEquipmentRefs(contract, addedEquipmentIds)) {
          nextAdded.add(linkEquipment(contract, eq, eq.getStatus()));
        }
      }
      Set<Long> keptAddedEquipmentIds =
          nextAdded.stream().map(tce -> tce.getEquipment().getId()).collect(Collectors.toSet());
      orphanAddedToDelete =
          previousAdded.stream()
              .map(TenantContractEquipment::getEquipment)
              .filter(eq -> !keptAddedEquipmentIds.contains(eq.getId()))
              .toList();
    } else {
      nextAdded.addAll(previousAdded);
    }

    List<TenantContractEquipment> desired = new ArrayList<>(nextExisting.size() + nextAdded.size());
    desired.addAll(nextExisting);
    desired.addAll(nextAdded);
    // Diff-based: giữ TenantContractEquipment đã persist — tránh clear+addAll cùng equipment_id
    // (Hibernate INSERT trước DELETE → unique (tenant_contract_id, equipment_id)).
    syncSelectedEquipments(contract, desired);
    contract.setEquipmentSnapshot(buildEquipmentSnapshot(contract.getSelectedEquipments()));

    if (!orphanAddedToDelete.isEmpty()) {
      equipmentRepository.deleteAll(orphanAddedToDelete);
    }
  }

  /**
   * Đồng bộ collection theo equipment_id: giữ entity cũ, chỉ remove/add phần diff.
   */
  private void syncSelectedEquipments(TenantContract contract, List<TenantContractEquipment> desired) {
    List<TenantContractEquipment> selected = contract.getSelectedEquipments();
    Map<Long, TenantContractEquipment> currentByEqId =
        selected.stream()
            .collect(
                Collectors.toMap(
                    tce -> tce.getEquipment().getId(), tce -> tce, (a, b) -> a));

    Map<Long, TenantContractEquipment> nextByEqId = new LinkedHashMap<>();
    for (TenantContractEquipment link : desired) {
      Long eqId = link.getEquipment().getId();
      TenantContractEquipment existing = currentByEqId.get(eqId);
      if (existing != null) {
        existing.setConditionAtSigning(link.getConditionAtSigning());
        existing.setQuantity(link.getQuantity() != null ? link.getQuantity() : 1);
        nextByEqId.put(eqId, existing);
      } else {
        nextByEqId.put(eqId, link);
      }
    }

    selected.removeIf(tce -> !nextByEqId.containsKey(tce.getEquipment().getId()));
    for (TenantContractEquipment link : nextByEqId.values()) {
      if (!selected.contains(link)) {
        selected.add(link);
      }
    }
  }

  @Override
  public void disableDeclinedForActiveContract(TenantContract contract) {
    Long propertyId = contract.getProperty().getId();
    Long roomId = contract.getRoom() != null ? contract.getRoom().getId() : null;
    Set<Long> selectedExistingIds = new HashSet<>(getSelectedExistingIds(contract));

    List<Equipment> toDisable =
        findExistingInventory(propertyId, roomId).stream()
            .filter(eq -> !selectedExistingIds.contains(eq.getId()))
            .filter(eq -> eq.getOperationalStatus() == EquipmentOperationalStatus.ACTIVE)
            .toList();

    if (toDisable.isEmpty()) {
      return;
    }

    LocalDateTime now = LocalDateTime.now();
    String reason = "Khách không nhận · HĐ " + contract.getContractCode();
    for (Equipment eq : toDisable) {
      eq.setOperationalStatus(EquipmentOperationalStatus.DISABLED);
      eq.setDisabledAt(now);
      eq.setDisabledReason(reason);
      eq.setDisabledByContractId(contract.getId());
    }
    equipmentRepository.saveAll(toDisable);
  }

  @Override
  public void restoreDisabledByContract(Long contractId) {
    if (contractId == null) {
      return;
    }
    List<Equipment> disabled = equipmentRepository.findByDisabledByContractId(contractId);
    if (disabled.isEmpty()) {
      return;
    }
    for (Equipment eq : disabled) {
      eq.setOperationalStatus(EquipmentOperationalStatus.ACTIVE);
      eq.setDisabledAt(null);
      eq.setDisabledReason(null);
      eq.setDisabledByContractId(null);
    }
    equipmentRepository.saveAll(disabled);
  }

  @Override
  public List<EquipmentItem> mapSelectedToItems(TenantContract contract) {
    if (contract.getSelectedEquipments() == null || contract.getSelectedEquipments().isEmpty()) {
      return List.of();
    }
    return contract.getSelectedEquipments().stream().map(this::toEquipmentItem).toList();
  }

  @Override
  public List<EquipmentItem> mapAvailableToItems(Long propertyId, Long roomId) {
    return findExistingInventory(propertyId, roomId).stream().map(this::toEquipmentItem).toList();
  }

  @Override
  public List<Long> getSelectedIds(TenantContract contract) {
    if (contract.getSelectedEquipments() == null || contract.getSelectedEquipments().isEmpty()) {
      return List.of();
    }
    return contract.getSelectedEquipments().stream()
        .map(tce -> tce.getEquipment().getId())
        .toList();
  }

  @Override
  public List<Long> getSelectedExistingIds(TenantContract contract) {
    return filterSelectedBySource(contract, ContractEquipmentSource.EXISTING);
  }

  @Override
  public List<Long> getSelectedAddedIds(TenantContract contract) {
    return filterSelectedBySource(contract, ContractEquipmentSource.ADDED);
  }

  @Override
  public String resolveEquipmentSnapshot(TenantContract contract) {
    if (contract == null) {
      return "";
    }
    String stored = contract.getEquipmentSnapshot();
    if (stored != null && !stored.isBlank()) {
      return stored.trim();
    }
    if (contract.getSelectedEquipments() != null && !contract.getSelectedEquipments().isEmpty()) {
      String fromSelected = buildEquipmentSnapshot(contract.getSelectedEquipments());
      if (!fromSelected.isBlank()) {
        return fromSelected;
      }
    }
    Long propertyId = contract.getProperty().getId();
    Long roomId = contract.getRoom() != null ? contract.getRoom().getId() : null;
    return buildInventorySnapshot(propertyId, roomId);
  }

  @Override
  public String buildEquipmentSnapshot(List<TenantContractEquipment> selected) {
    if (selected == null || selected.isEmpty()) {
      return "";
    }

    List<String> existingLines = new ArrayList<>();
    List<String> addedLines = new ArrayList<>();
    for (TenantContractEquipment tce : selected) {
      String line = formatSnapshotLine(tce);
      if (resolveContractSource(tce.getEquipment()) == ContractEquipmentSource.ADDED) {
        addedLines.add(line);
      } else {
        existingLines.add(line);
      }
    }

    StringBuilder sb = new StringBuilder();
    if (!existingLines.isEmpty()) {
      sb.append(String.join(", ", existingLines));
    }
    if (!addedLines.isEmpty()) {
      if (!sb.isEmpty()) {
        sb.append("\n");
      }
      sb.append("Lắp thêm: ").append(String.join(", ", addedLines));
    }
    return sb.toString();
  }

  private String buildInventorySnapshot(Long propertyId, Long roomId) {
    List<Equipment> inventory = findExistingInventory(propertyId, roomId);
    if (inventory.isEmpty()) {
      return "";
    }
    List<TenantContractEquipment> links = new ArrayList<>();
    for (Equipment equipment : inventory) {
      links.add(
          TenantContractEquipment.builder()
              .equipment(equipment)
              .conditionAtSigning(equipment.getStatus())
              .quantity(1)
              .build());
    }
    return buildEquipmentSnapshot(links);
  }

  private List<Long> filterSelectedBySource(TenantContract contract, ContractEquipmentSource source) {
    if (contract.getSelectedEquipments() == null) {
      return List.of();
    }
    return contract.getSelectedEquipments().stream()
        .filter(tce -> resolveContractSource(tce.getEquipment()) == source)
        .map(tce -> tce.getEquipment().getId())
        .toList();
  }

  private List<TenantContractEquipment> collectAddedSelections(TenantContract contract) {
    if (contract.getSelectedEquipments() == null) {
      return List.of();
    }
    return contract.getSelectedEquipments().stream()
        .filter(tce -> resolveContractSource(tce.getEquipment()) == ContractEquipmentSource.ADDED)
        .toList();
  }

  private List<Long> resolveExistingSelected(
      List<Long> selectedEquipmentIds, List<Long> declinedEquipmentIds, List<Equipment> existingInventory) {
    if (selectedEquipmentIds != null) {
      return selectedEquipmentIds;
    }
    if (declinedEquipmentIds != null) {
      Set<Long> declined = new HashSet<>(declinedEquipmentIds);
      return existingInventory.stream()
          .map(Equipment::getId)
          .filter(id -> !declined.contains(id))
          .toList();
    }
    // Mặc định: toàn bộ nội thất ACTIVE trong nhà/phòng đã chọn
    return existingInventory.stream().map(Equipment::getId).toList();
  }

  private void validateSelectedIds(List<Long> selectedIds, Set<Long> scopeIds) {
    for (Long id : selectedIds) {
      if (!scopeIds.contains(id)) {
        throw new BusinessException("Thiết bị ID " + id + " không thuộc phạm vi hợp đồng");
      }
    }
    if (selectedIds.size() != new LinkedHashSet<>(selectedIds).size()) {
      throw new BusinessException("Danh sách thiết bị bàn giao có ID trùng lặp");
    }
  }

  private List<Equipment> loadAddedEquipmentRefs(TenantContract contract, List<Long> addedEquipmentIds) {
    if (addedEquipmentIds.isEmpty()) {
      return List.of();
    }
    if (addedEquipmentIds.size() != new LinkedHashSet<>(addedEquipmentIds).size()) {
      throw new BusinessException("Danh sách thiết bị lắp thêm có ID trùng lặp");
    }

    Long propertyId = contract.getProperty().getId();
    Long contractRoomId = contract.getRoom() != null ? contract.getRoom().getId() : null;
    List<Equipment> loaded = equipmentRepository.findAllById(addedEquipmentIds);
    Map<Long, Equipment> byId = loaded.stream().collect(Collectors.toMap(Equipment::getId, e -> e));

    List<Equipment> result = new ArrayList<>();
    for (Long id : addedEquipmentIds) {
      Equipment eq = byId.get(id);
      if (eq == null) {
        throw new BusinessException("Không tìm thấy thiết bị lắp thêm ID " + id);
      }
      if (eq.getSource() != EquipmentSource.ADDED_BY_TENANT) {
        throw new BusinessException("Thiết bị ID " + id + " không phải loại lắp thêm (ADDED)");
      }
      assertEquipmentInContractScope(eq, propertyId, contractRoomId);
      result.add(eq);
    }
    return result;
  }

  private Equipment createAddedEquipment(TenantContract contract, ContractAddedEquipmentRequest request) {
    Property property = contract.getProperty();
    Long contractRoomId = contract.getRoom() != null ? contract.getRoom().getId() : null;
    Long targetRoomId = request.getRoomId() != null ? request.getRoomId() : contractRoomId;

    Room room = null;
    if (targetRoomId != null) {
      room =
          roomRepository
              .findByIdAndPropertyIdAndDeletedIsFalse(targetRoomId, property.getId())
              .orElseThrow(
                  () -> new ResourceNotFoundException("Không tìm thấy phòng ID=" + targetRoomId));
    }

    EquipmentCatalog catalog =
        equipmentCatalogRepository
            .findFirstByNameIgnoreCaseAndActiveTrue(request.getName())
            .orElseGet(
                () ->
                    equipmentCatalogRepository.save(
                        EquipmentCatalog.builder()
                            .name(request.getName())
                            .description(request.getCategory())
                            .active(true)
                            .build()));

    EquipmentStatus status = resolveCondition(request);
    BigDecimal cost = request.getCost() != null ? request.getCost() : BigDecimal.ZERO;

    Equipment equipment =
        Equipment.builder()
            .property(property)
            .room(room)
            .catalog(catalog)
            .equipmentName(request.getName())
            .equipmentCategory(request.getCategory())
            .source(EquipmentSource.ADDED_BY_TENANT)
            .status(status)
            .operationalStatus(EquipmentOperationalStatus.ACTIVE)
            .price(cost)
            .build();

    return equipmentRepository.save(equipment);
  }

  private static EquipmentStatus resolveCondition(ContractAddedEquipmentRequest request) {
    return request.getCondition() != null ? request.getCondition() : EquipmentStatus.NEW;
  }

  private static TenantContractEquipment linkEquipment(
      TenantContract contract, Equipment equipment, EquipmentStatus condition) {
    return TenantContractEquipment.builder()
        .tenantContract(contract)
        .equipment(equipment)
        .conditionAtSigning(condition)
        .quantity(1)
        .build();
  }

  private void assertEquipmentInContractScope(
      Equipment equipment, Long propertyId, Long contractRoomId) {
    if (!equipment.getProperty().getId().equals(propertyId)) {
      throw new BusinessException("Thiết bị ID " + equipment.getId() + " không thuộc tòa nhà của hợp đồng");
    }
    if (contractRoomId != null) {
      if (equipment.getRoom() != null && !equipment.getRoom().getId().equals(contractRoomId)) {
        throw new BusinessException("Thiết bị ID " + equipment.getId() + " không thuộc phòng của hợp đồng");
      }
    }
  }

  private EquipmentItem toEquipmentItem(Equipment eq) {
    return EquipmentItem.builder()
        .id(eq.getId())
        .name(resolveEquipmentName(eq))
        .condition(eq.getStatus() != null ? eq.getStatus().name() : EquipmentStatus.GOOD.name())
        .quantity(1)
        .source(resolveContractSource(eq).name())
        .scope(resolvePlacementScope(eq).name())
        .roomNumber(eq.getRoom() != null ? eq.getRoom().getRoomNumber() : null)
        .houseArea(eq.getHouseArea())
        .cost(eq.getSource() == EquipmentSource.ADDED_BY_TENANT ? eq.getPrice() : null)
        .build();
  }

  private EquipmentItem toEquipmentItem(TenantContractEquipment tce) {
    Equipment eq = tce.getEquipment();
    EquipmentStatus condition =
        tce.getConditionAtSigning() != null ? tce.getConditionAtSigning() : eq.getStatus();
    return EquipmentItem.builder()
        .id(eq.getId())
        .name(resolveEquipmentName(eq))
        .condition(condition != null ? condition.name() : EquipmentStatus.GOOD.name())
        .quantity(tce.getQuantity() != null ? tce.getQuantity() : 1)
        .source(resolveContractSource(eq).name())
        .scope(resolvePlacementScope(eq).name())
        .roomNumber(eq.getRoom() != null ? eq.getRoom().getRoomNumber() : null)
        .houseArea(eq.getHouseArea())
        .cost(eq.getSource() == EquipmentSource.ADDED_BY_TENANT ? eq.getPrice() : null)
        .build();
  }

  private String formatSnapshotLine(TenantContractEquipment tce) {
    Equipment eq = tce.getEquipment();
    String name = resolveEquipmentName(eq);
    EquipmentStatus condition =
        tce.getConditionAtSigning() != null ? tce.getConditionAtSigning() : eq.getStatus();
    int qty = tce.getQuantity() != null ? tce.getQuantity() : 1;
    String line = name + " (" + formatCondition(condition) + ") x" + qty;
    if (eq.getSource() == EquipmentSource.ADDED_BY_TENANT
        && eq.getPrice() != null
        && eq.getPrice().signum() > 0) {
      line += " — " + formatMoney(eq.getPrice()) + "đ";
    }
    return line;
  }

  private static ContractEquipmentSource resolveContractSource(Equipment eq) {
    return eq.getSource() == EquipmentSource.ADDED_BY_TENANT
        ? ContractEquipmentSource.ADDED
        : ContractEquipmentSource.EXISTING;
  }

  private static EquipmentPlacementScope resolvePlacementScope(Equipment eq) {
    return eq.getRoom() != null ? EquipmentPlacementScope.ROOM : EquipmentPlacementScope.SHARED;
  }

  private static String resolveEquipmentName(Equipment eq) {
    if (eq.getCatalog() != null && eq.getCatalog().getName() != null) {
      return eq.getCatalog().getName();
    }
    if (eq.getEquipmentName() != null && !eq.getEquipmentName().isBlank()) {
      return eq.getEquipmentName();
    }
    return "Thiết bị";
  }

  static String formatCondition(EquipmentStatus status) {
    if (status == null) {
      return "Tốt";
    }
    return switch (status) {
      case NEW -> "Mới";
      case GOOD -> "Tốt";
      case DAMAGED -> "Hư hỏng nhẹ";
      case MAINTENANCE -> "Bảo trì";
      case BROKEN -> "Hỏng";
      case DISPOSED -> "Thanh lý";
    };
  }

  private static String formatMoney(BigDecimal amount) {
    return String.format("%,d", amount.longValue()).replace(',', '.');
  }
}
