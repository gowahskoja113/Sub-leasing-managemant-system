package com.sep490.slms2026.config;

import com.sep490.slms2026.service.TenantOnboardingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cron vòng đời hợp đồng thuê.
 * Nhắc đón khách 07:15 — trước auto-cancel no-show 08:05.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractLifecycleCron {

    private final TenantOnboardingService tenantOnboardingService;
    private final com.sep490.slms2026.service.TenantCheckoutService tenantCheckoutService;

    @Scheduled(cron = "0 25 8 * * *", zone = "Asia/Ho_Chi_Minh")
    public void expireDueContracts() {
        tenantCheckoutService.processExpiredContracts();
        tenantCheckoutService.processContractExpirationReminders();
        log.info("ContractLifecycleCron: đã xử lý hợp đồng hết hạn và nhắc nhở");
    }

    @Scheduled(cron = "0 15 7 * * *", zone = "Asia/Ho_Chi_Minh")
    public void remindUpcomingReception() {
        int reminded = tenantOnboardingService.remindUpcomingReception();
        if (reminded > 0) {
            log.info("ContractLifecycleCron: đã nhắc đón khách {} hợp đồng", reminded);
        }
    }

    /** Chạy mỗi ngày 08:05 (sau billing sweep 08:00). */
    @Scheduled(cron = "0 5 8 * * *", zone = "Asia/Ho_Chi_Minh")
    public void autoCancelNoShowContracts() {
        int cancelled = tenantOnboardingService.autoCancelNoShowContracts();
        if (cancelled > 0) {
            log.info("ContractLifecycleCron: đã tự động hủy {} hợp đồng no-show", cancelled);
        }
    }
}
