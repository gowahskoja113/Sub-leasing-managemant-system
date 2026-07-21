package com.sep490.slms2026.config;

import com.sep490.slms2026.service.TenantOnboardingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cron vòng đời hợp đồng thuê.
 * Hiện tại: tự động hủy HĐ nháp/chờ (DRAFT/PENDING) khi khách không đến nhận nhà quá hạn (no-show).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractLifecycleCron {

    private final TenantOnboardingService tenantOnboardingService;

    /** Chạy mỗi ngày 08:05 (sau billing sweep 08:00). */
    @Scheduled(cron = "0 5 8 * * *", zone = "Asia/Ho_Chi_Minh")
    public void autoCancelNoShowContracts() {
        int cancelled = tenantOnboardingService.autoCancelNoShowContracts();
        if (cancelled > 0) {
            log.info("ContractLifecycleCron: đã tự động hủy {} hợp đồng no-show", cancelled);
        }
    }
}
