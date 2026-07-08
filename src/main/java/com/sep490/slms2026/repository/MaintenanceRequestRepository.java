package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.MaintenanceRequest;
import com.sep490.slms2026.enums.MaintenanceCategory;
import com.sep490.slms2026.enums.MaintenancePriority;
import com.sep490.slms2026.enums.MaintenanceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface MaintenanceRequestRepository extends JpaRepository<MaintenanceRequest, Long>, JpaSpecificationExecutor<MaintenanceRequest> {

    // --- Tenant: chỉ request của chính mình ---
    @Query("SELECT r FROM MaintenanceRequest r WHERE r.tenant.id = :tenantId " +
           "AND (:status IS NULL OR r.status = :status)")
    Page<MaintenanceRequest> findByTenantIdAndOptionalStatus(
            @Param("tenantId") UUID tenantId,
            @Param("status") MaintenanceStatus status,
            Pageable pageable);

    // --- Manager/Admin: filter nhiều field ---
    @Query("SELECT r FROM MaintenanceRequest r WHERE " +
           "(:status IS NULL OR r.status = :status) " +
           "AND (:priority IS NULL OR r.priority = :priority) " +
           "AND (:propertyId IS NULL OR r.property.id = :propertyId) " +
           "AND (:roomId IS NULL OR r.room.id = :roomId) " +
           "AND (:category IS NULL OR r.category = :category)")
    Page<MaintenanceRequest> findAllWithFilters(
            @Param("status") MaintenanceStatus status,
            @Param("priority") MaintenancePriority priority,
            @Param("propertyId") Long propertyId,
            @Param("roomId") Long roomId,
            @Param("category") MaintenanceCategory category,
            Pageable pageable);

    // --- Manager: chỉ request thuộc property mình phụ trách ---
    @Query("SELECT r FROM MaintenanceRequest r WHERE r.property.id IN :propertyIds " +
           "AND (:status IS NULL OR r.status = :status) " +
           "AND (:priority IS NULL OR r.priority = :priority) " +
           "AND (:propertyId IS NULL OR r.property.id = :propertyId) " +
           "AND (:roomId IS NULL OR r.room.id = :roomId) " +
           "AND (:category IS NULL OR r.category = :category)")
    Page<MaintenanceRequest> findByPropertyIdInWithFilters(
            @Param("propertyIds") List<Long> propertyIds,
            @Param("status") MaintenanceStatus status,
            @Param("priority") MaintenancePriority priority,
            @Param("propertyId") Long propertyId,
            @Param("roomId") Long roomId,
            @Param("category") MaintenanceCategory category,
            Pageable pageable);

    // --- Dashboard aggregation ---
    long countByStatus(MaintenanceStatus status);

    @Query("SELECT COALESCE(SUM(r.repairCost), 0) FROM MaintenanceRequest r " +
           "WHERE r.status = 'RESOLVED' " +
           "AND (:propertyId IS NULL OR r.property.id = :propertyId) " +
           "AND (:from IS NULL OR r.createdAt >= :from) " +
           "AND (:to IS NULL OR r.createdAt <= :to)")
    long sumRepairCostWithFilters(
            @Param("propertyId") Long propertyId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(r) FROM MaintenanceRequest r WHERE r.status = :status " +
           "AND (:propertyId IS NULL OR r.property.id = :propertyId) " +
           "AND (:from IS NULL OR r.createdAt >= :from) " +
           "AND (:to IS NULL OR r.createdAt <= :to)")
    long countByStatusWithFilters(
            @Param("status") MaintenanceStatus status,
            @Param("propertyId") Long propertyId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(r) FROM MaintenanceRequest r " +
           "WHERE (:propertyId IS NULL OR r.property.id = :propertyId) " +
           "AND (:from IS NULL OR r.createdAt >= :from) " +
           "AND (:to IS NULL OR r.createdAt <= :to)")
    long countWithFilters(
            @Param("propertyId") Long propertyId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

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
