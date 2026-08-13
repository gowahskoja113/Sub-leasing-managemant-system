package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.CreateRentInvoiceRequest;
import com.sep490.slms2026.dto.response.TenantInvoiceResponse;
import com.sep490.slms2026.dto.response.TenantPaymentResponse;
import com.sep490.slms2026.entity.*;
import com.sep490.slms2026.enums.*;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.*;
import com.sep490.slms2026.service.BillingConfigService;
import com.sep490.slms2026.service.PayosService;
import com.sep490.slms2026.service.PropertyAccessService;
import com.sep490.slms2026.service.RealtimeEventService;
import com.sep490.slms2026.service.TenantBillingService;
import com.sep490.slms2026.service.UserPushTokenService;
import com.sep490.slms2026.util.ContractBillingCalendar;
import com.sep490.slms2026.util.InvoiceItemBuilder;
import com.sep490.slms2026.util.PaymentBreakdownBuilder;
import com.sep490.slms2026.util.PaymentMethods;
import com.sep490.slms2026.util.RentFirstCycleCalculator;
import com.sep490.slms2026.util.TenantContractPaymentAmounts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final RealtimeEventService realtimeEventService;
    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final TenantPaymentClaimRepository tenantPaymentClaimRepository;
    private final NotificationRepository notificationRepository;
    private final UserPushTokenService userPushTokenService;
    private final BillingConfigService billingConfigService;

    @Value("${billing.first-cycle-grace-days:3}")
    private long firstCycleGraceDays;

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
                invoice = saveAndPublishPaidInvoice(invoice);
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
        if (invoice.getStatus() == TenantInvoiceStatus.PAID) {
            throw new BusinessException("Hóa đơn đã được thanh toán qua kênh khác. Vui lòng 'Từ chối' yêu cầu này để tránh ghi trùng.");
        }
        
        claim.setStatus(PaymentClaimStatus.VERIFIED);
        claim.setVerifiedAt(LocalDateTime.now());
        claim.setVerifiedBy(verifiedBy);
        tenantPaymentClaimRepository.save(claim);
        markPaid(invoice, claim.getMethod(), "CLAIM-" + claim.getId());
        saveAndPublishPaidInvoice(invoice);
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
                saveAndPublishPaidInvoice(invoice);
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

        YearMonth currentMonth = YearMonth.now();
        if (billingMonth.equals(currentMonth)) {
            BillingConfig billingConfig = billingConfigService.current();
            LocalDate due = ContractBillingCalendar.dueDate(
                    billingMonth,
                    ContractBillingCalendar.billingDayOfMonth(contract),
                    billingConfig.getGraceDays());
            if (LocalDate.now().isAfter(due)) {
                throw new BusinessException("UTILITY_WINDOW_CLOSED",
                        "Đã quá hạn chót phát hành hoá đơn tiền nhà kỳ này (" + due + ").");
            }
        }

        var existing = tenantInvoiceRepository.findByTenantContractIdAndInvoiceTypeAndBillingYearAndBillingMonth(
                contract.getId(), TenantInvoiceType.RENT, billingMonth.getYear(), billingMonth.getMonthValue());
        if (existing.isPresent()) {
            TenantInvoiceStatus status = existing.get().getStatus();
            if (status == TenantInvoiceStatus.PAID) {
                throw new BusinessException("PERIOD_ALREADY_SETTLED",
                        "Kỳ cước này đã được tất toán, không thể tạo thêm hoá đơn tiền nhà.");
            } else if (status != TenantInvoiceStatus.CANCELLED) {
                throw new BusinessException("INVOICE_ALREADY_EXISTS",
                        "Hoá đơn tiền nhà của kỳ này đã tồn tại.");
            }
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
                .cycleType(RentCycleType.REGULAR)
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
                .autoIssued(false)
                .build());

        return toResponse(invoice);
    }
    
    /**
     * Sau khi HĐ ACTIVE: phát hành tiền nhà còn thiếu — không thu trùng QR onboard.
     * <ul>
     *   <li>startDate thuộc tháng hiện tại → hoá đơn FIRST PENDING chỉ khi
     *       {@code HD-ONBOARD} chưa thu tiền nhà kỳ đầu (defer / kích hoạt không qua QR).</li>
     *   <li>Đã thu {@code firstRentAmount} trong HD-ONBOARD → return, không tạo FIRST lần 2.</li>
     *   <li>startDate thuộc tháng trước → catch-up REGULAR từ tháng sau chu kỳ đầu đến tháng hiện tại.</li>
     * </ul>
     */
    @Transactional
    public void generateProratedRentForNewContract(TenantContract contract) {
        if (contract == null || contract.getTenant() == null || contract.getRentAmount() == null) {
            return;
        }
        YearMonth currentMonth = YearMonth.now();
        LocalDate anchor = contract.getStartDate() != null ? contract.getStartDate() : contract.getMoveInDate();
        YearMonth firstCycleMonth = anchor != null ? YearMonth.from(anchor) : currentMonth;

        if (firstCycleMonth.isAfter(currentMonth)) {
            // startDate tháng tương lai — tiền nhà đã (hoặc sẽ) thu qua QR onboard; không phát tháng hiện tại.
            return;
        }

        if (firstCycleMonth.isBefore(currentMonth)) {
            // Tháng đầu thường đã thu qua onboard (hoặc DEFERRED). Bù mọi tháng sau đó đến hiện tại.
            for (YearMonth m = firstCycleMonth.plusMonths(1); !m.isAfter(currentMonth); m = m.plusMonths(1)) {
                issueRegularRentIfAbsent(contract, m);
            }
            return;
        }

        // firstCycleMonth == currentMonth
        if (hasFirstRentPaidViaOnboardForMonth(contract.getId(), currentMonth)) {
            return;
        }
        var existing = tenantInvoiceRepository.findByTenantContractIdAndInvoiceTypeAndBillingYearAndBillingMonth(
            contract.getId(), TenantInvoiceType.RENT, currentMonth.getYear(), currentMonth.getMonthValue());
        if (existing.isPresent()) {
            return;
        }

        RentFirstCycleCalculator.Result r = RentFirstCycleCalculator.calculate(contract, currentMonth);
        if (r.deferredToNextMonth() || "OUT_OF_MONTH".equals(r.outcome())
                || r.amount() == null || r.amount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        String periodLabel = RentFirstCycleCalculator.periodLabel(r);
        if (periodLabel == null) {
            periodLabel = "Tiền nhà tháng " + String.format("%02d/%d", currentMonth.getMonthValue(), currentMonth.getYear());
        }

        tenantInvoiceRepository.save(TenantInvoice.builder()
                .code("HD-RENT-" + contract.getId() + "-" + currentMonth)
                .tenantUserId(contract.getTenant().getId())
                .tenantContract(contract)
                .invoiceType(TenantInvoiceType.RENT)
                .cycleType(RentCycleType.FIRST)
                .propertyName(contract.getProperty().getPropertyName())
                .roomNumber(contract.getRoom() != null ? contract.getRoom().getRoomNumber() : contract.getProperty().getPropertyName())
                .billingMonth(currentMonth.getMonthValue())
                .billingYear(currentMonth.getYear())
                .billingPeriod(periodLabel)
                .note("FIRST_CYCLE|days=" + r.billedDays() + "|daysInMonth=" + r.daysInMonth()
                        + "|rentAmount=" + contract.getRentAmount().toPlainString()
                        + "|periodStart=" + r.periodStart()
                        + "|periodEnd=" + r.periodEnd()
                        + "|formula=" + (r.formula() != null ? r.formula().replace("|", "/") : ""))
                .totalAmount(r.amount())
                .lateFee(BigDecimal.ZERO)
                .grandTotal(r.amount())
                .status(TenantInvoiceStatus.PENDING)
                .dueDate(LocalDate.now().plusDays(firstCycleGraceDays))
                .createdAt(LocalDateTime.now())
                .autoIssued(true)
                .build());
    }

    private void issueRegularRentIfAbsent(TenantContract contract, YearMonth billingMonth) {
        if (contract.getEndDate() != null
                && billingMonth.isAfter(YearMonth.from(contract.getEndDate()))) {
            return;
        }
        var existing = tenantInvoiceRepository.findByTenantContractIdAndInvoiceTypeAndBillingYearAndBillingMonth(
                contract.getId(), TenantInvoiceType.RENT, billingMonth.getYear(), billingMonth.getMonthValue());
        if (existing.isPresent()) {
            return;
        }
        BigDecimal rent = contract.getRentAmount();
        if (rent == null || rent.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        RentFirstCycleCalculator.DeferredCarryOver carry =
                RentFirstCycleCalculator.deferredCarryOver(contract, billingMonth);
        BigDecimal amount = rent.add(carry.amount());
        String periodLabel = "Tiền nhà tháng " + String.format("%02d/%d",
                billingMonth.getMonthValue(), billingMonth.getYear());
        String note = "REGULAR|catchUpAfterOnboard=true|rentAmount=" + rent.toPlainString();
        if (carry.present()) {
            note += "|deferredDays=" + carry.days()
                    + "|deferredFrom=" + carry.fromMonth()
                    + "|deferredAmount=" + carry.amount().toPlainString();
        }
        tenantInvoiceRepository.save(TenantInvoice.builder()
                .code("HD-RENT-" + contract.getId() + "-" + billingMonth)
                .tenantUserId(contract.getTenant().getId())
                .tenantContract(contract)
                .invoiceType(TenantInvoiceType.RENT)
                .cycleType(RentCycleType.REGULAR)
                .propertyName(contract.getProperty().getPropertyName())
                .roomNumber(contract.getRoom() != null ? contract.getRoom().getRoomNumber()
                        : contract.getProperty().getPropertyName())
                .billingMonth(billingMonth.getMonthValue())
                .billingYear(billingMonth.getYear())
                .billingPeriod(periodLabel)
                .note(note)
                .totalAmount(amount)
                .lateFee(BigDecimal.ZERO)
                .grandTotal(amount)
                .status(TenantInvoiceStatus.PENDING)
                .dueDate(RentFirstCycleCalculator.regularRentDueDate(
                        billingMonth,
                        ContractBillingCalendar.clampDay(
                                billingMonth,
                                ContractBillingCalendar.billingDayOfMonth(contract)
                                        + billingConfigService.current().getGraceDays()),
                        firstCycleGraceDays,
                        LocalDate.now()))
                .createdAt(LocalDateTime.now())
                .autoIssued(true)
                .build());
    }

    @Override
    @Transactional
    public void recordPaidFirstRentFromOnboard(TenantContract contract, Long payosOrderCode, String method,
                                               LocalDateTime paidAt) {
        if (contract == null || contract.getRentAmount() == null) {
            return;
        }
        BigDecimal firstRentAmount = contract.getOnboardQrFirstRentAmount() != null
                ? contract.getOnboardQrFirstRentAmount()
                : TenantContractPaymentAmounts.resolveFirstRentAmount(contract);
        if (firstRentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        LocalDate anchor = contract.getStartDate() != null ? contract.getStartDate() : contract.getMoveInDate();
        YearMonth billingMonth = anchor != null ? YearMonth.from(anchor) : YearMonth.from(paidAt.toLocalDate());
        var existing = tenantInvoiceRepository.findByTenantContractIdAndInvoiceTypeAndBillingYearAndBillingMonth(
                contract.getId(), TenantInvoiceType.RENT, billingMonth.getYear(), billingMonth.getMonthValue());
        if (existing.isPresent()) {
            return;
        }

        RentFirstCycleCalculator.Result r = TenantContractPaymentAmounts.resolveFirstRentCycle(contract);
        String periodLabel = RentFirstCycleCalculator.periodLabel(r);
        if (periodLabel == null) {
            periodLabel = "Tiền nhà tháng " + String.format("%02d/%d",
                    billingMonth.getMonthValue(), billingMonth.getYear());
        }

        UUID tenantUserId = contract.getTenant() != null ? contract.getTenant().getId() : null;
        saveAndPublishPaidInvoice(TenantInvoice.builder()
                .code("HD-RENT-" + contract.getId() + "-" + billingMonth)
                .tenantUserId(tenantUserId)
                .tenantContract(contract)
                .invoiceType(TenantInvoiceType.RENT)
                .cycleType(RentCycleType.FIRST)
                .propertyName(contract.getProperty().getPropertyName())
                .roomNumber(contract.getRoom() != null ? contract.getRoom().getRoomNumber()
                        : contract.getProperty().getPropertyName())
                .billingMonth(billingMonth.getMonthValue())
                .billingYear(billingMonth.getYear())
                .billingPeriod(periodLabel)
                .note("FIRST_CYCLE|onboardPaid=true|days=" + r.billedDays()
                        + "|daysInMonth=" + r.daysInMonth()
                        + "|rentAmount=" + contract.getRentAmount().toPlainString()
                        + "|periodStart=" + r.periodStart()
                        + "|periodEnd=" + r.periodEnd()
                        + "|formula=" + (r.formula() != null ? r.formula().replace("|", "/") : ""))
                .totalAmount(firstRentAmount)
                .lateFee(BigDecimal.ZERO)
                .grandTotal(firstRentAmount)
                .status(TenantInvoiceStatus.PAID)
                .dueDate(paidAt.toLocalDate())
                .createdAt(paidAt)
                .paidAt(paidAt)
                .paymentMethod(PaymentMethods.toPublic(method != null ? method : PaymentMethods.QR))
                .transactionId(payosOrderCode != null ? String.valueOf(payosOrderCode) : null)
                .payosOrderCode(payosOrderCode)
                .autoIssued(true)
                .build());
    }

    /**
     * Chỉ chặn phát hành FIRST của đúng tháng đã thu qua onboard — không chặn tháng khác.
     */
    private boolean hasFirstRentPaidViaOnboardForMonth(Long contractId, YearMonth ym) {
        boolean paidFirstInvoice = tenantInvoiceRepository.findByTenantContractId(contractId).stream()
                .anyMatch(inv -> inv.getInvoiceType() == TenantInvoiceType.RENT
                        && inv.getCycleType() == RentCycleType.FIRST
                        && inv.getStatus() == TenantInvoiceStatus.PAID
                        && inv.getNote() != null
                        && inv.getNote().contains("onboardPaid=true")
                        && ym.getYear() == (inv.getBillingYear() != null ? inv.getBillingYear() : -1)
                        && ym.getMonthValue() == (inv.getBillingMonth() != null ? inv.getBillingMonth() : -1));
        if (paidFirstInvoice) {
            return true;
        }
        return tenantInvoiceRepository.findByCode("HD-ONBOARD-" + contractId)
                .filter(inv -> inv.getStatus() == TenantInvoiceStatus.PAID)
                .filter(inv -> parseOnboardFirstRentAmount(inv.getNote()).compareTo(BigDecimal.ZERO) > 0)
                .map(inv -> parseOnboardPeriodStartMonth(inv.getNote()))
                .filter(ym::equals)
                .isPresent();
    }

    private static BigDecimal parseOnboardFirstRentAmount(String note) {
        if (note == null) {
            return BigDecimal.ZERO;
        }
        for (String part : note.split("\\|")) {
            if (part.startsWith("firstRentAmount=")) {
                try {
                    return new BigDecimal(part.substring("firstRentAmount=".length()).trim());
                } catch (NumberFormatException e) {
                    return BigDecimal.ZERO;
                }
            }
        }
        return BigDecimal.ZERO;
    }

    private static YearMonth parseOnboardPeriodStartMonth(String note) {
        if (note == null) {
            return null;
        }
        for (String part : note.split("\\|")) {
            if (part.startsWith("periodStart=")) {
                try {
                    return YearMonth.from(LocalDate.parse(part.substring("periodStart=".length()).trim()));
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }

    static boolean isOnboardPaidInvoice(TenantInvoice invoice) {
        if (invoice == null || invoice.getNote() == null) {
            return false;
        }
        String note = invoice.getNote();
        return note.contains("onboardPaid=true") || note.startsWith("ONBOARD|");
    }

    @Override
    @Transactional(readOnly = true)
    public List<TenantInvoiceResponse> getRentInvoicesForProperty(Long propertyId, String month) {
        propertyAccessService.assertCanManageProperty(propertyId);
        YearMonth ym = parseBillingMonth(month);
        return tenantInvoiceRepository.findRentInvoicesByPropertyAndMonth(propertyId, ym.getYear(), ym.getMonthValue())
                .stream().map(this::toResponse).toList();
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

        if (invoice.getInvoiceType() == TenantInvoiceType.RENT && invoice.getTenantContract() != null) {
            if (Boolean.TRUE.equals(invoice.getTenantContract().getTerminationProposed())) {
                invoice.getTenantContract().setTerminationProposed(false);
                tenantContractRepository.save(invoice.getTenantContract());
            }
        }

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

    private TenantInvoice saveAndPublishPaidInvoice(TenantInvoice invoice) {
        TenantInvoice savedInvoice = tenantInvoiceRepository.save(invoice);
        realtimeEventService.publishInvoicePaid(savedInvoice);
        notifyManagerPaymentReceived(savedInvoice);
        return savedInvoice;
    }

    /**
     * In-app + Expo push cho operation manager. Không kèm số tiền (chính sách ẩn tiền manager).
     * Type chính thức: {@code PAYMENT_RECEIVED_MANAGER}.
     */
    private void notifyManagerPaymentReceived(TenantInvoice invoice) {
        // FIRST RENT thu cùng QR onboard đã có DEPOSIT_PAID_MANAGER → ResumeContract.
        if (invoice.getCycleType() == RentCycleType.FIRST && isOnboardPaidInvoice(invoice)) {
            return;
        }
        TenantContract contract = invoice.getTenantContract();
        if (contract == null || contract.getProperty() == null) {
            return;
        }
        UUID managerId = contract.getProperty().getOperationManagerId();
        if (managerId == null && contract.getProperty().getManagedBy() != null) {
            managerId = contract.getProperty().getManagedBy();
        }
        if (managerId == null && contract.getAssignedManager() != null) {
            managerId = contract.getAssignedManager().getId();
        }
        if (managerId == null) {
            return;
        }

        String tenantName = "khách";
        if (contract.getTenant() != null && contract.getTenant().getUser() != null
                && contract.getTenant().getUser().getFullName() != null
                && !contract.getTenant().getUser().getFullName().isBlank()) {
            tenantName = contract.getTenant().getUser().getFullName();
        } else if (contract.getDraftTenantName() != null && !contract.getDraftTenantName().isBlank()) {
            tenantName = contract.getDraftTenantName();
        }
        String roomLabel = invoice.getRoomNumber() != null && !invoice.getRoomNumber().isBlank()
                ? invoice.getRoomNumber()
                : (contract.getRoom() != null ? contract.getRoom().getRoomNumber() : "nguyên căn");
        String typeLabel = invoiceTypeLabel(invoice.getInvoiceType());

        String title = "💰 Khách đã thanh toán";
        String body = "Khách " + tenantName + " · Phòng " + roomLabel + " đã thanh toán " + typeLabel + ".";
        String paramsJson = "{\"invoiceId\":" + invoice.getId()
                + ",\"contractId\":" + contract.getId() + "}";

        notificationRepository.save(Notification.builder()
                .userId(managerId)
                .title(title)
                .content(body)
                .type("PAYMENT_RECEIVED_MANAGER")
                .screen("InvoiceList")
                .paramsJson(paramsJson)
                .read(false)
                .build());

        Map<String, Object> data = new HashMap<>();
        data.put("type", "PAYMENT_RECEIVED_MANAGER");
        data.put("screen", "InvoiceList");
        data.put("invoiceId", invoice.getId());
        data.put("contractId", contract.getId());
        userPushTokenService.sendToUser(managerId, title, body, data);
    }

    private static String invoiceTypeLabel(TenantInvoiceType type) {
        if (type == null) {
            return "hoá đơn";
        }
        return switch (type) {
            case RENT -> "tiền nhà";
            case ELECTRICITY -> "tiền điện";
            case WATER -> "tiền nước";
            case SERVICE -> "phí dịch vụ";
            case MAINTENANCE -> "phí bảo trì";
            case OTHER -> "hoá đơn onboard";
        };
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
                .cycleType(invoice.getCycleType() != null ? invoice.getCycleType().name() : null)
                .propertyName(invoice.getPropertyName())
                .roomNumber(invoice.getRoomNumber())
                .month(invoice.getBillingMonth())
                .year(invoice.getBillingYear())
                .billingPeriod(invoice.getBillingPeriod())
                .items(InvoiceItemBuilder.buildItems(invoice))
                .paymentBreakdown(PaymentBreakdownBuilder.fromInvoice(invoice))
                .totalAmount(invoice.getTotalAmount())
                .lateFee(invoice.getLateFee())
                .grandTotal(invoice.getGrandTotal())
                .status(invoice.getStatus().name())
                .dueDate(invoice.getDueDate())
                .createdAt(invoice.getCreatedAt())
                .paidAt(invoice.getPaidAt())
                .paymentMethod(PaymentMethods.toPublic(invoice.getPaymentMethod()))
                .transactionId(invoice.getTransactionId())
                .kwhUsed(invoice.getKwhUsed())
                .electricityRate(invoice.getElectricityRate())
                .m3Used(invoice.getM3Used())
                .waterRate(invoice.getWaterRate())
                .payosCheckoutUrl(invoice.getPayosCheckoutUrl())
                .payosQrCode(invoice.getPayosQrCode())
                .payosOrderCode(invoice.getPayosOrderCode())
                .autoIssued(invoice.getAutoIssued())
                .onboardPaid(isOnboardPaidInvoice(invoice))
                .build();
    }

    private TenantPaymentResponse toPaymentResponse(TenantPayment payment) {
        return TenantPaymentResponse.builder()
                .id(payment.getId())
                .invoiceId(payment.getTenantInvoice().getId())
                .invoiceCode(payment.getInvoiceCode())
                .invoiceType(payment.getInvoiceType().name())
                .amount(payment.getAmount())
                .method(PaymentMethods.toPublic(payment.getMethod()))
                .paidAt(payment.getPaidAt())
                .transactionId(payment.getTransactionId())
                .propertyName(payment.getPropertyName())
                .roomNumber(payment.getRoomNumber())
                .build();
    }
}
