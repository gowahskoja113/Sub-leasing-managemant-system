package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.MeterOverridePasscodeGenerateRequest;
import com.sep490.slms2026.dto.request.MeterOverrideVerifyRequest;
import com.sep490.slms2026.dto.response.MeterOverrideLogResponse;
import com.sep490.slms2026.dto.response.MeterOverridePasscodeResponse;
import com.sep490.slms2026.dto.response.MeterOverrideVerifyResponse;
import com.sep490.slms2026.entity.MeterOverrideFailCounter;
import com.sep490.slms2026.entity.MeterOverrideLog;
import com.sep490.slms2026.entity.MeterOverridePasscode;
import com.sep490.slms2026.entity.MeterOverrideToken;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.repository.MeterOverrideFailCounterRepository;
import com.sep490.slms2026.repository.MeterOverrideLogRepository;
import com.sep490.slms2026.repository.MeterOverridePasscodeRepository;
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
import java.security.SecureRandom;
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
    private static final int CODE_DIGITS = 6;
    private static final int MAX_GEN_ATTEMPTS = 20;
    /** Trần số mã một admin được gen trong cửa sổ 1 giờ (chống spam chìa khoá bypass ảnh). */
    private static final int MAX_GEN_PER_HOUR = 20;

    private final MeterOverrideTokenRepository tokenRepository;
    private final MeterOverridePasscodeRepository passcodeRepository;
    private final MeterOverrideLogRepository logRepository;
    private final MeterOverrideFailCounterRepository failCounterRepository;
    private final TenantContractRepository tenantContractRepository;
    private final UserRepository userRepository;

    private final SecureRandom secureRandom = new SecureRandom();

    /** TTL phút của mã admin gen (mặc định 10). */
    @Value("${manager.override.passcode-ttl-minutes:10}")
    private int passcodeTtlMinutes;

    /** TTL phút của overrideToken sau khi verify (mặc định 15). */
    @Value("${manager.override.ttl-minutes:15}")
    private int tokenTtlMinutes;

    @Override
    @Transactional
    public MeterOverridePasscodeResponse generatePasscode(UUID adminId, MeterOverridePasscodeGenerateRequest request) {
        LocalDateTime now = LocalDateTime.now();
        long recent = passcodeRepository.countByCreatedByAndCreatedAtAfter(adminId, now.minusHours(1));
        if (recent >= MAX_GEN_PER_HOUR) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Đã tạo quá nhiều mã trong 1 giờ. Chờ ít phút rồi thử lại.");
        }

        int ttl = passcodeTtlMinutes;
        if (request != null && request.getTtlMinutes() != null) {
            ttl = Math.max(1, Math.min(request.getTtlMinutes(), 60));
        }
        String note = request != null && request.getNote() != null ? request.getNote().trim() : null;
        if (note != null && note.isEmpty()) {
            note = null;
        }

        String code = generateUniqueCode(now);
        LocalDateTime expiresAt = now.plusMinutes(ttl);

        MeterOverridePasscode saved = passcodeRepository.save(MeterOverridePasscode.builder()
                .code(code)
                .createdBy(adminId)
                .note(note)
                .expiresAt(expiresAt)
                .createdAt(now)
                .build());

        log.info("Admin {} gen meter-override passcode id={} expiresAt={}", adminId, saved.getId(), expiresAt);

        return MeterOverridePasscodeResponse.builder()
                .id(saved.getId())
                .code(saved.getCode())
                .createdBy(saved.getCreatedBy())
                .note(saved.getNote())
                .expiresAt(saved.getExpiresAt())
                .usedAt(null)
                .usedBy(null)
                .createdAt(saved.getCreatedAt())
                .usable(true)
                .message("Gửi mã này cho manager. Mã dùng 1 lần, hết hạn sau " + ttl + " phút.")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeterOverridePasscodeResponse> listPasscodes(boolean activeOnly) {
        LocalDateTime now = LocalDateTime.now();
        List<MeterOverridePasscode> rows = activeOnly
                ? passcodeRepository.findActive(now)
                : passcodeRepository.findAllOrderByCreatedAtDesc();
        return rows.stream().map(p -> toPasscodeResponse(p, now)).toList();
    }

    @Override
    @Transactional
    public MeterOverrideVerifyResponse verifyPasscode(UUID managerId, MeterOverrideVerifyRequest request) {
        // contractId null = đang giữa luồng đón khách (HĐ chưa tạo); chỉ validate khi có id
        if (request.getContractId() != null) {
            tenantContractRepository.findById(request.getContractId())
                    .orElseThrow(() -> new BusinessException(
                            "Không tìm thấy hợp đồng ID: " + request.getContractId()));
        }

        String kind = normalizeKind(request.getMeterKind());
        MeterOverrideFailCounter counter = failCounterRepository.findById(managerId)
                .orElse(MeterOverrideFailCounter.builder().managerId(managerId).failCount(0).build());

        LocalDateTime now = LocalDateTime.now();
        if (counter.getLockedUntil() != null && counter.getLockedUntil().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Đã nhập sai quá nhiều lần. Thử lại sau " + LOCK_MINUTES + " phút.");
        }

        String raw = request.getPasscode() != null ? request.getPasscode().trim() : "";
        MeterOverridePasscode passcode = passcodeRepository.findUsableByCode(raw, now).orElse(null);

        if (passcode == null) {
            counter.setFailCount(counter.getFailCount() + 1);
            if (counter.getFailCount() >= MAX_FAILS) {
                counter.setLockedUntil(now.plusMinutes(LOCK_MINUTES));
                counter.setFailCount(0);
            }
            failCounterRepository.save(counter);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Mã không đúng hoặc đã hết hạn. Liên hệ admin để lấy mã mới.");
        }

        // Tiêu thụ mã OTP ngay — dùng 1 lần là chết
        passcode.setUsedAt(now);
        passcode.setUsedBy(managerId);
        passcodeRepository.save(passcode);

        counter.setFailCount(0);
        counter.setLockedUntil(null);
        failCounterRepository.save(counter);

        UUID token = UUID.randomUUID();
        LocalDateTime tokenExpires = now.plusMinutes(Math.max(1, tokenTtlMinutes));
        tokenRepository.save(MeterOverrideToken.builder()
                .token(token)
                .managerId(managerId)
                .contractId(request.getContractId())
                .meterKind(kind)
                .expiresAt(tokenExpires)
                .createdAt(now)
                .build());

        return MeterOverrideVerifyResponse.builder()
                .valid(true)
                .overrideToken(token)
                .expiresAt(tokenExpires)
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
        // Token xin trước khi HĐ tồn tại thì contractId = null — không so khớp
        if (token.getContractId() != null && !token.getContractId().equals(contractId)) {
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
                .collect(Collectors.toMap(u -> u.getId(),
                        u -> u.getFullName() != null ? u.getFullName() : "", (a, b) -> a));
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

    private String generateUniqueCode(LocalDateTime now) {
        for (int i = 0; i < MAX_GEN_ATTEMPTS; i++) {
            int bound = (int) Math.pow(10, CODE_DIGITS);
            String code = String.format("%0" + CODE_DIGITS + "d", secureRandom.nextInt(bound));
            if (!passcodeRepository.existsByCodeAndUsedAtIsNullAndExpiresAtAfter(code, now)) {
                return code;
            }
        }
        throw new BusinessException("Không tạo được mã duy nhất — thử lại");
    }

    private static MeterOverridePasscodeResponse toPasscodeResponse(MeterOverridePasscode p, LocalDateTime now) {
        boolean usable = p.isUsable(now);
        return MeterOverridePasscodeResponse.builder()
                .id(p.getId())
                // Còn dùng được thì hiện full; đã chết thì vẫn hiện (audit admin)
                .code(p.getCode())
                .createdBy(p.getCreatedBy())
                .note(p.getNote())
                .expiresAt(p.getExpiresAt())
                .usedAt(p.getUsedAt())
                .usedBy(p.getUsedBy())
                .createdAt(p.getCreatedAt())
                .usable(usable)
                .message(usable ? "Còn hiệu lực" : (p.getUsedAt() != null ? "Đã dùng" : "Hết hạn"))
                .build();
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
