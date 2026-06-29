package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.CheckoutRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CheckoutRequestRepository extends JpaRepository<CheckoutRequest, Long> {

    List<CheckoutRequest> findByTenantUserIdOrderByCreatedAtDesc(UUID tenantUserId);

    Optional<CheckoutRequest> findByIdAndTenantUserId(Long id, UUID tenantUserId);
}
