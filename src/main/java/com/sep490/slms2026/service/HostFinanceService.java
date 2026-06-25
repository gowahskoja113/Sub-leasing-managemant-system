package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.response.DepositLedgerResponse;
import com.sep490.slms2026.dto.response.InvoiceResponse;
import com.sep490.slms2026.dto.response.PropertyPnlResponse;
import com.sep490.slms2026.dto.response.ReceivablesAgingResponse;
import com.sep490.slms2026.enums.DepositStatus;
import com.sep490.slms2026.enums.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface HostFinanceService {
    Object getCashflow(String from, String to);
    Object getExpenseBreakdown(String month);
    PropertyPnlResponse getPropertyPnl(String month);
    Page<InvoiceResponse> getInvoices(String month, InvoiceStatus status, Pageable pageable);
    ReceivablesAgingResponse getReceivablesAging();
    DepositLedgerResponse getDepositLedger(DepositStatus status);
}
