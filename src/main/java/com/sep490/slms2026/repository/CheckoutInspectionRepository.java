package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.CheckoutInspection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CheckoutInspectionRepository extends JpaRepository<CheckoutInspection, Long> {
    Optional<CheckoutInspection> findByCheckoutRequestId(Long checkoutRequestId);
}
