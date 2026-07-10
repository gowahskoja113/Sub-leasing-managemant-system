package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.ContractAddedEquipmentRequest;
import com.sep490.slms2026.dto.response.TenantContractDetailResponse.EquipmentItem;
import com.sep490.slms2026.entity.Equipment;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.entity.TenantContractEquipment;
import java.util.List;

public interface ContractEquipmentService {

  /** Inventory có sẵn (INITIAL_HANDOVER / PURCHASED) — dùng cho checkbox. */
  List<Equipment> findExistingInventory(Long propertyId, Long roomId);

  /** Toàn bộ ACTIVE trong phạm vi (gồm ADDED_BY_TENANT đã tạo). */
  List<Equipment> findInScope(Long propertyId, Long roomId);

  /**
   * Áp dụng bàn giao đầy đủ: thiết bị có sẵn + lắp thêm. Mỗi nhóm null = không đổi nhóm đó.
   *
   * <p>{@code selectedEquipmentIds} chỉ ID inventory có sẵn. {@code addedEquipments} tạo mới inline.
   * {@code addedEquipmentIds} tham chiếu thiết bị đã POST /equipments trước đó.
   */
  void resolveAndApplyHandover(
      TenantContract contract,
      List<Long> selectedEquipmentIds,
      List<Long> declinedEquipmentIds,
      List<ContractAddedEquipmentRequest> addedEquipments,
      List<Long> addedEquipmentIds);

  /** @deprecated Gọi {@link #resolveAndApplyHandover} với added = null. */
  default void resolveAndApplySelection(
      TenantContract contract, List<Long> selectedEquipmentIds, List<Long> declinedEquipmentIds) {
    resolveAndApplyHandover(contract, selectedEquipmentIds, declinedEquipmentIds, null, null);
  }

  void disableDeclinedForActiveContract(TenantContract contract);

  List<EquipmentItem> mapSelectedToItems(TenantContract contract);

  List<EquipmentItem> mapAvailableToItems(Long propertyId, Long roomId);

  List<Long> getSelectedIds(TenantContract contract);

  List<Long> getSelectedExistingIds(TenantContract contract);

  List<Long> getSelectedAddedIds(TenantContract contract);

  String buildEquipmentSnapshot(List<TenantContractEquipment> selected);
}
