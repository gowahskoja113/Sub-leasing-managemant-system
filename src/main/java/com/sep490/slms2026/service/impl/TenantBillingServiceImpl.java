package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.CreateRentInvoiceRequest;
import com.sep490.slms2026.dto.response.TenantInvoiceItemResponse;
import com.sep490.slms2026.dto.response.TenantInvoiceResponse;
import com.sep490.slms2026.dto.response.TenantPaymentResponse;
import com.sep490.slms2026.entity.*;
import com.sep490.slms2026.enums.*;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.*;
import com.sep490.slms2026.service.PayosService;
import com.sep490.slms2026.service.PropertyAccessService;
import com.sep490.slms2026.service.TenantBillingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantBillingServiceImpl implements TenantBillingService {

    private final TenantInvoiceRepository tenantInvoiceRepository;
    private final TenantPaymentRepository tenantPaymentRepository;
    private final TenantContractRepository tenantContractRepository;
    private final UtilityInvoiceRepository utilityInvoiceRepository;
    private final PayosService payosService;
    private final PropertyAccessService propertyAccessService;
    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final TenantPaymentClaimRepository tenantPaymentClaimRepository;

    @Override
    @Transactional
    public List<TenantInvoiceResponse> listInvoices(UUID tenantUserId, String status, String type) {
        syncInvoicesForTenant(tenantUserId);
        refreshOverdueStatuses(tenantUserId);

        TenantInvoiceStatus statusFilter = parseStatus(status);
        TenantInvoiceType typeFilter = parseType(type);

        return tenantInvoiceRepository.findForTenant(tenantUserId, statusFilter, typeFilter)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TenantInvoiceResponse getInvoice(UUID tenantUserId, Long invoiceId) {
        return toResponse(loadOwnedInvoice(invoiceId, tenantUserId));
    }

    @Override
    @Transactional
    public TenantInvoiceResponse createPayment(UUID tenantUserId, Long invoiceId) {
        TenantInvoice invoice = loadOwnedInvoice(invoiceId, tenantUserId);
        if (invoice.getStatus() == TenantInvoiceStatus.PAID) {
            throw new BusinessException("Hóa đơn đã được thanh toán");
        }
        if (invoice.getStatus() == TenantInvoiceStatus.CANCELLED) {
            throw new BusinessException("Hóa đơn đã bị hủy");
        }

        long orderCode = System.currentTimeMillis();
        long amount = invoice.getGrandTotal().longValue();
        PayosService.PaymentLink link = payosService.createPaymentLink(
                orderCode, amount, invoice.getCode());

        invoice.setPayosOrderCode(orderCode);
        invoice.setPayosCheckoutUrl(link.checkoutUrl);
        invoice.setPayosQrCode(link.qrCode);
        return toResponse(tenantInvoiceRepository.save(invoice));
    }

    @Override
    @Transactional
    public TenantInvoiceResponse checkPayment(UUID tenantUserId, Long invoiceId) {
        TenantInvoice invoice = loadOwnedInvoice(invoiceId, tenantUserId);
        if (invoice.getStatus() == TenantInvoiceStatus.PAID) {
            return toResponse(invoice);
        }
        if (invoice.getPayosOrderCode() != null) {
            String payosStatus = payosService.getPaymentStatus(invoice.getPayosOrderCode());
            if ("PAID".equalsIgnoreCase(payosStatus)) {
                markPaid(invoice, "QR", "VQR-" + invoice.getPayosOrderCode());
                invoice = tenantInvoiceRepository.save(invoice);
            } else {
                createBankTransferClaim(invoice, null);
            }
        } else {
            createBankTransferClaim(invoice, null);
        }
        return toResponse(invoice);
    }

    @Override
    @Transactional
    public void approvePaymentClaim(TenantPaymentClaim claim, UUID verifiedBy) {
        TenantInvoice invoice = claim.getTenantInvoice();
        claim.setStatus(PaymentClaimStatus.VERIFIED);
        claim.setVerifiedAt(LocalDateTime.now());
        claim.setVerifiedBy(verifiedBy);
        tenantPaymentClaimRepository.save(claim);
        markPaid(invoice, claim.getMethod(), "CLAIM-" + claim.getId());
        tenantInvoiceRepository.save(invoice);
    }

    @Override
    @Transactional
    public void createBankTransferClaim(TenantInvoice invoice, String transferContent) {
        if (invoice.getStatus() == TenantInvoiceStatus.PAID) {
            return;
        }
        var existing = tenantPaymentClaimRepository.findByTenantInvoiceIdAndStatus(
                invoice.getId(), PaymentClaimStatus.PENDING_VERIFY);
        if (existing.isPresent()) {
            TenantPaymentClaim claim = existing.get();
            if (transferContent != null && !transferContent.isBlank()) {
                claim.setTransferContent(transferContent);
                tenantPaymentClaimRepository.save(claim);
            }
            return;
        }
        tenantPaymentClaimRepository.save(TenantPaymentClaim.builder()
                .tenantInvoice(invoice)
                .tenantUserId(invoice.getTenantUserId())
                .amount(invoice.getGrandTotal())
                .method("BANK_TRANSFER")
                .transferContent(transferContent)
                .status(PaymentClaimStatus.PENDING_VERIFY)
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TenantPaymentResponse> listPayments(UUID tenantUserId) {
        return tenantPaymentRepository.findByTenantUserIdOrderByPaidAtDesc(tenantUserId)
                .stream()
                .map(this::toPaymentResponse)
                .toList();
    }

    @Override
    @Transactional
    public void markInvoicePaidByPayosOrderCode(Long payosOrderCode) {
        tenantInvoiceRepository.findByPayosOrderCode(payosOrderCode).ifPresent(invoice -> {
            if (invoice.getStatus() != TenantInvoiceStatus.PAID) {
                markPaid(invoice, "QR", "VQR-" + payosOrderCode);
                tenantInvoiceRepository.save(invoice);
                log.info("Đã ghi nhận thanh toán hóa đơn tenant orderCode={}", payosOrderCode);
            }
        });
    }

    @Override
    @Transactional
    public TenantInvoice createFromUtilityInvoice(UtilityInvoice utilityInvoice, TenantContract contract) {
        if (contract == null || contract.getTenant() == null) {
            return null;
        }
        if (tenantInvoiceRepository.findByUtilityInvoiceId(utilityInvoice.getId()).isPresent()) {
            return tenantInvoiceRepository.findByUtilityInvoiceId(utilityInvoice.getId()).orElse(null);
        }

        TenantInvoiceType type = utilityInvoice.getUtilityType() == UtilityType.ELECTRIC
                ? TenantInvoiceType.ELECTRICITY
                : TenantInvoiceType.WATER;
        YearMonth ym = YearMonth.from(utilityInvoice.getCreatedAt());
        LocalDate dueDate = ym.atEndOfMonth().plusDays(20);

        TenantInvoice invoice = TenantInvoice.builder()
                .code(buildCode(type, utilityInvoice.getId()))
                .tenantUserId(contract.getTenant().getId())
                .tenantContract(contract)
                .utilityInvoiceId(utilityInvoice.getId())
                .invoiceType(type)
                .propertyName(utilityInvoice.getProperty().getPropertyName())
                .roomNumber(utilityInvoice.getRoom() != null ? utilityInvoice.getRoom().getRoomNumber() : null)
                .billingMonth(ym.getMonthValue())
                .billingYear(ym.getYear())
                .billingPeriod(utilityInvoice.getBillingPeriod())
                .totalAmount(utilityInvoice.getAmount())
                .lateFee(BigDecimal.ZERO)
                .grandTotal(utilityInvoice.getAmount())
                .status(TenantInvoiceStatus.PENDING)
                .dueDate(dueDate)
                .createdAt(utilityInvoice.getCreatedAt())
                .kwhUsed(type == TenantInvoiceType.ELECTRICITY ? utilityInvoice.getConsumption() : null)
                .electricityRate(type == TenantInvoiceType.ELECTRICITY ? utilityInvoice.getUnitPrice() : null)
                .m3Used(type == TenantInvoiceType.WATER ? utilityInvoice.getConsumption() : null)
                .waterRate(type == TenantInvoiceType.WATER ? utilityInvoice.getUnitPrice() : null)
                .build();

        return tenantInvoiceRepository.save(invoice);
    }

    @Override
    @Transactional
    public TenantInvoiceResponse createManagerRentInvoice(
            Long propertyId, Long roomId, CreateRentInvoiceRequest request) {
        propertyAccessService.assertCanManageProperty(propertyId);
        loadProperty(propertyId);

        YearMonth billingMonth = parseBillingMonth(request.getBillingMonth());
        TenantContract contract = loadAndValidateContract(propertyId, roomId, request.getContractId());

        var existing = tenantInvoiceRepository.findByTenantContractIdAndInvoiceTypeAndBillingYearAndBillingMonth(
                contract.getId(), TenantInvoiceType.RENT, billingMonth.getYear(), billingMonth.getMonthValue());
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        if (contract.getRentAmount() != null
                && contract.getRentAmount().compareTo(request.getAmount()) != 0) {
            throw new BusinessException(
                    "Số tiền không khớp với giá thuê trong hợp đồng (" + contract.getRentAmount() + ")");
        }

        Property property = contract.getProperty();
        Room room = contract.getRoom();
        UUID tenantUserId = contract.getTenant().getId();
        LocalDateTime now = LocalDateTime.now();

        TenantInvoice invoice = tenantInvoiceRepository.save(TenantInvoice.builder()
                .code("HD-RENT-" + contract.getId() + "-" + billingMonth)
                .tenantUserId(tenantUserId)
                .tenantContract(contract)
                .invoiceType(TenantInvoiceType.RENT)
                .propertyName(property.getPropertyName())
                .roomNumber(room != null ? room.getRoomNumber() : property.getPropertyName())
                .billingMonth(billingMonth.getMonthValue())
                .billingYear(billingMonth.getYear())
                .billingPeriod("Tiền nhà tháng " + billingMonth.getMonthValue() + "/" + billingMonth.getYear())
                .note(request.getNote())
                .totalAmount(request.getAmount())
                .lateFee(BigDecimal.ZERO)
                .grandTotal(request.getAmount())
                .status(TenantInvoiceStatus.PENDING)
                .dueDate(request.getDueDate())
                .createdAt(now)
                .build());

        return toResponse(invoice);
    }

    private void syncInvoicesForTenant(UUID tenantUserId) {
        for (UtilityInvoice utilityInvoice : utilityInvoiceRepository.findByTenantUserId(tenantUserId)) {
            TenantContract contract = utilityInvoice.getTenantContract();
            if (contract != null) {
                createFromUtilityInvoice(utilityInvoice, contract);
            }
        }

        List<TenantContract> contracts = tenantContractRepository.findByTenantId(tenantUserId);
        for (TenantContract contract : contracts) {
            if (contract.getStatus() != ContractStatus.ACTIVE) {
                continue;
            }
            ensureServiceInvoice(contract, tenantUserId);
        }
    }

    private TenantContract loadAndValidateContract(Long propertyId, Long roomId, Long contractId) {
        TenantContract contract = tenantContractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy hợp đồng ID=" + contractId));

        if (!contract.getProperty().getId().equals(propertyId)) {
            throw new BusinessException("Hợp đồng không thuộc tòa nhà này");
        }
        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw new BusinessException("Chỉ gửi hóa đơn tiền nhà cho hợp đồng đang hiệu lực");
        }
        if (roomId != null) {
            if (contract.getRoom() == null || !contract.getRoom().getId().equals(roomId)) {
                throw new BusinessException("Hợp đồng không thuộc phòng này");
            }
            loadRoom(propertyId, roomId);
        } else if (contract.getRoom() != null) {
            throw new BusinessException("API nguyên căn chỉ dùng cho hợp đồng không gắn phòng");
        }
        return contract;
    }

    private YearMonth parseBillingMonth(String billingMonth) {
        try {
            return YearMonth.parse(billingMonth.trim());
        } catch (Exception e) {
            throw new BusinessException("billingMonth không hợp lệ — dùng định dạng yyyy-MM");
        }
    }

    private Property loadProperty(Long propertyId) {
        return propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy tòa nhà với ID: " + propertyId));
    }

    private Room loadRoom(Long propertyId, Long roomId) {
        return roomRepository.findByIdAndPropertyIdAndDeletedIsFalse(roomId, propertyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy phòng ID=" + roomId + " trong tòa nhà ID=" + propertyId));
    }

    private void ensureServiceInvoice(TenantContract contract, UUID tenantUserId) {
        Property property = contract.getProperty();
        if (property.getServiceFee() == null
                || property.getServiceFee().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        YearMonth ym = YearMonth.now();
        if (tenantInvoiceRepository.existsByTenantContractIdAndInvoiceTypeAndBillingYearAndBillingMonth(
                contract.getId(), TenantInvoiceType.SERVICE, ym.getYear(), ym.getMonthValue())) {
            return;
        }

        Room room = contract.getRoom();
        tenantInvoiceRepository.save(TenantInvoice.builder()
                .code("HD-SVC-" + contract.getId() + "-" + ym)
                .tenantUserId(tenantUserId)
                .tenantContract(contract)
                .invoiceType(TenantInvoiceType.SERVICE)
                .propertyName(property.getPropertyName())
                .roomNumber(room != null ? room.getRoomNumber() : property.getPropertyName())
                .billingMonth(ym.getMonthValue())
                .billingYear(ym.getYear())
                .billingPeriod("Phí dịch vụ tháng " + ym.getMonthValue() + "/" + ym.getYear())
                .totalAmount(property.getServiceFee())
                .lateFee(BigDecimal.ZERO)
                .grandTotal(property.getServiceFee())
                .status(TenantInvoiceStatus.PENDING)
                .dueDate(ym.atEndOfMonth().plusDays(5))
                .createdAt(LocalDateTime.now())
                .build());
    }

    private void refreshOverdueStatuses(UUID tenantUserId) {
        LocalDate today = LocalDate.now();
        for (TenantInvoice invoice : tenantInvoiceRepository.findForTenant(
                tenantUserId, TenantInvoiceStatus.PENDING, null)) {
            if (invoice.getDueDate() != null && invoice.getDueDate().isBefore(today)) {
                invoice.setStatus(TenantInvoiceStatus.OVERDUE);
                tenantInvoiceRepository.save(invoice);
            }
        }
    }

    private void markPaid(TenantInvoice invoice, String method, String transactionId) {
        LocalDateTime now = LocalDateTime.now();
        invoice.setStatus(TenantInvoiceStatus.PAID);
        invoice.setPaidAt(now);
        invoice.setPaymentMethod(method);
        invoice.setTransactionId(transactionId);

        tenantPaymentRepository.save(TenantPayment.builder()
                .tenantInvoice(invoice)
                .tenantUserId(invoice.getTenantUserId())
                .invoiceCode(invoice.getCode())
                .invoiceType(invoice.getInvoiceType())
                .amount(invoice.getGrandTotal())
                .method(method)
                .paidAt(now)
                .transactionId(transactionId)
                .propertyName(invoice.getPropertyName())
                .roomNumber(invoice.getRoomNumber())
                .build());
    }

    private TenantInvoice loadOwnedInvoice(Long invoiceId, UUID tenantUserId) {
        return tenantInvoiceRepository.findByIdAndTenantUserId(invoiceId, tenantUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy hóa đơn ID=" + invoiceId));
    }

    private TenantInvoiceStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return TenantInvoiceStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Trạng thái hóa đơn không hợp lệ: " + status);
        }
    }

    private TenantInvoiceType parseType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        if ("ELECTRIC".equalsIgnoreCase(type)) {
            return TenantInvoiceType.ELECTRICITY;
        }
        try {
            return TenantInvoiceType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Loại hóa đơn không hợp lệ: " + type);
        }
    }

    private String buildCode(TenantInvoiceType type, Long sourceId) {
        return "HD-" + type.name().substring(0, Math.min(3, type.name().length())) + "-" + sourceId;
    }

    private TenantInvoiceResponse toResponse(TenantInvoice invoice) {
        return TenantInvoiceResponse.builder()
                .id(invoice.getId())
                .code(invoice.getCode())
                .type(invoice.getInvoiceType().name())
                .propertyName(invoice.getPropertyName())
                .roomNumber(invoice.getRoomNumber())
                .month(invoice.getBillingMonth())
                .year(invoice.getBillingYear())
                .billingPeriod(invoice.getBillingPeriod())
                .items(buildItems(invoice))
                .totalAmount(invoice.getTotalAmount())
                .lateFee(invoice.getLateFee())
                .grandTotal(invoice.getGrandTotal())
                .status(invoice.getStatus().name())
                .dueDate(invoice.getDueDate())
                .createdAt(invoice.getCreatedAt())
                .paidAt(invoice.getPaidAt())
                .paymentMethod(invoice.getPaymentMethod())
                .transactionId(invoice.getTransactionId())
                .kwhUsed(invoice.getKwhUsed())
                .electricityRate(invoice.getElectricityRate())
                .m3Used(invoice.getM3Used())
                .waterRate(invoice.getWaterRate())
                .payosCheckoutUrl(invoice.getPayosCheckoutUrl())
                .payosQrCode(invoice.getPayosQrCode())
                .payosOrderCode(invoice.getPayosOrderCode())
                .build();
    }

    private List<TenantInvoiceItemResponse> buildItems(TenantInvoice invoice) {
        List<TenantInvoiceItemResponse> items = new ArrayList<>();
        switch (invoice.getInvoiceType()) {
            case ELECTRICITY -> items.add(TenantInvoiceItemResponse.builder()
                    .label("Điện (" + formatQty(invoice.getKwhUsed()) + " kWh)")
                    .amount(invoice.getTotalAmount())
                    .build());
            case WATER -> items.add(TenantInvoiceItemResponse.builder()
                    .label("Nước (" + formatQty(invoice.getM3Used()) + " m³)")
                    .amount(invoice.getTotalAmount())
                    .build());
            case RENT -> items.add(TenantInvoiceItemResponse.builder()
                    .label("Tiền thuê phòng")
                    .amount(invoice.getTotalAmount())
                    .build());
            case SERVICE -> items.add(TenantInvoiceItemResponse.builder()
                    .label("Phí dịch vụ")
                    .amount(invoice.getTotalAmount())
                    .build());
            default -> items.add(TenantInvoiceItemResponse.builder()
                    .label("Khoản thu")
                    .amount(invoice.getTotalAmount())
                    .build());
        }
        if (invoice.getLateFee() != null && invoice.getLateFee().compareTo(BigDecimal.ZERO) > 0) {
            items.add(TenantInvoiceItemResponse.builder()
                    .label("Phí trễ hạn")
                    .amount(invoice.getLateFee())
                    .build());
        }
        return items;
    }

    private String formatQty(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private TenantPaymentResponse toPaymentResponse(TenantPayment payment) {
        return TenantPaymentResponse.builder()
                .id(payment.getId())
                .invoiceId(payment.getTenantInvoice().getId())
                .invoiceCode(payment.getInvoiceCode())
                .invoiceType(payment.getInvoiceType().name())
                .amount(payment.getAmount())
                .method(payment.getMethod())
                .paidAt(payment.getPaidAt())
                .transactionId(payment.getTransactionId())
                .propertyName(payment.getPropertyName())
                .roomNumber(payment.getRoomNumber())
                .build();
    }
}
