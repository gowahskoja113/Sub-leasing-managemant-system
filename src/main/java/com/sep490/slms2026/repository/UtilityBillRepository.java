package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.UtilityBill;
import com.sep490.slms2026.enums.UtilityBillStatus;
import com.sep490.slms2026.enums.UtilityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UtilityBillRepository extends JpaRepository<UtilityBill, Long> {

    @Query("SELECT e FROM UtilityBill e WHERE (:propertyId IS NULL OR e.property.id = :propertyId) " +
           "AND (:month IS NULL OR e.month = :month) " +
           "AND (:year IS NULL OR e.year = :year) " +
           "AND (:type IS NULL OR e.type = :type) " +
           "AND (:status IS NULL OR e.status = :status) " +
           "ORDER BY e.createdAt DESC")
    List<UtilityBill> findByFilters(@Param("propertyId") Long propertyId,
                                @Param("month") Integer month,
                                @Param("year") Integer year,
                                @Param("type") UtilityType type,
                                @Param("status") UtilityBillStatus status);

    Optional<UtilityBill> findByPropertyIdAndMonthAndYearAndTypeAndStatus(
            Long propertyId, Integer month, Integer year, UtilityType type, UtilityBillStatus status);
}

