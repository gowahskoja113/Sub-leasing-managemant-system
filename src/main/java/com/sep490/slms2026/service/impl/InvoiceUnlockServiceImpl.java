package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.InvoiceUnlockPasscodeGenerateRequest;
import com.sep490.slms2026.dto.request.InvoiceUnlockVerifyRequest;
import com.sep490.slms2026.dto.response.InvoiceUnlockLogResponse;
import com.sep490.slms2026.dto.response.InvoiceUnlockPasscodeResponse;
import com.sep490.slms2026.dto.response.InvoiceUnlockVerifyResponse;
import com.sep490.slms2026.entity.*;
import com.sep490.slms2026.enums.InvoiceUnlockPurpose;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.*;
import com.sep490.slms2026.service.InvoiceUnlockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceUnlockServiceImpl implements InvoiceUnlockService {

    private static final int MAX_FAILS = 3;
    private static final int LOCK_MINUTES = 15;
    private static final int CODE_DIGITS = 6;
    private static final int MAX_GEN_ATTEMPTS = 20;
    private static final int MAX_GEN_PER_HOUR = 20;

    private final InvoiceUnlockTokenRepository tokenRepository;
    private final InvoiceUnlockPasscodeRepository passcodeRepository;
    private final InvoiceUnlockLogRepository logRepository;
    private final InvoiceUnlockFailCounterRepository failCounterRepository;
    private final TenantInvoiceRepository tenantInvoiceRepository;
    private final UserRepository userRepository;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${billing.invoice-unlock.passcode-ttl-minutes:15}")
    private int passcodeTtlMinutes;

    @Value("${billing.invoice-unlock.token-ttl-minutes:15}")
    private int tokenTtlMinutes;

    @Override
    @Transactional
    public InvoiceUnlockPasscodeResponse generatePasscode(UUID adminId, InvoiceUnlockPasscodeGenerateRequest request) {
        if (request.getInvoiceId() == null || request.getPurpose() == null) {
            throw new BusinessException("invoiceId và purpose là bắt buộc");
        }
        tenantInvoiceRepository.findById(request.getInvoiceId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy hoá đơn ID=" + request.getInvoiceId()));

        LocalDateTime now = LocalDateTime.now();
        long recent = passcodeRepository.countByCreatedByAndCreatedAtAfter(adminId, now.minusHours(1));
        if (recent >= MAX_GEN_PER_HOUR) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Đã tạo quá nhiều mã trong 1 giờ. Chờ ít phút rồi thử lại.");
        }

        int ttl = passcodeTtlMinutes;
        if (request.getTtlMinutes() != null) {
            ttl = Math.max(1, Math.min(request.getTtlMinutes(), 60));
        }
        String note = request.getNote() != null ? request.getNote().trim() : null;
        if (note != null && note.isEmpty()) {
            note = null;
        }

        String code = generateUniqueCode(now);
        InvoiceUnlockPasscode saved = passcodeRepository.save(InvoiceUnlockPasscode.builder()
                .code(code)
                .invoiceId(request.getInvoiceId())
                .purpose(request.getPurpose())
                .createdBy(adminId)
                .note(note)
                .expiresAt(now.plusMinutes(ttl))
                .createdAt(now)
                .build());

        log.info("Admin {} gen invoice-unlock passcode id={} invoiceId={} purpose={}",
                adminId, saved.getId(), saved.getInvoiceId(), saved.getPurpose());

        return toPasscodeResponse(saved, now);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceUnlockPasscodeResponse> listPasscodes(boolean activeOnly) {
        LocalDateTime now = LocalDateTime.now();
        List<InvoiceUnlockPasscode> rows = activeOnly
                ? passcodeRepository.findActive(now)
                : passcodeRepository.findAllByOrderByCreatedAtDesc();
        return rows.stream().map(p -> toPasscodeResponse(p, now)).toList();
    }

    @Override
    @Transactional
    public InvoiceUnlockVerifyResponse verifyPasscode(UUID managerId, InvoiceUnlockVerifyRequest request) {
        TenantInvoice invoice = tenantInvoiceRepository.findById(request.getInvoiceId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy hoá đơn ID=" + request.getInvoiceId()));

        assertManagerCanAccessInvoice(managerId, invoice);

        InvoiceUnlockFailCounterId counterId = new InvoiceUnlockFailCounterId(managerId, invoice.getId());
        InvoiceUnlockFailCounter counter = failCounterRepository.findById(counterId)
                .orElse(InvoiceUnlockFailCounter.builder()
                        .managerId(managerId)
                        .invoiceId(invoice.getId())
                        .failCount(0)
                        .build());

        LocalDateTime now = LocalDateTime.now();
        if (counter.getLockedUntil() != null && counter.getLockedUntil().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Đã nhập sai quá nhiều lần trên hoá đơn này. Thử lại sau " + LOCK_MINUTES + " phút.");
        }

        String raw = request.getPasscode() != null ? request.getPasscode().trim() : "";
        InvoiceUnlockPasscode passcode = passcodeRepository.findUsableByCode(raw, now).orElse(null);

        if (passcode == null || !passcode.getInvoiceId().equals(invoice.getId())) {
            counter.setFailCount(counter.getFailCount() + 1);
            if (counter.getFailCount() >= MAX_FAILS) {
                counter.setLockedUntil(now.plusMinutes(LOCK_MINUTES));
                counter.setFailCount(0);
            }
            failCounterRepository.save(counter);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Mã không đúng hoặc đã hết hạn. Liên hệ admin để lấy mã mới.");
        }

        passcode.setUsedAt(now);
        passcode.setUsedBy(managerId);
        passcodeRepository.save(passcode);

        counter.setFailCount(0);
        counter.setLockedUntil(null);
        failCounterRepository.save(counter);

        UUID token = UUID.randomUUID();
        LocalDateTime tokenExpires = now.plusMinutes(Math.max(1, tokenTtlMinutes));
        tokenRepository.save(InvoiceUnlockToken.builder()
                .token(token)
                .managerId(managerId)
                .invoiceId(invoice.getId())
                .purpose(passcode.getPurpose())
                .passcodeId(passcode.getId())
                .unlockedByAdmin(passcode.getCreatedBy())
                .expiresAt(tokenExpires)
                .createdAt(now)
                .build());

        logRepository.save(InvoiceUnlockLog.builder()
                .managerId(managerId)
                .invoiceId(invoice.getId())
                .purpose(passcode.getPurpose())
                .unlockedByAdmin(passcode.getCreatedBy())
                .passcodeId(passcode.getId())
                .success(true)
                .paymentResult("UNLOCKED")
                .createdAt(now)
                .build());

        return InvoiceUnlockVerifyResponse.builder()
                .valid(true)
                .unlockToken(token)
                .expiresAt(tokenExpires)
                .message("OK")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceUnlockLogResponse> listLogs() {
        Map<UUID, String> names = userRepository.findAll().stream()
                .collect(Collectors.toMap(User::getId,
                        u -> u.getFullName() != null ? u.getFullName() : "", (a, b) -> a));
        Map<Long, String> invoiceCodes = tenantInvoiceRepository.findAll().stream()
                .collect(Collectors.toMap(TenantInvoice::getId, TenantInvoice::getCode, (a, b) -> a));

        return logRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(l -> InvoiceUnlockLogResponse.builder()
                        .id(l.getId())
                        .managerId(l.getManagerId())
                        .managerName(names.getOrDefault(l.getManagerId(), ""))
                        .invoiceId(l.getInvoiceId())
                        .invoiceCode(invoiceCodes.getOrDefault(l.getInvoiceId(), ""))
                        .purpose(l.getPurpose())
                        .unlockedByAdmin(l.getUnlockedByAdmin())
                        .adminName(names.getOrDefault(l.getUnlockedByAdmin(), ""))
                        .passcodeId(l.getPasscodeId())
                        .success(l.isSuccess())
                        .paymentResult(l.getPaymentResult())
                        .createdAt(l.getCreatedAt())
                        .build())
                .toList();
    }

    private void assertManagerCanAccessInvoice(UUID managerId, TenantInvoice invoice) {
        if (invoice.getTenantContract() == null || invoice.getTenantContract().getProperty() == null) {
            throw new BusinessException("Hoá đơn không gắn tòa nhà");
        }
        UUID opManagerId = invoice.getTenantContract().getProperty().getOperationManagerId();
        if (opManagerId == null || !managerId.equals(opManagerId)) {
            throw new AccessDeniedException("Bạn không phụ trách tòa nhà của hoá đơn này");
        }
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

    private static InvoiceUnlockPasscodeResponse toPasscodeResponse(InvoiceUnlockPasscode p, LocalDateTime now) {
        boolean usable = p.isUsable(now);
        return InvoiceUnlockPasscodeResponse.builder()
                .id(p.getId())
                .passcode(p.getCode())
                .invoiceId(p.getInvoiceId())
                .purpose(p.getPurpose())
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
}
