package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.MaintenanceRequest;
import com.sep490.slms2026.enums.MaintenanceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface MaintenanceRepository extends JpaRepository<MaintenanceRequest, UUID> {

    // Manager xem tất cả request trong vùng mình quản lý
    @Query("SELECT mr FROM MaintenanceRequest mr " +
            "JOIN mr.equipment e JOIN e.room r JOIN r.property p JOIN p.zone z " +
            "WHERE EXISTS (SELECT 1 FROM OperationManagement om JOIN om.zones mz " +
            "              WHERE om.id = :managerId AND " +
            "              (mz.id = z.id OR mz.id = z.parent.id OR mz.id = z.parent.parent.id))")
    Page<MaintenanceRequest> findAllByManagerZones(@Param("managerId") UUID managerId, Pageable pageable);

    // Tenant xem request của chính mình
    Page<MaintenanceRequest> findAllByTenantId(UUID tenantId, Pageable pageable);

    // Lọc theo trạng thái
    Page<MaintenanceRequest> findAllByTenantIdAndStatus(UUID tenantId, MaintenanceStatus status, Pageable pageable);
}