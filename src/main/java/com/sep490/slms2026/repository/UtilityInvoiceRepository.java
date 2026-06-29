package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.UtilityInvoice;
import com.sep490.slms2026.enums.UtilityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UtilityInvoiceRepository extends JpaRepository<UtilityInvoice, Long> {

    @Query("""
            SELECT i FROM UtilityInvoice i
            LEFT JOIN FETCH i.room
            LEFT JOIN FETCH i.property
            LEFT JOIN FETCH i.tenantContract tc
            LEFT JOIN FETCH tc.tenant t
            LEFT JOIN FETCH t.user
            WHERE i.property.id = :propertyId
              AND (:period IS NULL OR LOWER(i.billingPeriod) LIKE LOWER(CONCAT('%', :period, '%')))
              AND (:utilityType IS NULL OR i.utilityType = :utilityType)
            ORDER BY i.createdAt DESC
            """)
    List<UtilityInvoice> findByFilters(
            @Param("propertyId") Long propertyId,
            @Param("period") String period,
            @Param("utilityType") UtilityType utilityType);

    Optional<UtilityInvoice> findTopByPropertyIdAndRoomIdAndUtilityTypeOrderByCreatedAtDesc(
            Long propertyId, Long roomId, UtilityType utilityType);

    Optional<UtilityInvoice> findTopByPropertyIdAndRoomIsNullAndUtilityTypeOrderByCreatedAtDesc(
            Long propertyId, UtilityType utilityType);
}
