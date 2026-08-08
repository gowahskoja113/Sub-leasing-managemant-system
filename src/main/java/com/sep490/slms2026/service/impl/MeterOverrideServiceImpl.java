package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.MeterOverrideVerifyRequest;
import com.sep490.slms2026.dto.response.MeterOverrideLogResponse;
import com.sep490.slms2026.dto.response.MeterOverrideVerifyResponse;
import com.sep490.slms2026.entity.MeterOverrideFailCounter;
import com.sep490.slms2026.entity.MeterOverrideLog;
import com.sep490.slms2026.entity.MeterOverrideToken;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.repository.MeterOverrideFailCounterRepository;
import com.sep490.slms2026.repository.MeterOverrideLogRepository;
import com.sep490.slms2026.repository.MeterOverrideTokenRepository;
import com.sep490.slms2026.repository.TenantContractRepository;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.service.MeterOverrideService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeterOverrideServiceImpl implements MeterOverrideService {

    private static final int MAX_FAILS = 5;
    private static final int LOCK_MINUTES = 5;

    private final MeterOverrideTokenRepository tokenRepository;
    private final MeterOverrideLogRepository logRepository;
    private final MeterOverrideFailCounterRepository failCounterRepository;
    private final TenantContractRepository tenantContractRepository;
    private final UserRepository userRepository;

    @Value("${manager.override.passcode:}")
    private String configuredPasscode;

    @Value("${manager.override.ttl-minutes:15}")
    private int ttlMinutes;

    @Override
    @Transactional
    public MeterOverrideVerifyResponse verifyPasscode(UUID managerId, MeterOverrideVerifyRequest request) {
        if (configuredPasscode == null || configuredPasscode.isBlank()) {
            throw new BusinessException("Chưa cấu hình MANAGER_OVERRIDE_PASSCODE — liên hệ admin");
        }
        tenantContractRepository.findById(request.getContractId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy hợp đồng ID: " + request.getContractId()));

        String kind = normalizeKind(request.getMeterKind());
        MeterOverrideFailCounter counter = failCounterRepository.findById(managerId)
                .orElse(MeterOverrideFailCounter.builder().managerId(managerId).failCount(0).build());

        LocalDateTime now = LocalDateTime.now();
        if (counter.getLockedUntil() != null && counter.getLockedUntil().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Đã nhập sai quá nhiều lần. Thử lại sau " + LOCK_MINUTES + " phút.");
        }

        if (!configuredPasscode.equals(request.getPasscode())) {
            counter.setFailCount(counter.getFailCount() + 1);
            if (counter.getFailCount() >= MAX_FAILS) {
                counter.setLockedUntil(now.plusMinutes(LOCK_MINUTES));
                counter.setFailCount(0);
            }
            failCounterRepository.save(counter);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Mã không đúng. Liên hệ admin để lấy mã.");
        }

        counter.setFailCount(0);
        counter.setLockedUntil(null);
        failCounterRepository.save(counter);

        UUID token = UUID.randomUUID();
        LocalDateTime expiresAt = now.plusMinutes(Math.max(1, ttlMinutes));
        tokenRepository.save(MeterOverrideToken.builder()
                .token(token)
                .managerId(managerId)
                .contractId(request.getContractId())
                .meterKind(kind)
                .expiresAt(expiresAt)
                .createdAt(now)
                .build());

        return MeterOverrideVerifyResponse.builder()
                .valid(true)
                .overrideToken(token)
                .expiresAt(expiresAt)
                .message("OK")
                .build();
    }

    @Override
    @Transactional
    public boolean consumeOverrideIfPresent(UUID managerId, Long contractId, String meterKind,
                                            UUID overrideToken, BigDecimal enteredValue, String reason) {
        if (overrideToken == null) {
            return false;
        }
        String kind = normalizeKind(meterKind);
        MeterOverrideToken token = tokenRepository.findByToken(overrideToken)
                .orElseThrow(() -> new BusinessException("Mã override không hợp lệ hoặc đã hết hạn"));

        LocalDateTime now = LocalDateTime.now();
        if (token.getUsedAt() != null) {
            throw new BusinessException("Mã override đã được sử dụng");
        }
        if (token.getExpiresAt().isBefore(now)) {
            throw new BusinessException("Mã override đã hết hạn");
        }
        if (!token.getManagerId().equals(managerId)) {
            throw new BusinessException("Mã override không thuộc tài khoản hiện tại");
        }
        if (!token.getContractId().equals(contractId)) {
            throw new BusinessException("Mã override không khớp hợp đồng");
        }
        if (!token.getMeterKind().equalsIgnoreCase(kind)) {
            throw new BusinessException("Mã override không khớp loại đồng hồ (" + kind + ")");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("Bắt buộc ghi lý do khi nhập chỉ số thủ công");
        }

        token.setUsedAt(now);
        tokenRepository.save(token);

        logRepository.save(MeterOverrideLog.builder()
                .managerId(managerId)
                .contractId(contractId)
                .meterKind(kind)
                .enteredValue(enteredValue)
                .reason(reason.trim())
                .createdAt(now)
                .build());
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeterOverrideLogResponse> listLogs() {
        Map<UUID, String> names = userRepository.findAll().stream()
                .collect(Collectors.toMap(u -> u.getId(), u -> u.getFullName() != null ? u.getFullName() : "", (a, b) -> a));
        return logRepository.findAllOrderByCreatedAtDesc().stream()
                .map(l -> MeterOverrideLogResponse.builder()
                        .id(l.getId())
                        .managerId(l.getManagerId())
                        .managerName(names.getOrDefault(l.getManagerId(), ""))
                        .contractId(l.getContractId())
                        .meterKind(l.getMeterKind())
                        .enteredValue(l.getEnteredValue())
                        .reason(l.getReason())
                        .createdAt(l.getCreatedAt())
                        .build())
                .toList();
    }

    private static String normalizeKind(String kind) {
        if (kind == null || kind.isBlank()) {
            throw new BusinessException("Thiếu meterKind (ELEC|WATER)");
        }
        String k = kind.trim().toUpperCase(Locale.ROOT);
        if ("ELECTRIC".equals(k) || "ELECTRICITY".equals(k)) {
            k = "ELEC";
        }
        if (!"ELEC".equals(k) && !"WATER".equals(k)) {
            throw new BusinessException("meterKind phải là ELEC hoặc WATER");
        }
        return k;
    }
}
