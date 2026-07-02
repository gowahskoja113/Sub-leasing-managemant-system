package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.MaintenanceTimeline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaintenanceTimelineRepository extends JpaRepository<MaintenanceTimeline, Long> {
    List<MaintenanceTimeline> findByMaintenanceRequestIdOrderByChangedAtAsc(Long maintenanceRequestId);
}
