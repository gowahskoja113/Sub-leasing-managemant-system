package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.billing.InvoicePaymentContext;
import com.sep490.slms2026.dto.response.BillingRealtimeEvent;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.entity.TenantInvoice;
import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.enums.PaymentCollectionMode;
import com.sep490.slms2026.enums.Role;
import com.sep490.slms2026.enums.UserStatus;
import com.sep490.slms2026.repository.TenantInvoiceRepository;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.service.RealtimeEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeEventServiceImpl implements RealtimeEventService {

    private static final String BILLING_USER_DESTINATION = "/queue/billing";

    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;
    private final TenantInvoiceRepository tenantInvoiceRepository;

    @Override
    public void publishInvoicePaid(TenantInvoice invoice) {
        publishInvoicePaid(invoice, InvoicePaymentContext.selfQr());
    }

    /**
     * Chỉ gọi sau khi transaction ghi PAID đã commit (từ {@link InvoicePaidEventListener}).
     * Reload từ DB để payload WS khớp dữ liệu FE nạp lại ngay sau event.
     */
    @Override
    public void publishInvoicePaid(TenantInvoice invoice, InvoicePaymentContext context) {
        if (invoice == null || invoice.getId() == null) {
            return;
        }
        doPublishInvoicePaid(invoice.getId(), context != null ? context : InvoicePaymentContext.selfQr());
    }

    private void doPublishInvoicePaid(Long invoiceId, InvoicePaymentContext ctx) {
        TenantInvoice invoice = tenantInvoiceRepository.findByIdForRealtime(invoiceId).orElse(null);
        if (invoice == null) {
            log.warn("Skip INVOICE_PAID websocket: invoice {} not found", invoiceId);
            return;
        }

        TenantContract contract = invoice.getTenantContract();
        Property property = contract != null ? contract.getProperty() : null;

        String remittedByName = resolveUserName(ctx.remittedBy());
        String payerName = ctx.payerName();
        String unlockedByAdminName = resolveUserName(ctx.unlockedByAdmin());

        BillingRealtimeEvent event = BillingRealtimeEvent.builder()
                .event("INVOICE_PAID")
                .invoiceId(invoice.getId())
                .invoiceCode(invoice.getCode())
                .invoiceType(invoice.getInvoiceType() != null ? invoice.getInvoiceType().name() : null)
                .cycleType(invoice.getCycleType() != null ? invoice.getCycleType().name() : null)
                .status(invoice.getStatus() != null ? invoice.getStatus().name() : null)
                .propertyId(property != null ? property.getId() : null)
                .propertyName(invoice.getPropertyName())
                .roomNumber(invoice.getRoomNumber())
                .contractId(contract != null ? contract.getId() : null)
                .tenantUserId(invoice.getTenantUserId())
                .tenantName(resolveTenantName(invoice))
                .billingMonth(invoice.getBillingMonth())
                .billingYear(invoice.getBillingYear())
                .billingPeriod(invoice.getBillingPeriod())
                .utilityInvoiceId(invoice.getUtilityInvoiceId())
                .paymentMethod(invoice.getPaymentMethod())
                .transactionId(invoice.getTransactionId())
                .paidAt(invoice.getPaidAt())
                .collectionMode(ctx.collectionMode() != null ? ctx.collectionMode().name() : PaymentCollectionMode.SELF.name())
                .remittedByName(remittedByName)
                .payerName(payerName)
                .unlockedByAdminName(unlockedByAdminName)
                .build();

        Set<UUID> sent = new HashSet<>();
        sendToRole(Role.ROLE_ADMIN, event, sent);
        sendToRole(Role.ROLE_OWNER, event, sent);
        sendByUserId(property != null ? property.getOperationManagerId() : null, event, sent);
        sendByUserId(property != null ? property.getManagedBy() : null, event, sent);
        if (contract != null && contract.getAssignedManager() != null) {
            sendToUser(contract.getAssignedManager(), event, sent);
        }
        sendByUserId(invoice.getTenantUserId(), event, sent);
        if (contract != null && contract.getTenant() != null && contract.getTenant().getUser() != null) {
            sendToUser(contract.getTenant().getUser(), event, sent);
        }
    }

    private String resolveUserName(UUID userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .map(User::getFullName)
                .orElse(null);
    }

    private void sendToRole(Role role, BillingRealtimeEvent event, Set<UUID> sent) {
        userRepository.findByRoleAndStatus(role, UserStatus.ACTIVE)
                .forEach(user -> sendToUser(user, event, sent));
    }

    private void sendByUserId(UUID userId, BillingRealtimeEvent event, Set<UUID> sent) {
        if (userId == null || sent.contains(userId)) {
            return;
        }
        userRepository.findById(userId).ifPresent(user -> sendToUser(user, event, sent));
    }

    private void sendToUser(User user, BillingRealtimeEvent event, Set<UUID> sent) {
        if (user == null || user.getUsername() == null || user.getUsername().isBlank()) {
            return;
        }
        if (user.getStatus() != null && user.getStatus() != UserStatus.ACTIVE) {
            return;
        }
        if (user.getId() != null && !sent.add(user.getId())) {
            return;
        }
        messagingTemplate.convertAndSendToUser(user.getUsername(), BILLING_USER_DESTINATION, event);
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
