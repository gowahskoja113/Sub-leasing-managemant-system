package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.InboundContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InboundContractRepository extends JpaRepository<InboundContract, Long> {

    boolean existsByPropertyId(Long propertyId);

    java.util.Optional<InboundContract> findByPropertyId(Long propertyId);

    boolean existsByContractCode(String contractCode);

    java.util.Optional<InboundContract> findByContractCode(String contractCode);

    @Query("""
       SELECT COALESCE(SUM(i.totalRentAmount),0)
       FROM InboundContract i
       """)
    java.math.BigDecimal getTotalInboundCost();
}
