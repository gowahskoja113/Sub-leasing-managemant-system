package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.response.UtilityInvoiceHistoryResponse;
import com.sep490.slms2026.service.UtilityInvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/manager/utility-invoices")
@RequiredArgsConstructor
public class ManagerUtilityInvoiceController {

    private final UtilityInvoiceService utilityInvoiceService;

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<UtilityInvoiceHistoryResponse> listInvoices(
            @RequestParam Long propertyId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String type) {
        return ResponseEntity.ok(utilityInvoiceService.listInvoices(propertyId, period, type));
    }
}
