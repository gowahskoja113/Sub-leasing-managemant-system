package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.response.BillingRealtimeEvent;
import com.sep490.slms2026.entity.TenantInvoice;
import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.enums.Role;
import com.sep490.slms2026.enums.UserStatus;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.service.RealtimeEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RealtimeEventServiceImpl implements RealtimeEventService {

    private static final String BILLING_USER_DESTINATION = "/queue/billing";

    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    @Override
    public void publishInvoicePaid(TenantInvoice invoice) {
        if (invoice == null) {
            return;
        }

        BillingRealtimeEvent event = BillingRealtimeEvent.builder()
                .event("INVOICE_PAID")
                .invoiceId(invoice.getId())
                .invoiceCode(invoice.getCode())
                .invoiceType(invoice.getInvoiceType() != null ? invoice.getInvoiceType().name() : null)
                .status(invoice.getStatus() != null ? invoice.getStatus().name() : null)
                .propertyName(invoice.getPropertyName())
                .roomNumber(invoice.getRoomNumber())
                .tenantName(resolveTenantName(invoice))
                .paymentMethod(invoice.getPaymentMethod())
                .transactionId(invoice.getTransactionId())
                .paidAt(invoice.getPaidAt())
                .build();

        sendToAdmins(event);
        sendToManager(invoice, event);
    }

    private void sendToAdmins(BillingRealtimeEvent event) {
        List<User> admins = userRepository.findByRoleAndStatus(Role.ROLE_ADMIN, UserStatus.ACTIVE);
        admins.forEach(admin -> messagingTemplate.convertAndSendToUser(
                admin.getUsername(), BILLING_USER_DESTINATION, event));
    }

    private void sendToManager(TenantInvoice invoice, BillingRealtimeEvent event) {
        UUID managerId = invoice.getTenantContract() != null
                && invoice.getTenantContract().getProperty() != null
                ? invoice.getTenantContract().getProperty().getOperationManagerId()
                : null;
        if (managerId == null) {
            return;
        }

        userRepository.findById(managerId).ifPresent(manager ->
                messagingTemplate.convertAndSendToUser(
                        manager.getUsername(), BILLING_USER_DESTINATION, event));
    }

    private String resolveTenantName(TenantInvoice invoice) {
        if (invoice.getTenantContract() == null) {
            return null;
        }
        if (invoice.getTenantContract().getTenant() != null
                && invoice.getTenantContract().getTenant().getUser() != null) {
            return invoice.getTenantContract().getTenant().getUser().getFullName();
        }
        return invoice.getTenantContract().getDraftTenantName();
    }
}
