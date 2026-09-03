package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.billing.InvoicePaymentContext;
import com.sep490.slms2026.dto.response.BillingRealtimeEvent;
import com.sep490.slms2026.dto.response.MaintenanceRealtimeEvent;
import com.sep490.slms2026.entity.MaintenanceRequest;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Room;
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
    private static final String MAINTENANCE_USER_DESTINATION = "/queue/maintenance";

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
        sendToRole(Role.ROLE_ADMIN, BILLING_USER_DESTINATION, event, sent);
        sendToRole(Role.ROLE_OWNER, BILLING_USER_DESTINATION, event, sent);
        sendByUserId(property != null ? property.getOperationManagerId() : null, BILLING_USER_DESTINATION, event, sent);
        if (contract != null && contract.getAssignedManager() != null) {
            sendToUser(contract.getAssignedManager(), BILLING_USER_DESTINATION, event, sent);
        }
        sendByUserId(invoice.getTenantUserId(), BILLING_USER_DESTINATION, event, sent);
        if (contract != null && contract.getTenant() != null && contract.getTenant().getUser() != null) {
            sendToUser(contract.getTenant().getUser(), BILLING_USER_DESTINATION, event, sent);
        }
    }

    @Override
    public void publishContractConfirmProgress(TenantContract contract) {
        publishContractEvent(contract, "CONTRACT_CONFIRM_PROGRESS");
    }

    @Override
    public void publishContractActivated(TenantContract contract) {
        publishContractEvent(contract, "CONTRACT_ACTIVATED");
    }

    private void publishContractEvent(TenantContract contract, String eventName) {
        if (contract == null || contract.getId() == null) {
            return;
        }
        Property property = contract.getProperty();
        BillingRealtimeEvent event = BillingRealtimeEvent.builder()
                .event(eventName)
                .contractId(contract.getId())
                .propertyId(property != null ? property.getId() : null)
                .propertyName(property != null ? property.getPropertyName() : null)
                .roomNumber(contract.getRoom() != null ? contract.getRoom().getRoomNumber() : null)
                .tenantUserId(contract.getTenant() != null && contract.getTenant().getUser() != null
                        ? contract.getTenant().getUser().getId() : null)
                .tenantName(contract.getTenant() != null && contract.getTenant().getUser() != null
                        ? contract.getTenant().getUser().getFullName()
                        : contract.getDraftTenantName())
                .tenantOtpVerified(contract.getTenantOtpVerifiedAt() != null)
                .managerOtpVerified(contract.getManagerOtpVerifiedAt() != null)
                .contractStatus(contract.getStatus() != null ? contract.getStatus().name() : null)
                .paymentStatus(contract.getPaymentStatus() != null ? contract.getPaymentStatus().name() : null)
                .build();

        Set<UUID> sent = new HashSet<>();
        sendToRole(Role.ROLE_ADMIN, BILLING_USER_DESTINATION, event, sent);
        if (property != null) {
            sendByUserId(property.getOperationManagerId(), BILLING_USER_DESTINATION, event, sent);
        }
        if (contract.getAssignedManager() != null) {
            sendToUser(contract.getAssignedManager(), BILLING_USER_DESTINATION, event, sent);
        }
        if (contract.getOnboardedByManager() != null) {
            sendToUser(contract.getOnboardedByManager(), BILLING_USER_DESTINATION, event, sent);
        }
        if (contract.getTenant() != null && contract.getTenant().getUser() != null) {
            sendToUser(contract.getTenant().getUser(), BILLING_USER_DESTINATION, event, sent);
        }
    }

    @Override
    public void publishMaintenanceEvent(MaintenanceRequest request, String eventType) {
        if (request == null || request.getId() == null || eventType == null || eventType.isBlank()) {
            return;
        }
        try {
            doPublishMaintenanceEvent(request, eventType.trim());
        } catch (Exception e) {
            log.error("Failed to publish maintenance websocket event {} for request {}: {}",
                    eventType, request.getId(), e.getMessage(), e);
        }
    }

    private void doPublishMaintenanceEvent(MaintenanceRequest request, String eventType) {
        Property property = request.getProperty();
        Room room = request.getRoom();
        UUID tenantUserId = request.getTenant() != null && request.getTenant().getUser() != null
                ? request.getTenant().getUser().getId()
                : (request.getTenant() != null ? request.getTenant().getId() : null);
        UUID assignedManagerId = property != null ? property.getOperationManagerId() : null;

        String requestCode = request.getRequestCode();
        if (requestCode == null || requestCode.isBlank()) {
            requestCode = "M-" + request.getId();
        }

        MaintenanceRealtimeEvent event = MaintenanceRealtimeEvent.builder()
                .event(eventType)
                .requestId(request.getId())
                .requestCode(requestCode)
                .status(request.getStatus() != null ? request.getStatus().name() : null)
                .propertyId(property != null ? property.getId() : null)
                .propertyName(property != null ? property.getPropertyName() : null)
                .roomId(room != null ? room.getId() : null)
                .roomNumber(room != null ? room.getRoomNumber() : null)
                .tenantUserId(tenantUserId)
                .assignedManagerId(assignedManagerId)
                .adminApproved(request.getAdminApproved())
                .build();

        Set<UUID> sent = new HashSet<>();
        switch (eventType) {
            case EVT_MAINTENANCE_CREATED,
                 EVT_MAINTENANCE_ADMIN_REVIEWED,
                 EVT_MAINTENANCE_SELF_REPAIR_SUBMITTED,
                 EVT_MAINTENANCE_CANCELLED_BY_TENANT -> sendToPropertyManagers(property, event, sent);

            case EVT_MAINTENANCE_APPROVED,
                 EVT_MAINTENANCE_REJECT_FAULT,
                 EVT_MAINTENANCE_VERIFY_REPAIR,
                 EVT_MAINTENANCE_COMPLETED,
                 EVT_MAINTENANCE_CANCELLED_BY_MANAGER -> sendByUserId(tenantUserId, MAINTENANCE_USER_DESTINATION, event, sent);

            case EVT_MAINTENANCE_FAULT_REPORTED -> {
                sendToRole(Role.ROLE_ADMIN, MAINTENANCE_USER_DESTINATION, event, sent);
                sendByUserId(tenantUserId, MAINTENANCE_USER_DESTINATION, event, sent);
            }

            default -> {
                // Fallback an toàn: đẩy cho tenant + manager phụ trách (+ admin nếu có adminApproved).
                log.warn("Unknown maintenance eventType '{}', broadcasting to tenant+manager", eventType);
                sendByUserId(tenantUserId, MAINTENANCE_USER_DESTINATION, event, sent);
                sendToPropertyManagers(property, event, sent);
            }
        }

        log.debug("Published {} to /user/queue/maintenance for request {} ({} recipients)",
                eventType, request.getId(), sent.size());
    }

    /** Manager phụ trách nhà; nếu chưa gán thì gửi mọi ROLE_MANAGER đang ACTIVE. */
    private void sendToPropertyManagers(Property property, MaintenanceRealtimeEvent event, Set<UUID> sent) {
        UUID managerId = property != null ? property.getOperationManagerId() : null;
        if (managerId != null) {
            sendByUserId(managerId, MAINTENANCE_USER_DESTINATION, event, sent);
            return;
        }
        sendToRole(Role.ROLE_MANAGER, MAINTENANCE_USER_DESTINATION, event, sent);
    }

    private String resolveUserName(UUID userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .map(User::getFullName)
                .orElse(null);
    }

    private void sendToRole(Role role, String destination, Object event, Set<UUID> sent) {
        userRepository.findByRoleAndStatus(role, UserStatus.ACTIVE)
                .forEach(user -> sendToUser(user, destination, event, sent));
    }

    private void sendByUserId(UUID userId, String destination, Object event, Set<UUID> sent) {
        if (userId == null || sent.contains(userId)) {
            return;
        }
        userRepository.findById(userId).ifPresent(user -> sendToUser(user, destination, event, sent));
    }

    private void sendToUser(User user, String destination, Object event, Set<UUID> sent) {
        if (user == null || user.getUsername() == null || user.getUsername().isBlank()) {
            return;
        }
        if (user.getStatus() != null && user.getStatus() != UserStatus.ACTIVE) {
            return;
        }
        if (user.getId() != null && !sent.add(user.getId())) {
            return;
        }
        messagingTemplate.convertAndSendToUser(user.getUsername(), destination, event);
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
