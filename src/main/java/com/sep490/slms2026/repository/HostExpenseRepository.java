package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.HostExpense;
import com.sep490.slms2026.enums.HostExpenseCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface HostExpenseRepository extends JpaRepository<HostExpense, Long> {

    @Query("""
            SELECT e FROM HostExpense e JOIN FETCH e.property p
            WHERE (:propertyId IS NULL OR p.id = :propertyId)
              AND (:category IS NULL OR e.category = :category)
              AND (:month IS NULL OR e.month = :month)
            ORDER BY e.createdAt DESC
            """)
    Page<HostExpense> search(
            @Param("propertyId") Long propertyId,
            @Param("category") HostExpenseCategory category,
            @Param("month") String month,
            Pageable pageable);

    @Query("""
            SELECT COALESCE(SUM(e.amount), 0) FROM HostExpense e
            WHERE e.month = :month
            """)
    BigDecimal sumAmountByMonth(@Param("month") String month);

    @Query("""
            SELECT e.category, COALESCE(SUM(e.amount), 0) FROM HostExpense e
            WHERE e.month = :month
            GROUP BY e.category
            """)
    List<Object[]> sumAmountByCategoryForMonth(@Param("month") String month);

    @Query("""
            SELECT COALESCE(SUM(e.amount), 0) FROM HostExpense e
            WHERE e.property.id = :propertyId AND e.month = :month
            """)
    BigDecimal sumAmountByPropertyAndMonth(@Param("propertyId") Long propertyId, @Param("month") String month);
}
