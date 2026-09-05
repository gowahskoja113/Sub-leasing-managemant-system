package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.entity.CheckoutRequest;
import com.sep490.slms2026.enums.CheckoutRequestStatus;
import com.sep490.slms2026.repository.CheckoutRequestRepository;
import com.sep490.slms2026.repository.CheckoutSettlementRepository;
import com.sep490.slms2026.service.CheckoutCronService;
import com.sep490.slms2026.service.CheckoutProcessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutCronServiceImpl implements CheckoutCronService {

    private static final int REFUND_SILENCE_DISABLE_DAYS = 30;

    private final CheckoutRequestRepository checkoutRequestRepository;
    private final CheckoutSettlementRepository checkoutSettlementRepository;
    private final CheckoutProcessService checkoutProcessService;
    private final com.sep490.slms2026.repository.UserRepository userRepository;
    private final com.sep490.slms2026.service.PushNotificationService pushNotificationService;
    private final com.sep490.slms2026.service.TwilioService twilioService;
    private final com.sep490.slms2026.repository.TenantInvoiceRepository tenantInvoiceRepository;
    private final com.sep490.slms2026.repository.NotificationRepository notificationRepository;
    private final com.sep490.slms2026.service.TenantOnboardingService tenantOnboardingService;

    @Override
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Ho_Chi_Minh") // Run at midnight every day
    @Transactional
    public void autoAcceptSettlementTask() {
        log.info("Starting autoAcceptSettlementTask...");
        List<CheckoutRequest> waitingRequests = checkoutRequestRepository.findByStatusOrderByCreatedAtDesc(CheckoutRequestStatus.WAITING_TENANT);
        
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        int autoAcceptedCount = 0;

        for (CheckoutRequest request : waitingRequests) {
            java.util.Optional<com.sep490.slms2026.entity.CheckoutSettlement> settlementOpt = checkoutSettlementRepository.findByCheckoutRequestId(request.getId());
            if (settlementOpt.isPresent()) {
                com.sep490.slms2026.entity.CheckoutSettlement settlement = settlementOpt.get();
                // If the settlement was created or updated more than 7 days ago, auto accept
                LocalDateTime referenceTime = settlement.getUpdatedAt() != null ? settlement.getUpdatedAt() : settlement.getCreatedAt();
                if (referenceTime != null && referenceTime.isBefore(sevenDaysAgo)) {
                    log.info("Auto-accepting checkout request ID = {} (waiting since {})", request.getId(), referenceTime);
                    try {
                        checkoutProcessService.acceptSettlement(request.getId(), request.getTenantUserId());
                        autoAcceptedCount++;
                    } catch (Exception e) {
                        log.error("Failed to auto-accept settlement for checkout request ID = {}", request.getId(), e);
                    }
                }
            }
        }
        
        log.info("Finished autoAcceptSettlementTask. Auto-accepted {} requests.", autoAcceptedCount);
    }

    @Override
    @Scheduled(cron = "0 15 0 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void disableAccountsAfterSilentRefund() {
        LocalDateTime paidBefore = LocalDateTime.now().minusDays(REFUND_SILENCE_DISABLE_DAYS);
        List<com.sep490.slms2026.entity.CheckoutSettlement> silent =
                checkoutSettlementRepository.findSilentRefundAwaitingAccountDisable(paidBefore);
        int disabled = 0;
        for (com.sep490.slms2026.entity.CheckoutSettlement settlement : silent) {
            CheckoutRequest request = settlement.getCheckoutRequest();
            if (request == null || request.getTenantUserId() == null) {
                continue;
            }
            try {
                Long contractId = request.getTenantContract() != null
                        ? request.getTenantContract().getId()
                        : null;
                tenantOnboardingService.disableTenantAccountIfNoActiveContracts(
                        request.getTenantUserId(), contractId);
                disabled++;
            } catch (Exception e) {
                log.error("Failed to disable tenant after silent refund, checkoutRequestId={}",
                        request.getId(), e);
            }
        }
        log.info("disableAccountsAfterSilentRefund: candidates={}, processed={}", silent.size(), disabled);
    }

    @Scheduled(cron = "0 30 8 * * *", zone = "Asia/Ho_Chi_Minh") // Run at 8:30 AM every day
    @Transactional
    public void overdueAndWarningTask() {
        log.info("Starting overdueAndWarningTask...");
        
        List<com.sep490.slms2026.entity.CheckoutSettlement> activeSettlements = checkoutSettlementRepository.findAll();
        
        java.time.LocalDate today = java.time.LocalDate.now();
        
        for (com.sep490.slms2026.entity.CheckoutSettlement settlement : activeSettlements) {
            CheckoutRequest req = settlement.getCheckoutRequest();
            if (req.getStatus() == CheckoutRequestStatus.COMPLETED || req.getStatus() == CheckoutRequestStatus.CANCELLED || req.getStatus() == CheckoutRequestStatus.REJECTED) {
                continue;
            }
            
            // Check refund due date overdue (C1, C3)
            if (settlement.getRefundDueDate() != null && settlement.getRefundPaidAt() == null) {
                if (today.isAfter(settlement.getRefundDueDate())) {
                    long overdueDays = java.time.temporal.ChronoUnit.DAYS.between(settlement.getRefundDueDate(), today);
                    if (overdueDays == 1 || overdueDays == 3 || overdueDays == 7) {
                        notifyRefundOverdue(req, settlement, overdueDays);
                    }
                }
            }
            
            // Check unpaid invoices warning (C4)
            List<com.sep490.slms2026.entity.TenantInvoice> unpaid = tenantInvoiceRepository.findByTenantContractIdAndStatusNotIn(
                        req.getTenantContract().getId(),
                        List.of(com.sep490.slms2026.enums.TenantInvoiceStatus.PAID, com.sep490.slms2026.enums.TenantInvoiceStatus.CANCELLED)
            );
            if (!unpaid.isEmpty()) {
                java.time.LocalDateTime latestCreatedAt = unpaid.stream()
                        .map(com.sep490.slms2026.entity.TenantInvoice::getCreatedAt)
                        .filter(java.util.Objects::nonNull)
                        .max(java.time.LocalDateTime::compareTo)
                        .orElse(null);
                if (latestCreatedAt != null) {
                    long daysSinceInvoice = java.time.temporal.ChronoUnit.DAYS.between(latestCreatedAt.toLocalDate(), today);
                    long daysLeft = 30 - daysSinceInvoice;
                    if (daysLeft == 3 || daysLeft == 1) {
                        notifyTenantImpendingForceSettle(req, daysLeft);
                    }
                }
            }
        }
    }
    
    private void notifyRefundOverdue(CheckoutRequest req, com.sep490.slms2026.entity.CheckoutSettlement settlement, long overdueDays) {
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("screen", "CheckoutDetail");
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("requestId", req.getId());
        data.put("params", params);
        
        String roomStr = req.getTenantContract().getRoom() != null ? req.getTenantContract().getRoom().getRoomNumber() : "Nguyên căn";
        String title = "Quá hạn hoàn cọc";
        String content = "Hoàn cọc phòng " + roomStr + " đã quá hạn " + overdueDays + " ngày. Vui lòng thanh toán ngay.";
        
        userRepository.findByRole(com.sep490.slms2026.enums.Role.ROLE_OWNER).forEach(owner -> {
            sendNotification(owner.getId(), "CHECKOUT_DEPOSIT_REFUND_OVERDUE", title, content, data);
        });
        userRepository.findByRole(com.sep490.slms2026.enums.Role.ROLE_ADMIN).forEach(admin -> {
            sendNotification(admin.getId(), "CHECKOUT_DEPOSIT_REFUND_OVERDUE", title, content, data);
        });
    }

    private void notifyTenantImpendingForceSettle(CheckoutRequest req, long daysLeft) {
        String roomStr = req.getTenantContract().getRoom() != null ? req.getTenantContract().getRoom().getRoomNumber() : "Nguyên căn";
        
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("screen", "CheckoutDetail");
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("requestId", req.getId());
        data.put("params", params);
        
        String title = "Cảnh báo sắp cấn trừ cọc";
        String content = "Bạn sắp bị cấn trừ cọc phòng " + roomStr + ". Còn " + daysLeft + " ngày để thanh toán các khoản nợ trước khi hệ thống tự động cấn trừ.";
        sendNotification(req.getTenantUserId(), "CHECKOUT_FORCE_SETTLE_WARNING", title, content, data);

        String phone = req.getTenantUserId() != null && req.getTenantContract().getTenant() != null && req.getTenantContract().getTenant().getUser() != null ? req.getTenantContract().getTenant().getUser().getPhoneNumber() : null;
        if (phone != null && !phone.isBlank()) {
            String sms = "Ban sap bi can tru coc phong " + roomStr + ". Con " + daysLeft + " ngay de thanh toan cac khoan no truoc khi he thong tu dong can tru.";
            twilioService.sendSms(phone, sms);
        }
    }

    private void sendNotification(java.util.UUID targetUserId, String type, String title, String content, java.util.Map<String, Object> data) {
        if (targetUserId == null) return;
        
        String screen = data != null ? (String) data.get("screen") : null;
        String paramsJson = null;
        if (data != null && data.get("params") != null) {
            try {
                paramsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data.get("params"));
            } catch (Exception e) {
                // Ignore parse error
            }
        }
        
        com.sep490.slms2026.entity.Notification notification = com.sep490.slms2026.entity.Notification.builder()
                .userId(targetUserId)
                .title(title)
                .content(content)
                .type(type)
                .screen(screen)
                .paramsJson(paramsJson)
                .read(false)
                .build();
        notificationRepository.save(notification);

        userRepository.findById(targetUserId).ifPresent(u -> {
            if (u.getPushToken() != null && !u.getPushToken().isBlank()) {
                pushNotificationService.sendPushNotification(u.getPushToken(), title, content, data);
            }
        });
    }
}
