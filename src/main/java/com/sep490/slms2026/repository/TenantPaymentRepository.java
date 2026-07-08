package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.TenantPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TenantPaymentRepository extends JpaRepository<TenantPayment, Long> {

    List<TenantPayment> findByTenantUserIdOrderByPaidAtDesc(UUID tenantUserId);
}
