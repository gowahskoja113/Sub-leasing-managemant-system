package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.ExpenseRequest;
import com.sep490.slms2026.dto.response.ExpenseResponse;
import com.sep490.slms2026.enums.ExpenseCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface HostExpenseService {
    Page<ExpenseResponse> getExpenses(Long propertyId, ExpenseCategory category, String month, Pageable pageable);
    ExpenseResponse createExpense(ExpenseRequest request);
    ExpenseResponse updateExpense(Long id, ExpenseRequest request);
    void deleteExpense(Long id);
}
