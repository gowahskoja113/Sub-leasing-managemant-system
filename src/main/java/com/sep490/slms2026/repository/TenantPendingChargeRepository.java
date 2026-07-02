package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.TenantPendingCharge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TenantPendingChargeRepository extends JpaRepository<TenantPendingCharge, Long> {
    List<TenantPendingCharge> findByTenantContractIdAndStatus(Long contractId, String status);
}
