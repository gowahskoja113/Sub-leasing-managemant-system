package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.response.*;
import com.sep490.slms2026.enums.DepositStatus;
import com.sep490.slms2026.enums.InvoiceStatus;
import com.sep490.slms2026.service.HostFinanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

// @RestController
// @RequestMapping("/api/v1/host")
@RequiredArgsConstructor
public class HostFinanceController {

    private final HostFinanceService service;

    @GetMapping("/finance/cashflow")
    public Object getCashflow(
            @RequestParam String from,
            @RequestParam String to) {
        return service.getCashflow(from, to);
    }

    @GetMapping("/finance/expense-breakdown")
    public Object getExpenseBreakdown(@RequestParam String month) {
        return service.getExpenseBreakdown(month);
    }

    @GetMapping("/finance/property-pnl")
    public PropertyPnlResponse getPropertyPnl(@RequestParam String month) {
        return service.getPropertyPnl(month);
    }

    @GetMapping("/invoices")
    public Page<InvoiceResponse> getInvoices(
            @RequestParam String month,
            @RequestParam(required = false) InvoiceStatus status,
            Pageable pageable) {
        return service.getInvoices(month, status, pageable);
    }

    @GetMapping("/finance/receivables-aging")
    public ReceivablesAgingResponse getReceivablesAging() {
        return service.getReceivablesAging();
    }

    @GetMapping("/finance/deposits")
    public DepositLedgerResponse getDepositLedger(
            @RequestParam(required = false) DepositStatus status) {
        return service.getDepositLedger(status);
    }
}
