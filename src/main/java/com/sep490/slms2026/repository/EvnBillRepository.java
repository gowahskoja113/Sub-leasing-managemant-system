package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.EvnBill;
import com.sep490.slms2026.enums.EvnBillStatus;
import com.sep490.slms2026.enums.UtilityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EvnBillRepository extends JpaRepository<EvnBill, Long> {

    @Query("SELECT e FROM EvnBill e WHERE (:propertyId IS NULL OR e.property.id = :propertyId) " +
           "AND (:month IS NULL OR e.month = :month) " +
           "AND (:year IS NULL OR e.year = :year) " +
           "AND (:type IS NULL OR e.type = :type) " +
           "AND (:status IS NULL OR e.status = :status) " +
           "ORDER BY e.createdAt DESC")
    List<EvnBill> findByFilters(@Param("propertyId") Long propertyId,
                                @Param("month") Integer month,
                                @Param("year") Integer year,
                                @Param("type") UtilityType type,
                                @Param("status") EvnBillStatus status);

    Optional<EvnBill> findByPropertyIdAndMonthAndYearAndTypeAndStatus(
            Long propertyId, Integer month, Integer year, UtilityType type, EvnBillStatus status);
}
