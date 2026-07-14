package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.ContractAddedEquipmentRequest;
import com.sep490.slms2026.dto.response.TenantContractDetailResponse.EquipmentItem;
import com.sep490.slms2026.entity.Equipment;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.entity.TenantContractEquipment;
import java.util.List;

public interface ContractEquipmentService {

  /** Inventory có sẵn (INITIAL_HANDOVER / PURCHASED) trong nhà/phòng — ghi vào HĐ. */
  List<Equipment> findExistingInventory(Long propertyId, Long roomId);

  /** Toàn bộ ACTIVE trong phạm vi (gồm ADDED_BY_TENANT đã tạo). */
  List<Equipment> findInScope(Long propertyId, Long roomId);

  /**
   * Áp dụng bàn giao: thiết bị có sẵn + lắp thêm.
   *
   * <p><b>Có sẵn (EXISTING):</b> {@code selectedEquipmentIds == null} và
   * {@code declinedEquipmentIds == null} → tự lấy <b>toàn bộ</b> inventory ACTIVE
   * trong phạm vi property/room (không cần checkbox FE). Gửi list tường minh vẫn được
   * hỗ trợ (legacy).
   *
   * <p>{@code addedEquipments} / {@code addedEquipmentIds}: null = giữ phần lắp thêm hiện có.
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

  /** Khôi phục thiết bị DISABLE do HĐ này (khi terminate / hết hạn / hủy). */
  void restoreDisabledByContract(Long contractId);

  List<EquipmentItem> mapSelectedToItems(TenantContract contract);

  List<EquipmentItem> mapAvailableToItems(Long propertyId, Long roomId);

  List<Long> getSelectedIds(TenantContract contract);

  List<Long> getSelectedExistingIds(TenantContract contract);

  List<Long> getSelectedAddedIds(TenantContract contract);

  String buildEquipmentSnapshot(List<TenantContractEquipment> selected);
}
