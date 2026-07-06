package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.MaintenanceRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MaintenanceRequestRepository extends JpaRepository<MaintenanceRequest, Long>, JpaSpecificationExecutor<MaintenanceRequest> {
    Page<MaintenanceRequest> findByTenantIdAndDeletedFalse(java.util.UUID tenantId, Pageable pageable);
    Page<MaintenanceRequest> findByDeletedFalse(Pageable pageable);
    
    java.util.List<MaintenanceRequest> findByEquipmentIdAndDeletedFalseOrderByCreatedAtDesc(Long equipmentId);
    
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(m) FROM MaintenanceRequest m WHERE m.deleted = false")
    long countAll();
    
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(m) FROM MaintenanceRequest m WHERE m.status = 'PENDING' AND m.deleted = false")
    long countPending();
    
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(m) FROM MaintenanceRequest m WHERE m.status = 'IN_PROGRESS' AND m.deleted = false")
    long countInProgress();
    
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(m) FROM MaintenanceRequest m WHERE m.status IN ('DONE', 'CONFIRMED') AND m.deleted = false")
    long countResolved();
    
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(m) FROM MaintenanceRequest m WHERE m.status = 'CANCELLED' AND m.deleted = false")
    long countCancelled();
    
    @org.springframework.data.jpa.repository.Query("SELECT SUM(m.repairCost) FROM MaintenanceRequest m WHERE m.status IN ('DONE', 'CONFIRMED') AND m.deleted = false")
    java.math.BigDecimal sumRepairCost();
}
