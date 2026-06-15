package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.enums.ContractStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantContractRepository extends JpaRepository<TenantContract, Long> {

    // Quy tắc 1-HĐ-active: kiểm tra phòng đã có hợp đồng đang hiệu lực chưa
    boolean existsByRoomIdAndStatus(Long roomId, ContractStatus status);

    // Quy tắc 1-HĐ-active cho thuê nguyên căn (room == null)
    boolean existsByPropertyIdAndRoomIsNullAndStatus(Long propertyId, ContractStatus status);

    List<TenantContract> findByPropertyId(Long propertyId);

    List<TenantContract> findByTenantId(UUID tenantUserId);

    Optional<TenantContract> findByPayosOrderCode(Long payosOrderCode);
}
