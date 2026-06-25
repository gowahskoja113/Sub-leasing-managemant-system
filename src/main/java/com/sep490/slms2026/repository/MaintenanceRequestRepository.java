package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.MaintenanceRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceRequestRepository extends JpaRepository<MaintenanceRequest, Long> {
    Page<MaintenanceRequest> findByTenantIdAndDeletedFalse(Long tenantId, Pageable pageable);
    Page<MaintenanceRequest> findByDeletedFalse(Pageable pageable);
}
