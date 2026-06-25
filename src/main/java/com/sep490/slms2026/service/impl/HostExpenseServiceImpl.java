package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.ExpenseRequest;
import com.sep490.slms2026.dto.response.ExpenseResponse;
import com.sep490.slms2026.entity.Expense;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.enums.ExpenseCategory;
import com.sep490.slms2026.repository.ExpenseRepository;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.service.HostExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HostExpenseServiceImpl implements HostExpenseService {

    private final ExpenseRepository expenseRepository;
    private final PropertyRepository propertyRepository;

    @Override
    public Page<ExpenseResponse> getExpenses(Long propertyId, ExpenseCategory category, String month, Pageable pageable) {
        if (propertyId != null && category != null && month != null) {
            return expenseRepository.findByPropertyIdAndCategoryAndMonthAndDeletedFalse(propertyId, category, month, pageable).map(this::convertToResponse);
        } else if (propertyId != null && month != null) {
            return expenseRepository.findByPropertyIdAndMonthAndDeletedFalse(propertyId, month, pageable).map(this::convertToResponse);
        } else if (propertyId != null) {
            return expenseRepository.findByPropertyIdAndDeletedFalse(propertyId, pageable).map(this::convertToResponse);
        }
        return Page.empty();
    }

    @Override
    public ExpenseResponse createExpense(ExpenseRequest request) {
        Property property = propertyRepository.findById(request.getPropertyId()).orElseThrow();
        Expense expense = Expense.builder()
                .property(property)
                .category(request.getCategory())
                .amount(request.getAmount())
                .month(request.getMonth())
                .note(request.getNote())
                .build();
        return convertToResponse(expenseRepository.save(expense));
    }

    @Override
    public ExpenseResponse updateExpense(Long id, ExpenseRequest request) {
        Expense expense = expenseRepository.findById(id).orElseThrow();
        expense.setCategory(request.getCategory());
        expense.setAmount(request.getAmount());
        expense.setMonth(request.getMonth());
        expense.setNote(request.getNote());
        return convertToResponse(expenseRepository.save(expense));
    }

    @Override
    public void deleteExpense(Long id) {
        Expense expense = expenseRepository.findById(id).orElseThrow();
        expense.setDeleted(true);
        expenseRepository.save(expense);
    }

    private ExpenseResponse convertToResponse(Expense expense) {
        ExpenseResponse res = new ExpenseResponse();
        res.setId(expense.getId());
        res.setPropertyId(expense.getProperty().getId());
        res.setPropertyName(expense.getProperty().getPropertyName());
        res.setCategory(expense.getCategory());
        res.setAmount(expense.getAmount());
        res.setMonth(expense.getMonth());
        res.setNote(expense.getNote());
        res.setCreatedAt(expense.getCreatedAt());
        return res;
    }
}
