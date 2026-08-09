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

    private final CheckoutRequestRepository checkoutRequestRepository;
    private final CheckoutSettlementRepository checkoutSettlementRepository;
    private final CheckoutProcessService checkoutProcessService;

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
}
