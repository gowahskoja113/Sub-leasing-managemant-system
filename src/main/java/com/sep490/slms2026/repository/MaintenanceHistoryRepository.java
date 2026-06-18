package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.MaintenanceHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaintenanceHistoryRepository extends JpaRepository<MaintenanceHistory, Long> {

    List<MaintenanceHistory> findByMaintenanceRequestIdOrderByChangedAtAsc(Long maintenanceRequestId);
}
