package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.EquipmentMaintenanceHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipmentMaintenanceHistoryRepository extends JpaRepository<EquipmentMaintenanceHistory, Long> {

    List<EquipmentMaintenanceHistory> findByEquipmentIdOrderByMaintenanceDateDesc(Long equipmentId);
}
