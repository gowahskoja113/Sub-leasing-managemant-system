package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.CheckoutRequest;
import com.sep490.slms2026.enums.CheckoutRequestStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CheckoutRequestRepository extends JpaRepository<CheckoutRequest, Long> {

    List<CheckoutRequest> findByTenantUserIdOrderByCreatedAtDesc(UUID tenantUserId);

    List<CheckoutRequest> findByTenantContractIdOrderByCreatedAtDesc(Long tenantContractId);

    Optional<CheckoutRequest> findByIdAndTenantUserId(Long id, UUID tenantUserId);

    List<CheckoutRequest> findByStatusOrderByCreatedAtDesc(CheckoutRequestStatus status);

    List<CheckoutRequest> findAllByOrderByCreatedAtDesc();

    boolean existsByTenantContractIdAndStatusIn(Long tenantContractId, Collection<CheckoutRequestStatus> statuses);

    @Query("""
            SELECT cr FROM CheckoutRequest cr
            JOIN FETCH cr.tenantContract
            WHERE cr.tenantContract.id IN :contractIds
            ORDER BY cr.createdAt DESC
            """)
    List<CheckoutRequest> findByTenantContractIdIn(@Param("contractIds") Collection<Long> contractIds);
}
