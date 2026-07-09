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

    private TenantPendingChargeResponse toResponse(TenantPendingCharge charge) {
        return TenantPendingChargeResponse.builder()
                .id(charge.getId())
                .tenantContractId(charge.getTenantContract().getId())
                .invoiceId(charge.getInvoice() != null ? charge.getInvoice().getId() : null)
                .amount(charge.getAmount())
                .category(charge.getCategory())
                .note(charge.getNote())
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
                .build();
    }
}
