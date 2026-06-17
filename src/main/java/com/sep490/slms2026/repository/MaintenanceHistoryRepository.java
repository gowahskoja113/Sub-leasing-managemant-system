package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.MaintenanceHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceHistoryRepository
        extends JpaRepository<MaintenanceHistory, Long> {
}
