package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.ExpenseRequest;
import com.sep490.slms2026.dto.response.ExpenseResponse;
import com.sep490.slms2026.enums.ExpenseCategory;
import com.sep490.slms2026.service.HostExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/host/expenses")
@RequiredArgsConstructor
public class HostExpenseController {

    private final HostExpenseService service;

    @GetMapping
    public Page<ExpenseResponse> getExpenses(
            @RequestParam(required = false) Long propertyId,
            @RequestParam(required = false) ExpenseCategory category,
            @RequestParam(required = false) String month,
            Pageable pageable) {
        return service.getExpenses(propertyId, category, month, pageable);
    }

    @PostMapping
    public ExpenseResponse createExpense(@RequestBody ExpenseRequest request) {
        return service.createExpense(request);
    }

    @PutMapping("/{id}")
    public ExpenseResponse updateExpense(@PathVariable Long id, @RequestBody ExpenseRequest request) {
        return service.updateExpense(id, request);
    }

    @DeleteMapping("/{id}")
    public void deleteExpense(@PathVariable Long id) {
        service.deleteExpense(id);
    }
}
