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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface MaintenanceRequestRepository extends JpaRepository<MaintenanceRequest, Long>, JpaSpecificationExecutor<MaintenanceRequest> {

    @Query("SELECT r FROM MaintenanceRequest r WHERE r.tenant.id = :tenantId " +
           "AND (:status IS NULL OR r.status = :status)")
    Page<MaintenanceRequest> findByTenantIdAndOptionalStatus(
            @Param("tenantId") UUID tenantId,
            @Param("status") MaintenanceStatus status,
            Pageable pageable);

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

    long countByStatus(MaintenanceStatus status);

    Page<MaintenanceRequest> findByTenantIdAndDeletedFalse(UUID tenantId, Pageable pageable);

    Page<MaintenanceRequest> findByDeletedFalse(Pageable pageable);

    List<MaintenanceRequest> findByEquipmentIdAndDeletedFalseOrderByCreatedAtDesc(Long equipmentId);

    @Query("SELECT COUNT(m) FROM MaintenanceRequest m WHERE m.deleted = false")
    long countAll();

    @Query("SELECT COUNT(m) FROM MaintenanceRequest m WHERE m.status = 'OPEN' AND m.deleted = false")
    long countOpen();

    @Query("SELECT COUNT(m) FROM MaintenanceRequest m WHERE m.status IN ('IN_REPAIR', 'TENANT_FAULT', 'PENDING_TENANT_REPAIR') AND m.deleted = false")
    long countInProgress();

    @Query("SELECT COUNT(m) FROM MaintenanceRequest m WHERE m.status = 'CLOSED' AND m.deleted = false")
    long countResolved();

    @Query("SELECT COUNT(m) FROM MaintenanceRequest m WHERE m.status = 'CANCELLED' AND m.deleted = false")
    long countCancelled();

    @Query("SELECT COALESCE(SUM(m.invoiceAmount), 0) FROM MaintenanceRequest m WHERE m.status = 'CLOSED' AND m.deleted = false")
    java.math.BigDecimal sumInvoiceAmount();

    @Query("SELECT COUNT(m) FROM MaintenanceRequest m WHERE m.deleted = false AND m.property.operationManagerId = :managerId")
    long countAllByManager(@Param("managerId") UUID managerId);

    @Query("SELECT COUNT(m) FROM MaintenanceRequest m WHERE m.status = 'OPEN' AND m.deleted = false AND m.property.operationManagerId = :managerId")
    long countOpenByManager(@Param("managerId") UUID managerId);

    @Query("SELECT COUNT(m) FROM MaintenanceRequest m WHERE m.status IN ('IN_REPAIR', 'TENANT_FAULT', 'PENDING_TENANT_REPAIR') AND m.deleted = false AND m.property.operationManagerId = :managerId")
    long countInProgressByManager(@Param("managerId") UUID managerId);

    @Query("SELECT COUNT(m) FROM MaintenanceRequest m WHERE m.status = 'CLOSED' AND m.deleted = false AND m.property.operationManagerId = :managerId")
    long countResolvedByManager(@Param("managerId") UUID managerId);

    @Query("SELECT COUNT(m) FROM MaintenanceRequest m WHERE m.status = 'CANCELLED' AND m.deleted = false AND m.property.operationManagerId = :managerId")
    long countCancelledByManager(@Param("managerId") UUID managerId);

    @Query("SELECT COALESCE(SUM(m.invoiceAmount), 0) FROM MaintenanceRequest m WHERE m.status = 'CLOSED' AND m.deleted = false AND m.property.operationManagerId = :managerId")
    java.math.BigDecimal sumInvoiceAmountByManager(@Param("managerId") UUID managerId);

    List<MaintenanceRequest> findByStatusAndSelfRepairDeadlineBeforeAndDeletedFalse(
            MaintenanceStatus status, LocalDate deadline);

    boolean existsByRoomIdAndStatusNotInAndIdNotAndDeletedFalse(
            Long roomId, List<MaintenanceStatus> excludedStatuses, Long excludedId);
}
