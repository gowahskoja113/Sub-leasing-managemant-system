package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.IssueInvoiceRequest;
import com.sep490.slms2026.dto.response.TenantInvoiceResponse;
import com.sep490.slms2026.dto.response.TenantPendingChargeResponse;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.entity.TenantInvoice;
import com.sep490.slms2026.entity.TenantPendingCharge;
import com.sep490.slms2026.enums.TenantInvoiceStatus;
import com.sep490.slms2026.enums.TenantInvoiceType;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.TenantContractRepository;
import com.sep490.slms2026.repository.TenantInvoiceRepository;
import com.sep490.slms2026.repository.TenantPendingChargeRepository;
import com.sep490.slms2026.service.PayosService;
import com.sep490.slms2026.service.TenantPendingChargeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantPendingChargeServiceImpl implements TenantPendingChargeService {

    private final TenantPendingChargeRepository pendingChargeRepository;
    private final TenantInvoiceRepository tenantInvoiceRepository;
    private final TenantContractRepository tenantContractRepository;
    private final PayosService payosService;

    @Override
    public List<TenantPendingChargeResponse> getPendingChargesForManager(UUID managerId, boolean isAdmin, Long propertyId, String status) {
        return pendingChargeRepository.findForManager(managerId, isAdmin, propertyId, status).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<TenantPendingChargeResponse> getPendingChargesForTenant(UUID tenantId) {
        return pendingChargeRepository.findByTenantUserId(tenantId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public TenantInvoiceResponse issueInvoiceFromCharges(Long contractId, IssueInvoiceRequest request) {
        TenantContract contract = tenantContractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant contract not found"));

        if (request.getChargeIds() == null || request.getChargeIds().isEmpty()) {
            throw new BusinessException("Charge list is empty");
        }

        List<TenantPendingCharge> charges = pendingChargeRepository.findAllById(request.getChargeIds());

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (TenantPendingCharge charge : charges) {
            if (!charge.getTenantContract().getId().equals(contractId)) {
                throw new BusinessException("Charge " + charge.getId() + " does not belong to the given contract");
            }
            if (!"PENDING".equalsIgnoreCase(charge.getStatus())) {
                throw new BusinessException("Charge " + charge.getId() + " is already invoiced or processed. Current status: " + charge.getStatus());
            }
            totalAmount = totalAmount.add(charge.getAmount());
        }

        YearMonth ym = YearMonth.now();
        LocalDate dueDate = request.getDueDate() != null ? request.getDueDate() : ym.atEndOfMonth();
        
        TenantInvoice invoice = TenantInvoice.builder()
                .code("HD-MAINT-" + contract.getId() + "-" + System.currentTimeMillis())
                .tenantUserId(contract.getTenant().getUser().getId())
                .tenantContract(contract)
                .invoiceType(TenantInvoiceType.MAINTENANCE)
                .propertyName(contract.getProperty().getPropertyName())
                .roomNumber(contract.getRoom() != null ? contract.getRoom().getRoomNumber() : null)
                .billingMonth(ym.getMonthValue())
                .billingYear(ym.getYear())
                .billingPeriod("Phí bảo trì")
                .note(request.getNote())
                .totalAmount(totalAmount)
                .lateFee(BigDecimal.ZERO)
                .grandTotal(totalAmount)
                .status(TenantInvoiceStatus.PENDING)
                .dueDate(dueDate)
                .createdAt(LocalDateTime.now())
                .build();

        TenantInvoice savedInvoice = tenantInvoiceRepository.save(invoice);

        for (TenantPendingCharge charge : charges) {
            charge.setStatus("INVOICED");
            charge.setInvoice(savedInvoice);
        }
        pendingChargeRepository.saveAll(charges);

        return toInvoiceResponse(savedInvoice);
    }

    @Override
    @Transactional
    public TenantInvoiceResponse createAndIssueMaintenanceCharge(
            TenantContract contract,
            BigDecimal amount,
            Long maintenanceRequestId,
            String note) {
        if (contract == null) {
            throw new BusinessException("Không tìm thấy hợp đồng để tạo khoản bồi thường");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Số tiền bồi thường phải lớn hơn 0");
        }

        TenantPendingCharge charge = TenantPendingCharge.builder()
                .tenantContract(contract)
                .amount(amount)
                .category("MAINTENANCE")
                .note(note)
                .maintenanceRequestId(maintenanceRequestId)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();
        charge = pendingChargeRepository.save(charge);

        IssueInvoiceRequest issueRequest = new IssueInvoiceRequest();
        issueRequest.setChargeIds(List.of(charge.getId()));
        issueRequest.setNote(note);
        issueRequest.setDueDate(LocalDate.now().plusDays(7));

        TenantInvoiceResponse invoiceResponse = issueInvoiceFromCharges(contract.getId(), issueRequest);

        TenantInvoice invoice = tenantInvoiceRepository.findById(invoiceResponse.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found after issue"));
        if (payosService.isConfigured()) {
            try {
                long orderCode = System.currentTimeMillis();
                PayosService.PaymentLink link = payosService.createPaymentLink(
                        orderCode, invoice.getGrandTotal().longValue(), invoice.getCode());
                invoice.setPayosOrderCode(orderCode);
                invoice.setPayosCheckoutUrl(link.checkoutUrl);
                invoice.setPayosQrCode(link.qrCode);
                invoice = tenantInvoiceRepository.save(invoice);
            } catch (Exception e) {
                log.warn("PayOS link failed for maintenance invoice {}: {}", invoice.getCode(), e.getMessage());
            }
        }
        return toInvoiceResponse(invoice);
    }

    private TenantPendingChargeResponse toResponse(TenantPendingCharge charge) {
        return TenantPendingChargeResponse.builder()
                .id(charge.getId())
                .tenantContractId(charge.getTenantContract().getId())
                .invoiceId(charge.getInvoice() != null ? charge.getInvoice().getId() : null)
                .amount(charge.getAmount())
                .category(charge.getCategory())
                .note(charge.getNote())
                .maintenanceRequestId(charge.getMaintenanceRequestId())
                .status(charge.getStatus())
                .createdAt(charge.getCreatedAt())
                .build();
    }
    
    private TenantInvoiceResponse toInvoiceResponse(TenantInvoice invoice) {
        return TenantInvoiceResponse.builder()
                .id(invoice.getId())
                .code(invoice.getCode())
                .type(invoice.getInvoiceType().name())
                .propertyName(invoice.getPropertyName())
                .roomNumber(invoice.getRoomNumber())
                .month(invoice.getBillingMonth())
                .year(invoice.getBillingYear())
                .billingPeriod(invoice.getBillingPeriod())
                .totalAmount(invoice.getTotalAmount())
                .lateFee(invoice.getLateFee())
                .grandTotal(invoice.getGrandTotal())
                .status(invoice.getStatus().name())
                .dueDate(invoice.getDueDate())
                .createdAt(invoice.getCreatedAt())
                .paidAt(invoice.getPaidAt())
                .payosCheckoutUrl(invoice.getPayosCheckoutUrl())
                .payosQrCode(invoice.getPayosQrCode())
                .payosOrderCode(invoice.getPayosOrderCode())
                .build();
    }
}
