package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.ExtensionRequest;
import com.sep490.slms2026.enums.ExtensionRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExtensionRequestRepository extends JpaRepository<ExtensionRequest, Long> {
    List<ExtensionRequest> findByTenantUserIdOrderByCreatedAtDesc(UUID tenantUserId);
    Optional<ExtensionRequest> findByIdAndTenantUserId(Long id, UUID tenantUserId);
    boolean existsByTenantContractIdAndStatus(Long contractId, ExtensionRequestStatus status);
    List<ExtensionRequest> findAllByOrderByCreatedAtDesc();
    List<ExtensionRequest> findByStatusOrderByCreatedAtDesc(ExtensionRequestStatus status);
    List<ExtensionRequest> findByTenantContractIdAndStatus(Long contractId, ExtensionRequestStatus status);
    List<ExtensionRequest> findByTenantContractIdOrderByCreatedAtDesc(Long contractId);
}
