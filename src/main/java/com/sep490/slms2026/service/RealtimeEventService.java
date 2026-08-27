package com.sep490.slms2026.service;

import com.sep490.slms2026.entity.TenantInvoice;

import com.sep490.slms2026.dto.billing.InvoicePaymentContext;

public interface RealtimeEventService {
    void publishInvoicePaid(TenantInvoice invoice);

    void publishInvoicePaid(TenantInvoice invoice, InvoicePaymentContext context);

    /** Tiến độ dual-OTP xác nhận HĐ (tenant/manager đã verify chưa). */
    void publishContractConfirmProgress(com.sep490.slms2026.entity.TenantContract contract);

    /** HĐ vừa ACTIVE sau khi đủ 2 OTP. */
    void publishContractActivated(com.sep490.slms2026.entity.TenantContract contract);
}
