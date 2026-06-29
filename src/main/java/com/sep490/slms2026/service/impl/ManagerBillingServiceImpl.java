package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.response.ManagerInvoiceResponse;
import com.sep490.slms2026.dto.response.ManagerPaymentResponse;
import com.sep490.slms2026.dto.response.RentInvoiceSummaryResponse;
import com.sep490.slms2026.entity.TenantInvoice;
import com.sep490.slms2026.entity.TenantPaymentClaim;
import com.sep490.slms2026.enums.PaymentClaimStatus;
import com.sep490.slms2026.enums.TenantInvoiceStatus;
import com.sep490.slms2026.enums.TenantInvoiceType;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.TenantInvoiceRepository;
import com.sep490.slms2026.repository.TenantPaymentClaimRepository;
import com.sep490.slms2026.service.ManagerBillingService;
import com.sep490.slms2026.service.PropertyAccessService;
import com.sep490.slms2026.service.TenantBillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ManagerBillingServiceImpl implements ManagerBillingService {

    private final TenantInvoiceRepository tenantInvoiceRepository;
    private final TenantPaymentClaimRepository tenantPaymentClaimRepository;
    private final PropertyAccessService propertyAccessService;
    private final TenantBillingService tenantBillingService;

    @Override
    @Transactional(readOnly = true)
    public List<RentInvoiceSummaryResponse> listRentInvoices(Long propertyId, String month) {
        propertyAccessService.assertCanManageProperty(propertyId);
        Integer year = null;
        Integer monthValue = null;
        if (month != null && !month.isBlank()) {
            YearMonth ym = YearMonth.parse(month.trim());
            year = ym.getYear();
            monthValue = ym.getMonthValue();
        }

        return tenantInvoiceRepository.findRentInvoicesByPropertyAndMonth(propertyId, year, monthValue)
                .stream()
                .map(this::toRentSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ManagerInvoiceResponse> listInvoices(UUID managerUserId, boolean isAdmin,
                                                     String period, String status, String type) {
        UUID managerFilter = isAdmin ? null : managerUserId;
        YearMonth ym = parsePeriod(period);

        return tenantInvoiceRepository.findForManager(
                        managerFilter,
                        parseInvoiceStatus(status),
                        parseInvoiceType(type),
                        ym != null ? ym.getYear() : null,
                        ym != null ? ym.getMonthValue() : null)
                .stream()
                .map(this::toManagerInvoice)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ManagerPaymentResponse> listPayments(UUID managerUserId, boolean isAdmin, String status) {
        UUID managerFilter = isAdmin ? null : managerUserId;
        PaymentClaimStatus claimStatus = parseClaimStatus(status);

        return tenantPaymentClaimRepository.findForManager(managerFilter, claimStatus)
                .stream()
                .map(this::toManagerPayment)
                .toList();
    }

    @Override
    @Transactional
    public ManagerPaymentResponse verifyPayment(UUID managerUserId, boolean isAdmin, Long claimId) {
        TenantPaymentClaim claim = loadClaimForManager(claimId, managerUserId, isAdmin);
        if (claim.getStatus() != PaymentClaimStatus.PENDING_VERIFY) {
            throw new BusinessException("Chỉ xác nhận thanh toán đang chờ duyệt");
        }
        tenantBillingService.approvePaymentClaim(claim, managerUserId);
        return toManagerPayment(tenantPaymentClaimRepository.findById(claimId).orElse(claim));
    }

    @Override
    @Transactional
    public ManagerPaymentResponse rejectPayment(UUID managerUserId, boolean isAdmin,
                                              Long claimId, String reason) {
        TenantPaymentClaim claim = loadClaimForManager(claimId, managerUserId, isAdmin);
        if (claim.getStatus() != PaymentClaimStatus.PENDING_VERIFY) {
            throw new BusinessException("Chỉ từ chối thanh toán đang chờ duyệt");
        }
        claim.setStatus(PaymentClaimStatus.REJECTED);
        claim.setRejectReason(reason);
        claim.setVerifiedAt(java.time.LocalDateTime.now());
        claim.setVerifiedBy(managerUserId);
        return toManagerPayment(tenantPaymentClaimRepository.save(claim));
    }

    private TenantPaymentClaim loadClaimForManager(Long claimId, UUID managerUserId, boolean isAdmin) {
        TenantPaymentClaim claim = tenantPaymentClaimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy giao dịch ID=" + claimId));
        if (!isAdmin) {
            UUID opManagerId = claim.getTenantInvoice().getTenantContract().getProperty().getOperationManagerId();
            if (!managerUserId.equals(opManagerId)) {
                throw new BusinessException("Bạn không có quyền xử lý giao dịch này");
            }
        }
        return claim;
    }

    private RentInvoiceSummaryResponse toRentSummary(TenantInvoice invoice) {
        String billingMonth = invoice.getBillingYear() != null && invoice.getBillingMonth() != null
                ? String.format("%04d-%02d", invoice.getBillingYear(), invoice.getBillingMonth())
                : null;
        return RentInvoiceSummaryResponse.builder()
                .id(invoice.getId())
                .contractId(invoice.getTenantContract().getId())
                .roomNumber(invoice.getRoomNumber())
                .billingMonth(billingMonth)
                .amount(invoice.getGrandTotal())
                .status(invoice.getStatus().name())
                .build();
    }

    private ManagerInvoiceResponse toManagerInvoice(TenantInvoice invoice) {
        String tenantName = null;
        if (invoice.getTenantContract().getTenant() != null
                && invoice.getTenantContract().getTenant().getUser() != null) {
            tenantName = invoice.getTenantContract().getTenant().getUser().getFullName();
        }
        return ManagerInvoiceResponse.builder()
                .id(invoice.getId())
                .code(invoice.getCode())
                .type(invoice.getInvoiceType().name())
                .propertyId(invoice.getTenantContract().getProperty().getId())
                .propertyName(invoice.getPropertyName())
                .roomNumber(invoice.getRoomNumber())
                .tenantName(tenantName)
                .month(invoice.getBillingMonth())
                .year(invoice.getBillingYear())
                .amount(invoice.getGrandTotal())
                .status(invoice.getStatus().name())
                .dueDate(invoice.getDueDate())
                .createdAt(invoice.getCreatedAt())
                .build();
    }

    private ManagerPaymentResponse toManagerPayment(TenantPaymentClaim claim) {
        TenantInvoice invoice = claim.getTenantInvoice();
        String tenantName = null;
        var contract = invoice.getTenantContract();
        if (contract.getTenant() != null && contract.getTenant().getUser() != null) {
            tenantName = contract.getTenant().getUser().getFullName();
        }
        return ManagerPaymentResponse.builder()
                .id(claim.getId())
                .invoiceCode(invoice.getCode())
                .tenantName(tenantName)
                .roomNumber(invoice.getRoomNumber())
                .propertyName(invoice.getPropertyName())
                .amount(claim.getAmount())
                .method(claim.getMethod())
                .status(claim.getStatus().name())
                .transferContent(claim.getTransferContent())
                .createdAt(claim.getCreatedAt())
                .verifiedAt(claim.getVerifiedAt())
                .build();
    }

    private YearMonth parsePeriod(String period) {
        if (period == null || period.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(period.trim());
        } catch (Exception e) {
            throw new BusinessException("period không hợp lệ — dùng định dạng yyyy-MM");
        }
    }

    private TenantInvoiceStatus parseInvoiceStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return TenantInvoiceStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("status không hợp lệ: " + status);
        }
    }

    private TenantInvoiceType parseInvoiceType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        if ("ELECTRIC".equalsIgnoreCase(type)) {
            return TenantInvoiceType.ELECTRICITY;
        }
        try {
            return TenantInvoiceType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("type không hợp lệ: " + type);
        }
    }

    private PaymentClaimStatus parseClaimStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return PaymentClaimStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("status không hợp lệ: " + status);
        }
    }
}
