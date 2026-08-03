package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.CheckoutSettlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CheckoutSettlementRepository extends JpaRepository<CheckoutSettlement, Long> {
    Optional<CheckoutSettlement> findByCheckoutRequestId(Long checkoutRequestId);
}
