package com.sep490.slms2026.service;

import com.sep490.slms2026.entity.TenantInvoice;

public interface RealtimeEventService {
    void publishInvoicePaid(TenantInvoice invoice);
}
