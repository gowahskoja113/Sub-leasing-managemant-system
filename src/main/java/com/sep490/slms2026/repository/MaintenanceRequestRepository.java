package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.MaintenanceRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MaintenanceRequestRepository extends JpaRepository<MaintenanceRequest, UUID> {
    // Dành cho Tenant Web/App
    List<MaintenanceRequest> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    // Dành cho Operations Manager
    List<MaintenanceRequest> findByAssignedManagerIdOrderByCreatedAtDesc(UUID managerId);

    // Dành cho Admin
    List<MaintenanceRequest> findByPropertyId(UUID propertyId);
}
