package com.sep490.slms2026.config;

import com.sep490.slms2026.service.TenantOnboardingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * One-shot backfill: HĐ DRAFT/PENDING/ACTIVE còn {@code assignedManager = null}
 * trong khi nhà đã có {@code operationManagerId} (tạo trước khi auto-assign deploy).
 * Idempotent — chạy lại không đụng HĐ đã có manager.
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class AssignedManagerBackfillRunner implements ApplicationRunner {

    private final TenantOnboardingService tenantOnboardingService;

    @Override
    public void run(ApplicationArguments args) {
        int count = tenantOnboardingService.backfillMissingAssignedManagers();
        if (count > 0) {
            log.info("AssignedManagerBackfillRunner: đã backfill {} hợp đồng (assignedManager ← property.operationManagerId)",
                    count);
        } else {
            log.info("AssignedManagerBackfillRunner: không có hợp đồng nào cần backfill");
        }
    }
}
