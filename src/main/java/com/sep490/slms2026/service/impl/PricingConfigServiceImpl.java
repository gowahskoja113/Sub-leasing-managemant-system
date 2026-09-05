package com.sep490.slms2026.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.slms2026.dto.request.UpdatePricingConfigRequest;
import com.sep490.slms2026.dto.response.PricingConfigResponse;
import com.sep490.slms2026.entity.PricingConfig;
import com.sep490.slms2026.enums.PricingMode;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.repository.PricingConfigRepository;
import com.sep490.slms2026.service.PricingConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PricingConfigServiceImpl implements PricingConfigService {

    private final PricingConfigRepository pricingConfigRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public PricingConfig current() {
        return pricingConfigRepository.findById(PricingConfig.SINGLETON_ID)
                .orElseGet(() -> pricingConfigRepository.save(PricingConfig.defaults()));
    }

    @Override
    @Transactional
    public PricingConfigResponse get() {
        return toResponse(current());
    }

    @Override
    @Transactional
    public PricingConfigResponse update(UpdatePricingConfigRequest request, UUID actorId) {
        PricingConfig config = current();
        PricingMode mode = request.getMode();
        if (mode == PricingMode.FORWARD && request.getPDesired() == null) {
            throw new BusinessException("pDesired bắt buộc khi mode = FORWARD");
        }
        if (mode == PricingMode.REVERSE && request.getRoiExpected() == null) {
            throw new BusinessException("roiExpected bắt buộc khi mode = REVERSE");
        }

        config.setMode(mode);
        config.setPDesired(request.getPDesired() != null ? request.getPDesired() : BigDecimal.ZERO);
        config.setRoiExpected(request.getRoiExpected() != null ? request.getRoiExpected() : BigDecimal.ZERO);
        config.setOOperation(request.getOOperation());
        config.setManagerSalariesJson(writeSalaries(sanitizeSalaries(request.getManagerSalaries())));
        config.setAnnualIncreasePct(request.getAnnualIncreasePct());
        config.setEscalationGraceMonths(request.getEscalationGraceMonths());
        config.setNewYearPriceLeadMonths(request.getNewYearPriceLeadMonths());
        config.setVRatePct(request.getVRatePct());
        config.setHandoverBufferMonths(request.getHandoverBufferMonths());
        config.setUpdatedAt(LocalDateTime.now());
        config.setUpdatedBy(actorId);
        return toResponse(pricingConfigRepository.save(config));
    }

    private PricingConfigResponse toResponse(PricingConfig config) {
        return PricingConfigResponse.builder()
                .mode(config.getMode())
                .pDesired(config.getPDesired())
                .roiExpected(config.getRoiExpected())
                .oOperation(config.getOOperation())
                .managerSalaries(readSalaries(config.getManagerSalariesJson()))
                .annualIncreasePct(config.getAnnualIncreasePct())
                .escalationGraceMonths(config.getEscalationGraceMonths())
                .newYearPriceLeadMonths(config.getNewYearPriceLeadMonths())
                .vRatePct(config.getVRatePct())
                .handoverBufferMonths(config.getHandoverBufferMonths())
                .updatedAt(config.getUpdatedAt())
                .build();
    }

    private static Map<UUID, BigDecimal> sanitizeSalaries(Map<UUID, BigDecimal> raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<UUID, BigDecimal> cleaned = new LinkedHashMap<>();
        for (Map.Entry<UUID, BigDecimal> e : raw.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            if (e.getValue().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            cleaned.put(e.getKey(), e.getValue());
        }
        return cleaned;
    }

    private String writeSalaries(Map<UUID, BigDecimal> salaries) {
        try {
            return objectMapper.writeValueAsString(salaries != null ? salaries : Map.of());
        } catch (Exception e) {
            throw new BusinessException("Không ghi được managerSalaries");
        }
    }

    private Map<UUID, BigDecimal> readSalaries(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, BigDecimal> raw = objectMapper.readValue(json, new TypeReference<>() {
            });
            Map<UUID, BigDecimal> result = new LinkedHashMap<>();
            for (Map.Entry<String, BigDecimal> e : raw.entrySet()) {
                if (e.getKey() == null || e.getValue() == null || e.getValue().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                try {
                    result.put(UUID.fromString(e.getKey()), e.getValue());
                } catch (IllegalArgumentException ignored) {
                    log.debug("Bỏ qua managerSalaries key không phải UUID: {}", e.getKey());
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("manager_salaries_json không đọc được: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
