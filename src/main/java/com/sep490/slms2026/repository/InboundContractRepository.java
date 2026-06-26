package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.InboundContract;
import com.sep490.slms2026.enums.ContractStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InboundContractRepository extends JpaRepository<InboundContract, Long> {

    boolean existsByPropertyId(Long propertyId);

    Optional<InboundContract> findFirstByPropertyIdOrderByIdDesc(Long propertyId);

    boolean existsByContractCode(String contractCode);

    java.util.Optional<InboundContract> findByContractCode(String contractCode);

    @Query("""
            SELECT c FROM InboundContract c
            JOIN FETCH c.property
            WHERE LOWER(TRIM(c.contractCode)) = LOWER(TRIM(:contractCode))
            """)
    java.util.Optional<InboundContract> findByContractCodeIgnoreCaseWithProperty(
            @Param("contractCode") String contractCode);

    @Query("""
       SELECT COALESCE(SUM(i.totalRentAmount),0)
       FROM InboundContract i
       """)
    java.math.BigDecimal getTotalInboundCost();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM InboundContract c WHERE c.property.id = :propertyId")
    void deleteByPropertyId(@Param("propertyId") Long propertyId);

    java.util.List<InboundContract> findByStatus(ContractStatus status);

    @Query("""
            SELECT c FROM InboundContract c JOIN FETCH c.property p
            WHERE (:status IS NULL OR c.status = :status)
              AND (:propertyId IS NULL OR p.id = :propertyId)
            ORDER BY c.endDate DESC
            """)
    java.util.List<InboundContract> searchMasterLeases(
            @Param("status") ContractStatus status,
            @Param("propertyId") Long propertyId);
}
