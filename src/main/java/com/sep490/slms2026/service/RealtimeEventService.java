package com.sep490.slms2026.service;

import com.sep490.slms2026.entity.TenantInvoice;

import com.sep490.slms2026.dto.billing.InvoicePaymentContext;

public interface RealtimeEventService {
    void publishInvoicePaid(TenantInvoice invoice);

    void publishInvoicePaid(TenantInvoice invoice, InvoicePaymentContext context);
}
