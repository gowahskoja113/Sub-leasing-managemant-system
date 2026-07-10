package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.TenantContractEquipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TenantContractEquipmentRepository extends JpaRepository<TenantContractEquipment, Long> {

    List<TenantContractEquipment> findByTenantContractIdOrderByIdAsc(Long tenantContractId);
}
