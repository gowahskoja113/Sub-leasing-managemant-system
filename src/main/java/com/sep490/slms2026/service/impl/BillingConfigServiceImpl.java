package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.UpdateBillingConfigRequest;
import com.sep490.slms2026.dto.response.BillingConfigResponse;
import com.sep490.slms2026.entity.BillingConfig;
import com.sep490.slms2026.repository.BillingConfigRepository;
import com.sep490.slms2026.service.BillingConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillingConfigServiceImpl implements BillingConfigService {

    private final BillingConfigRepository billingConfigRepository;

    @Override
    @Transactional
    public BillingConfig current() {
        return billingConfigRepository.findById(BillingConfig.SINGLETON_ID)
                .orElseGet(() -> billingConfigRepository.save(BillingConfig.defaults()));
    }

    @Override
    @Transactional
    public BillingConfigResponse get() {
        return toResponse(current());
    }

    @Override
    @Transactional
    public BillingConfigResponse update(UpdateBillingConfigRequest request, UUID adminId) {
        BillingConfig config = current();
        config.setReminderLeadDays(request.getReminderLeadDays());
        config.setGraceDays(request.getGraceDays());
        if (request.getMeterReminderLeadDays() != null) {
            config.setMeterReminderLeadDays(request.getMeterReminderLeadDays());
        }
        config.setUpdatedAt(LocalDateTime.now());
        config.setUpdatedBy(adminId);
        return toResponse(billingConfigRepository.save(config));
    }

    private BillingConfigResponse toResponse(BillingConfig config) {
        return BillingConfigResponse.builder()
                .reminderLeadDays(config.getReminderLeadDays())
                .graceDays(config.getGraceDays())
                .meterReminderLeadDays(config.getMeterReminderLeadDays())
                .updatedAt(config.getUpdatedAt())
                .build();
    }
}
