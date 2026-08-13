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
import com.sep490.slms2026.util.InvoiceItemBuilder;
import com.sep490.slms2026.util.PaymentBreakdownBuilder;
import com.sep490.slms2026.util.PaymentMethods;
import com.sep490.slms2026.dto.response.PaymentBreakdownResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import com.sep490.slms2026.dto.response.ManagerDepositDto;
import com.sep490.slms2026.repository.TenantContractRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
public class ManagerBillingServiceImpl implements ManagerBillingService {

    private final TenantInvoiceRepository tenantInvoiceRepository;
    private final TenantPaymentClaimRepository tenantPaymentClaimRepository;
    private final PropertyAccessService propertyAccessService;
    private final TenantBillingService tenantBillingService;
    private final TenantContractRepository tenantContractRepository;

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
                .map(inv -> toManagerInvoice(inv, false, isAdmin))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ManagerInvoiceResponse getInvoice(UUID managerUserId, boolean isAdmin, Long invoiceId) {
        TenantInvoice invoice = tenantInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hoá đơn ID=" + invoiceId));
        if (!isAdmin) {
            UUID opManagerId = invoice.getTenantContract().getProperty().getOperationManagerId();
            if (opManagerId == null || !managerUserId.equals(opManagerId)) {
                throw new AccessDeniedException("Bạn không có quyền xem hoá đơn này");
            }
        }
        return toManagerInvoice(invoice, true, isAdmin);
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

    @Override
    @Transactional(readOnly = true)
    public Page<ManagerDepositDto> getManagerDeposits(UUID managerUserId, String status, Pageable pageable) {
        com.sep490.slms2026.enums.PaymentStatus paymentStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                paymentStatus = com.sep490.slms2026.enums.PaymentStatus.valueOf(status.toUpperCase());
            } catch (Exception e) {}
        }

