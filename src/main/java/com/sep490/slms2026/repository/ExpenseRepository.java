package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.Expense;
import com.sep490.slms2026.enums.ExpenseCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    Page<Expense> findByPropertyIdAndCategoryAndMonthAndDeletedFalse(Long propertyId, ExpenseCategory category, String month, Pageable pageable);
    Page<Expense> findByPropertyIdAndCategoryAndDeletedFalse(Long propertyId, ExpenseCategory category, Pageable pageable);
    Page<Expense> findByPropertyIdAndMonthAndDeletedFalse(Long propertyId, String month, Pageable pageable);
    Page<Expense> findByPropertyIdAndDeletedFalse(Long propertyId, Pageable pageable);
    List<Expense> findByMonthAndDeletedFalse(String month);
    List<Expense> findByPropertyIdAndMonthAndCategoryNotAndDeletedFalse(Long propertyId, String month, ExpenseCategory category);
}
