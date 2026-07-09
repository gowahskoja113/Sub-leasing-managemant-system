package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.TenantPendingCharge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface TenantPendingChargeRepository extends JpaRepository<TenantPendingCharge, Long> {
    List<TenantPendingCharge> findByTenantContractIdAndStatus(Long contractId, String status);

    @Query("SELECT tpc FROM TenantPendingCharge tpc " +
           "JOIN tpc.tenantContract tc " +
           "JOIN tc.tenant t " +
           "WHERE t.user.id = :tenantId " +
           "ORDER BY tpc.createdAt DESC")
    List<TenantPendingCharge> findByTenantUserId(@Param("tenantId") UUID tenantId);

    @Query("SELECT tpc FROM TenantPendingCharge tpc " +
           "JOIN tpc.tenantContract tc " +
           "JOIN tc.property p " +
           "WHERE (:propertyId IS NULL OR p.id = :propertyId) " +
           "  AND (:status IS NULL OR tpc.status = :status) " +
           "  AND (:isAdmin = true OR p.managedBy = :managerId) " +
           "ORDER BY tpc.createdAt DESC")
    List<TenantPendingCharge> findForManager(
            @Param("managerId") UUID managerId,
            @Param("isAdmin") boolean isAdmin,
            @Param("propertyId") Long propertyId,
            @Param("status") String status);
}