        return tenantContractRepository.findManagerDeposits(managerUserId, paymentStatus, pageable)
                .map(contract -> {
                    String tenantPhone = null;
                    if (contract.getTenant() != null && contract.getTenant().getUser() != null) {
                        tenantPhone = contract.getTenant().getUser().getPhoneNumber();
                    }
                    return ManagerDepositDto.builder()
                            .contractId(contract.getId())
                            .contractCode(contract.getContractCode())
                            .propertyName(contract.getProperty() != null ? contract.getProperty().getPropertyName() : null)
                            .roomNumber(contract.getRoom() != null ? contract.getRoom().getRoomNumber() : null)
                            .tenantName(contract.getTenant() != null && contract.getTenant().getUser() != null ? contract.getTenant().getUser().getFullName() : null)
                            .tenantPhone(tenantPhone)
                            .depositMonths(contract.getDepositMonths())
                            .deposit(contract.getDeposit())
                            .paymentStatus(contract.getPaymentStatus() != null ? contract.getPaymentStatus().name() : null)
                            .depositMethod(contract.getPayosOrderCode() != null ? "PAYOS" : (contract.getDepositCashManagerConfirmedAt() != null || contract.getDepositCashTenantConfirmedAt() != null ? "CASH" : null))
                            .depositPaidAt(contract.getPaidAt() != null ? contract.getPaidAt() : contract.getDepositCashManagerConfirmedAt())
                            .contractStatus(contract.getStatus() != null ? contract.getStatus().name() : null)
                            .moveInDate(contract.getMoveInDate())
                            .build();
                });
    }

    private TenantPaymentClaim loadClaimForManager(Long claimId, UUID managerUserId, boolean isAdmin) {
        TenantPaymentClaim claim = tenantPaymentClaimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy giao dịch ID=" + claimId));
        if (!isAdmin) {
            UUID opManagerId = claim.getTenantInvoice().getTenantContract().getProperty().getOperationManagerId();
            if (!managerUserId.equals(opManagerId)) {
                throw new AccessDeniedException("Bạn không có quyền xử lý giao dịch này");
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
                .autoIssued(invoice.getAutoIssued())
                .dueDate(invoice.getDueDate())
                .build();
    }

    private ManagerInvoiceResponse toManagerInvoice(TenantInvoice invoice, boolean includeItems, boolean isAdmin) {
        String tenantName = null;
        if (invoice.getTenantContract().getTenant() != null
                && invoice.getTenantContract().getTenant().getUser() != null) {
            tenantName = invoice.getTenantContract().getTenant().getUser().getFullName();
        } else if (invoice.getTenantContract().getDraftTenantName() != null) {
            tenantName = invoice.getTenantContract().getDraftTenantName();
        }
        var contract = invoice.getTenantContract();
        ManagerInvoiceResponse response = ManagerInvoiceResponse.builder()
                .id(invoice.getId())
                .code(invoice.getCode())
                .type(invoice.getInvoiceType().name())
                .propertyId(contract.getProperty().getId())
                .propertyName(invoice.getPropertyName())
                .roomNumber(invoice.getRoomNumber())
                .tenantName(tenantName)
                .contractId(contract.getId())
                .contractStatus(contract.getStatus() != null ? contract.getStatus().name() : null)
                .month(invoice.getBillingMonth())
                .year(invoice.getBillingYear())
                .billingPeriod(invoice.getBillingPeriod())
                .amount(invoice.getGrandTotal())
                .totalAmount(invoice.getTotalAmount())
                .lateFee(invoice.getLateFee())
                .cycleType(invoice.getCycleType() != null ? invoice.getCycleType().name() : null)
                .status(invoice.getStatus().name())
                .dueDate(invoice.getDueDate())
                .createdAt(invoice.getCreatedAt())
                .paidAt(invoice.getPaidAt())
                .paymentMethod(PaymentMethods.toPublic(invoice.getPaymentMethod()))
                .transactionId(invoice.getTransactionId())
                .payosQrCode(invoice.getPayosQrCode())
                .payosCheckoutUrl(invoice.getPayosCheckoutUrl())
                .onboardPaid(invoice.getNote() != null
                        && (invoice.getNote().contains("onboardPaid=true") || invoice.getNote().startsWith("ONBOARD|")))
                .build();

        if (includeItems) {
            response.setItems(InvoiceItemBuilder.buildItems(invoice));
            PaymentBreakdownResponse breakdown = PaymentBreakdownBuilder.fromInvoice(invoice);
            response.setPaymentBreakdown(breakdown);
        }

        // Manager không xem số tiền hóa đơn thuê phòng; admin vẫn xem đầy đủ.
        if (!isAdmin && invoice.getInvoiceType() == TenantInvoiceType.RENT) {
            response.setAmount(null);
            response.setTotalAmount(null);
            response.setLateFee(null);
            if (response.getPaymentBreakdown() != null) {
                maskMoneyOnBreakdown(response.getPaymentBreakdown());
            }
            if (response.getItems() != null) {
                response.getItems().forEach(i -> i.setAmount(null));
            }
        }

        if (!isAdmin) {
            stripDepositFromManagerResponse(response, invoice);
        }

        return response;
    }

    /** Manager không được đọc tiền cọc qua invoice — chỉ còn {@code /manager/deposits}. */
    private static void stripDepositFromManagerResponse(ManagerInvoiceResponse response, TenantInvoice invoice) {
        BigDecimal deposit = null;
        if (response.getPaymentBreakdown() != null) {
            PaymentBreakdownResponse b = response.getPaymentBreakdown();
            deposit = b.getDepositAmount();
            b.setDepositAmount(null);
            b.setDepositMonths(null);
            if (b.getLines() != null) {
                b.setLines(b.getLines().stream()
                        .filter(line -> !"depositAmount".equals(line.getKey())
                                && !"depositMonths".equals(line.getKey()))
                        .toList());
            }
            if (deposit != null) {
                b.setFormula(null);
                b.setTotalAmount(subtractOrNull(b.getTotalAmount(), deposit));
            }
        }
        if (deposit == null) {
            deposit = parseOnboardDeposit(invoice.getNote());
        }
        if (deposit != null) {
            response.setAmount(subtractOrNull(response.getAmount(), deposit));
            response.setTotalAmount(subtractOrNull(response.getTotalAmount(), deposit));
        }
        if (response.getItems() != null) {
            response.setItems(response.getItems().stream()
                    .filter(item -> item.getLabel() == null || !item.getLabel().contains("Tiền cọc"))
                    .toList());
        }
    }

    private static BigDecimal parseOnboardDeposit(String note) {
        if (note == null || !note.startsWith("ONBOARD|")) {
            return null;
        }
        for (String part : note.split("\\|")) {
            if (part.startsWith("depositAmount=")) {
                try {
                    return new BigDecimal(part.substring("depositAmount=".length()).trim());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private static BigDecimal subtractOrNull(BigDecimal value, BigDecimal deposit) {
        if (value == null) {
            return null;
        }
        BigDecimal remaining = value.subtract(deposit);
        return remaining.compareTo(BigDecimal.ZERO) <= 0 ? null : remaining;
    }

    private static void maskMoneyOnBreakdown(PaymentBreakdownResponse b) {
        b.setTotalAmount(null);
        b.setRentAmountMonthly(null);
        b.setDepositAmount(null);
        b.setDailyRate(null);
        b.setFormula(null);
        if (b.getLines() != null) {
            b.getLines().forEach(line -> {
                line.setAmount(null);
                if (line.getUnit() != null && "VND".equalsIgnoreCase(line.getUnit())) {
                    line.setDisplayValue("***");
                }
            });
        }
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
                .invoiceType(invoice.getInvoiceType() != null ? invoice.getInvoiceType().name() : null)
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
