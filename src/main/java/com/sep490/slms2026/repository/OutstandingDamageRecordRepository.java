package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.OutstandingDamageRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutstandingDamageRecordRepository extends JpaRepository<OutstandingDamageRecord, Long> {

    List<OutstandingDamageRecord> findByTenantContractIdAndResolvedAtCheckoutFalseOrderByCreatedAtDesc(
            Long tenantContractId);

    List<OutstandingDamageRecord> findByResolvedAtCheckoutFalseOrderByCreatedAtDesc();

    List<OutstandingDamageRecord> findByMaintenanceRequestIdAndResolvedAtCheckoutFalse(Long maintenanceRequestId);

    boolean existsByMaintenanceRequestId(Long maintenanceRequestId);
}
