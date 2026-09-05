package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.event.InvoicePaidEvent;
import com.sep490.slms2026.service.TenantBillingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bắn WebSocket/push SAU khi transaction thanh toán đã commit, để FE nạp lại ra bản mới.
 */
@Slf4j
@Component
public class InvoicePaidEventListener {

    private final TenantBillingService tenantBillingService;

    public InvoicePaidEventListener(@Lazy TenantBillingService tenantBillingService) {
        this.tenantBillingService = tenantBillingService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onInvoicePaid(InvoicePaidEvent event) {
        if (event == null || event.invoiceId() == null) {
            return;
        }
        try {
            tenantBillingService.handleInvoicePaidAfterCommit(event.invoiceId(), event.context());
            if (event.sendPaymentNotification()) {
                tenantBillingService.sendPaymentNotificationsAfterCommit(
                        event.invoiceId(), event.context());
            }
        } catch (Exception e) {
            log.error("Failed to publish invoice-paid after commit id={}", event.invoiceId(), e);
        }
    }
}
