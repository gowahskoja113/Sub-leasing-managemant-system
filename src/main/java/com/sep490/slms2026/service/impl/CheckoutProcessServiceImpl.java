package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.CheckoutDisputeRequest;
import com.sep490.slms2026.dto.request.CheckoutInspectionRequest;
import com.sep490.slms2026.dto.request.CheckoutRefundRequest;
import com.sep490.slms2026.dto.response.CheckoutInspectionResponse;
import com.sep490.slms2026.dto.response.CheckoutSettlementResponse;
import com.sep490.slms2026.dto.response.DepositRefundResponse;
import com.sep490.slms2026.entity.*;
import com.sep490.slms2026.enums.CheckoutRequestStatus;
import com.sep490.slms2026.enums.DepositStatus;
import com.sep490.slms2026.enums.InvoiceStatus;
import com.sep490.slms2026.enums.Role;
import com.sep490.slms2026.repository.CheckoutInspectionRepository;
import com.sep490.slms2026.repository.CheckoutRequestRepository;
import com.sep490.slms2026.repository.CheckoutSettlementRepository;
import com.sep490.slms2026.repository.InvoiceRepository;
import com.sep490.slms2026.repository.TenantInvoiceRepository;
import com.sep490.slms2026.repository.UtilityInvoiceRepository;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.service.CheckoutProcessService;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.exception.BusinessException;
import org.springframework.security.access.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sep490.slms2026.repository.NotificationRepository;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.service.PushNotificationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
public class CheckoutProcessServiceImpl implements CheckoutProcessService {

    private final CheckoutRequestRepository checkoutRequestRepository;
    private final CheckoutInspectionRepository checkoutInspectionRepository;
    private final CheckoutSettlementRepository checkoutSettlementRepository;
    private final InvoiceRepository invoiceRepository;
    private final TenantInvoiceRepository tenantInvoiceRepository;
    private final UtilityInvoiceRepository utilityInvoiceRepository;
    private final NotificationRepository notificationRepository;
    private final PushNotificationService pushNotificationService;
    private final UserRepository userRepository;
    private final com.sep490.slms2026.repository.MeterReadingRepository meterReadingRepository;
    private final com.sep490.slms2026.service.TenantBillingService tenantBillingService;
    private final com.sep490.slms2026.repository.DepositAuditLogRepository depositAuditLogRepository;
    private final com.sep490.slms2026.service.TwilioService twilioService;
    private final com.sep490.slms2026.service.MaintenanceService maintenanceService;

    private static final List<CheckoutRequestStatus> INSPECTION_EDITABLE_STATUSES = List.of(
            CheckoutRequestStatus.APPROVED,
            CheckoutRequestStatus.INSPECTING,
            CheckoutRequestStatus.DISPUTED);

    /** Hoàn cọc chạy song song với thanh lý — ghi nhận được khi đang quyết toán hoặc đã đóng hồ sơ. */
    private static final List<CheckoutRequestStatus> REFUND_ALLOWED_STATUSES = List.of(
            CheckoutRequestStatus.SETTLING,
            CheckoutRequestStatus.COMPLETED);

    @Override
    @Transactional
    public void saveInspection(Long checkoutRequestId, CheckoutInspectionRequest request) {
        CheckoutRequest checkoutRequest = checkoutRequestRepository.findById(checkoutRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu trả phòng ID=" + checkoutRequestId));

        requireInspectionEditable(checkoutRequest);
        CheckoutRequestStatus previousStatus = checkoutRequest.getStatus();

        CheckoutInspection inspection = checkoutInspectionRepository.findByCheckoutRequestId(checkoutRequestId)
                .orElse(new CheckoutInspection());

        inspection.setCheckoutRequest(checkoutRequest);
        inspection.setRoomConditionNote(request.getRoomConditionNote());
        inspection.setElectricityFinalReading(request.getElectricityFinalReading());
        inspection.setElectricMeterImageUrl(request.getElectricMeterImageUrl());
        inspection.setWaterFinalReading(request.getWaterFinalReading());
        inspection.setWaterMeterImageUrl(request.getWaterMeterImageUrl());
        inspection.setPhotos(request.getPhotos() != null ? request.getPhotos() : new ArrayList<>());

        if (inspection.getDamages() != null) {
            inspection.getDamages().clear();
        } else {
            inspection.setDamages(new ArrayList<>());
        }

        if (request.getDamages() != null) {
            for (var d : request.getDamages()) {
                CheckoutDamageItem item = CheckoutDamageItem.builder()
                        .checkoutInspection(inspection)
                        .equipmentId(d.getEquipmentId())
                        .maintenanceRequestId(d.getMaintenanceRequestId())
                        .label(d.getLabel())
                        .amount(d.getAmount())
                        .note(d.getNote())
                        .photos(d.getPhotos() != null ? d.getPhotos() : new ArrayList<>())
                        .build();
                inspection.getDamages().add(item);
            }
        }

        checkoutInspectionRepository.save(inspection);

        if (inspection.getDamages() != null) {
            for (CheckoutDamageItem item : inspection.getDamages()) {
                if (item.getMaintenanceRequestId() != null && item.getId() != null) {
                    maintenanceService.markOutstandingDamageResolved(
                            item.getMaintenanceRequestId(), item.getId(), item.getAmount());
                }
            }
        }

        TenantContract contract = checkoutRequest.getTenantContract();
        if (contract != null && checkoutRequest.getExpectedMoveOutDate() != null) {
            LocalDate moveOutDate = checkoutRequest.getExpectedMoveOutDate();
            if (checkoutRequest.getCompletedAt() != null) {
                moveOutDate = checkoutRequest.getCompletedAt().toLocalDate();
            }
            if (request.getElectricityFinalReading() != null) {
                createFinalUtilityInvoice(contract, com.sep490.slms2026.enums.UtilityType.ELECTRIC, 
                        BigDecimal.valueOf(request.getElectricityFinalReading()), 
                        request.getElectricMeterImageUrl(), moveOutDate, request.getElectricityUnitPrice());
            }
            if (request.getWaterFinalReading() != null) {
                createFinalUtilityInvoice(contract, com.sep490.slms2026.enums.UtilityType.WATER, 
                        BigDecimal.valueOf(request.getWaterFinalReading()), 
                        request.getWaterMeterImageUrl(), moveOutDate, request.getWaterUnitPrice());
            }
            syncCompensationInvoice(contract, moveOutDate, inspection.getDamages(), checkoutRequest.getCreatedAt());
        }

        if (checkoutRequest.getStatus() != CheckoutRequestStatus.INSPECTING) {
            checkoutRequest.setStatus(CheckoutRequestStatus.INSPECTING);
            checkoutRequestRepository.save(checkoutRequest);
        }

        // DISPUTED: manager đang sửa lại; khách chỉ nhận bảng mới khi submit settlement.
        if (previousStatus != CheckoutRequestStatus.DISPUTED) {
            Map<String, Object> data = new HashMap<>();
            data.put("screen", "CheckoutDetail");
            Map<String, Object> params = new HashMap<>();
            params.put("requestId", checkoutRequest.getId());
            data.put("params", params);

            String title = "Biên bản kiểm tra phòng";
            String content = "Quản lý đã lưu biên bản kiểm tra phòng của bạn.";
            sendNotification(checkoutRequest.getTenantUserId(), "CHECKOUT_INSPECTED", title, content, data);
        }
    }

    @Override
    public CheckoutInspectionResponse getInspection(Long checkoutRequestId) {
        CheckoutInspection inspection = checkoutInspectionRepository.findByCheckoutRequestId(checkoutRequestId)
                .orElseThrow(() -> new RuntimeException("Inspection not found"));

        return CheckoutInspectionResponse.builder()
                .id(inspection.getId())
                .roomConditionNote(inspection.getRoomConditionNote())
                .electricityFinalReading(inspection.getElectricityFinalReading())
                .electricMeterImageUrl(inspection.getElectricMeterImageUrl())
                .waterFinalReading(inspection.getWaterFinalReading())
                .waterMeterImageUrl(inspection.getWaterMeterImageUrl())
                .photos(inspection.getPhotos())
                .damages(inspection.getDamages().stream().map(d -> CheckoutInspectionResponse.DamageItemResponse.builder()
                        .id(d.getId())
                        .equipmentId(d.getEquipmentId())
                        .maintenanceRequestId(d.getMaintenanceRequestId())
                        .label(d.getLabel())
                        .amount(d.getAmount())
                        .note(d.getNote())
                        .photos(d.getPhotos())
                        .build()).collect(Collectors.toList()))
                .build();
    }

    @Override
    public CheckoutSettlementResponse getSettlement(Long checkoutRequestId) {
        CheckoutRequest checkoutRequest = checkoutRequestRepository.findById(checkoutRequestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy yêu cầu trả phòng ID=" + checkoutRequestId));

        CheckoutInspection inspection = checkoutInspectionRepository.findByCheckoutRequestId(checkoutRequestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Chưa có biên bản kiểm tra cho yêu cầu trả phòng ID=" + checkoutRequestId
                                + " — không tính được quyết toán."));

        BigDecimal deposit = checkoutRequest.getTenantContract().getDeposit();
        if (deposit == null) deposit = BigDecimal.ZERO;

        TenantContract contract = checkoutRequest.getTenantContract();

        List<com.sep490.slms2026.entity.TenantInvoice> allContractInvoices = tenantInvoiceRepository.findByTenantContractId(contract.getId());
        List<com.sep490.slms2026.entity.TenantInvoice> relevantInvoices = allContractInvoices.stream()
                .filter(inv -> {
                    if (inv.getStatus() != com.sep490.slms2026.enums.TenantInvoiceStatus.PAID && inv.getStatus() != com.sep490.slms2026.enums.TenantInvoiceStatus.CANCELLED) {
                        return true;
                    }
                    if (inv.getInvoiceType() == com.sep490.slms2026.enums.TenantInvoiceType.COMPENSATION) {
                        return inv.getCreatedAt() != null && !inv.getCreatedAt().isBefore(checkoutRequest.getCreatedAt());
                    }
                    if (inv.getBillingPeriod() != null && inv.getBillingPeriod().contains("chốt trả phòng")) {
                        return true;
                    }
                    return false;
                }).collect(Collectors.toList());

        BigDecimal chargesTotal = relevantInvoices.stream()
                .map(com.sep490.slms2026.entity.TenantInvoice::getGrandTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal chargesPaid = relevantInvoices.stream()
                .filter(inv -> inv.getStatus() == com.sep490.slms2026.enums.TenantInvoiceStatus.PAID)
                .map(com.sep490.slms2026.entity.TenantInvoice::getGrandTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        boolean chargesSettled = chargesTotal.compareTo(BigDecimal.ZERO) <= 0 || chargesPaid.compareTo(chargesTotal) >= 0;

        BigDecimal damageTotal = inspection.getDamages().stream()
                .map(CheckoutDamageItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal adjustmentTotal = BigDecimal.ZERO;
        List<CheckoutSettlementResponse.AdjustmentResponse> adjustments = new ArrayList<>();

        BigDecimal rentAmount = contract.getRentAmount();
        
        LocalDate moveOutDate = checkoutRequest.getExpectedMoveOutDate();
        if (contract.getEndDate() != null) {
            moveOutDate = contract.getEndDate();
        }
        if (checkoutRequest.getCompletedAt() != null) {
            moveOutDate = checkoutRequest.getCompletedAt().toLocalDate();
        }

        LocalDate startDate = contract.getStartDate();
        LocalDate firstDayOfMonth = moveOutDate.withDayOfMonth(1);
        LocalDate startOfStay = firstDayOfMonth;
        if (startDate.isAfter(firstDayOfMonth) && startDate.isBefore(moveOutDate.plusDays(1))) {
            startOfStay = startDate;
        }

        long daysStayed = java.time.temporal.ChronoUnit.DAYS.between(startOfStay, moveOutDate) + 1;
        long daysInMonth = moveOutDate.lengthOfMonth();
        
        BigDecimal rentPerDay = rentAmount.divide(BigDecimal.valueOf(daysInMonth), 0, java.math.RoundingMode.HALF_UP);
        BigDecimal rentForStayedDays = rentPerDay.multiply(BigDecimal.valueOf(daysStayed));
        
        java.time.YearMonth ym = java.time.YearMonth.from(moveOutDate);
        Optional<com.sep490.slms2026.entity.TenantInvoice> rentInvoiceOpt = 
                tenantInvoiceRepository.findByTenantContractIdAndInvoiceTypeAndBillingYearAndBillingMonth(
                    contract.getId(), com.sep490.slms2026.enums.TenantInvoiceType.RENT, ym.getYear(), ym.getMonthValue());
        
        BigDecimal tienDaDongChoThangDo = rentAmount; // Default full month
        boolean invoiceFound = false;
        if (rentInvoiceOpt.isPresent()) {
            tienDaDongChoThangDo = rentInvoiceOpt.get().getGrandTotal();
            if (tienDaDongChoThangDo == null) tienDaDongChoThangDo = rentInvoiceOpt.get().getTotalAmount();
            invoiceFound = true;
        }

        if (invoiceFound || moveOutDate.isAfter(contract.getStartDate().plusDays(20))) { // Only adjust if invoice was likely generated or they stayed long enough
            BigDecimal phanDu = tienDaDongChoThangDo.subtract(rentForStayedDays);
            if (phanDu.compareTo(BigDecimal.ZERO) > 0) {
                String label = String.format("Hoàn tiền phòng những ngày không ở (%02d/%02d–%02d/%02d)", 
                        moveOutDate.plusDays(1).getDayOfMonth(), moveOutDate.plusDays(1).getMonthValue(),
                        moveOutDate.lengthOfMonth(), moveOutDate.getMonthValue());
                adjustments.add(CheckoutSettlementResponse.AdjustmentResponse.builder()
                        .label(label)
                        .amount(phanDu)
                        .build());
                adjustmentTotal = adjustmentTotal.add(phanDu);
            } else if (phanDu.compareTo(BigDecimal.ZERO) < 0) {
                String label = String.format("Tiền phòng những ngày ở thêm");
                adjustments.add(CheckoutSettlementResponse.AdjustmentResponse.builder()
                        .label(label)
                        .amount(phanDu) // negative value
                        .build());
                adjustmentTotal = adjustmentTotal.add(phanDu);
            }
        }

        Optional<CheckoutSettlement> savedOpt = checkoutSettlementRepository.findByCheckoutRequestId(checkoutRequestId);
        if (savedOpt.isPresent()) {
            CheckoutSettlement saved = savedOpt.get();
            return CheckoutSettlementResponse.builder()
                    .depositAmount(saved.getDepositAmount())
                    .finalCharges(saved.getSettlementInvoices().stream().map(inv -> CheckoutSettlementResponse.InvoiceResponse.builder()
                            .id(inv.getInvoiceId())
                            .code(inv.getInvoiceCode())
                            .type(inv.getInvoiceType())
                            .amount(inv.getAmount())
                            .build()).collect(Collectors.toList()))
                    .chargesTotal(chargesTotal)
                    .chargesPaid(chargesPaid)
                    .chargesSettled(chargesSettled)
                    .adjustments(saved.getSettlementAdjustments().stream().map(adj -> CheckoutSettlementResponse.AdjustmentResponse.builder()
                            .label(adj.getLabel())
                            .amount(adj.getAmount())
                            .build()).collect(Collectors.toList()))
                    .adjustmentTotal(saved.getAdjustmentTotal())
                    .refundProofUrl(saved.getRefundProofUrl())
                    .refundedAt(saved.getRefundPaidAt() != null ? saved.getRefundPaidAt().toLocalDate() : null)
                    .build();
        }

        List<CheckoutSettlementResponse.InvoiceResponse> finalChargeResponses = new ArrayList<>();
        for (var inv : relevantInvoices) {
            finalChargeResponses.add(CheckoutSettlementResponse.InvoiceResponse.builder()
                    .id(inv.getId())
                    .code(inv.getCode() != null ? inv.getCode() : "INVOICE")
                    .type(inv.getInvoiceType() != null ? inv.getInvoiceType().name() : "OTHER")
                    .amount(inv.getGrandTotal())
                    .build());
        }

        return CheckoutSettlementResponse.builder()
                .depositAmount(deposit)
                .finalCharges(finalChargeResponses)
                .chargesTotal(chargesTotal)
                .chargesPaid(chargesPaid)
                .chargesSettled(chargesSettled)
                .adjustments(adjustments)
                .adjustmentTotal(adjustmentTotal)
                .build();
    }

    @Override
    @Transactional
    public void submitSettlement(Long checkoutRequestId) {
        CheckoutRequest checkoutRequest = checkoutRequestRepository.findById(checkoutRequestId)
                .orElseThrow(() -> new RuntimeException("Checkout request not found"));

        if (checkoutRequest.getStatus() != CheckoutRequestStatus.INSPECTING && checkoutRequest.getStatus() != CheckoutRequestStatus.DISPUTED) {
            throw new RuntimeException("Invalid status for submitting settlement");
        }

        CheckoutSettlementResponse settlementData = getSettlement(checkoutRequestId);

        // Verification constraint #2: Database has been queried inside getSettlement.
        // The results we got are the current truth. We lock this into the CheckoutSettlement entity.
        
        CheckoutSettlement settlement = checkoutSettlementRepository.findByCheckoutRequestId(checkoutRequestId)
                .orElse(new CheckoutSettlement());

        settlement.setCheckoutRequest(checkoutRequest);
        settlement.setDepositAmount(settlementData.getDepositAmount());
        
        BigDecimal unpaidTotal = settlementData.getFinalCharges().stream().filter(inv -> !"COMPENSATION".equals(inv.getType())).map(CheckoutSettlementResponse.InvoiceResponse::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal damageTotal = settlementData.getFinalCharges().stream().filter(inv -> "COMPENSATION".equals(inv.getType())).map(CheckoutSettlementResponse.InvoiceResponse::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal refundAmount = settlementData.getDepositAmount();
        
        // Phí cuối kỳ khách phải đóng = Tổng nợ (chargesTotal) trừ đi số tiền được hoàn (adjustmentTotal)
        BigDecimal extraChargeAmount = settlementData.getChargesTotal().subtract(settlementData.getAdjustmentTotal());
        
        if (extraChargeAmount.compareTo(BigDecimal.ZERO) < 0) {
            // Nếu khoản giảm trừ lớn hơn tổng nợ, số dư được cộng thêm vào hoàn cọc để trả lại khách.
            refundAmount = refundAmount.add(extraChargeAmount.abs());
            extraChargeAmount = BigDecimal.ZERO;
        }

        settlement.setUnpaidTotal(unpaidTotal);
        settlement.setDamageTotal(damageTotal);
        settlement.setAdjustmentTotal(settlementData.getAdjustmentTotal());
        settlement.setRefundAmount(refundAmount);
        settlement.setExtraChargeAmount(extraChargeAmount);

        if (settlement.getSettlementInvoices() != null) {
            settlement.getSettlementInvoices().clear();
        } else {
            settlement.setSettlementInvoices(new ArrayList<>());
        }

        if (settlementData.getFinalCharges() != null) {
            for (var inv : settlementData.getFinalCharges()) {
                settlement.getSettlementInvoices().add(CheckoutSettlementInvoice.builder()
                        .checkoutSettlement(settlement)
                        .invoiceId(inv.getId())
                        .invoiceCode(inv.getCode())
                        .invoiceType(inv.getType())
                        .amount(inv.getAmount())
                        .build());
            }
        }

        if (settlement.getSettlementAdjustments() != null) {
            settlement.getSettlementAdjustments().clear();
        } else {
            settlement.setSettlementAdjustments(new ArrayList<>());
        }

        if (settlementData.getAdjustments() != null) {
            for (var adj : settlementData.getAdjustments()) {
                settlement.getSettlementAdjustments().add(CheckoutSettlementAdjustment.builder()
                        .checkoutSettlement(settlement)
                        .label(adj.getLabel())
                        .amount(adj.getAmount())
                        .build());
            }
        }

        checkoutSettlementRepository.save(settlement);

        checkoutRequest.setStatus(CheckoutRequestStatus.WAITING_TENANT);
        checkoutRequestRepository.save(checkoutRequest);

        Map<String, Object> data = new HashMap<>();
        data.put("screen", "CheckoutDetail");
        Map<String, Object> params = new HashMap<>();
        params.put("requestId", checkoutRequest.getId());
        data.put("params", params);
        
        String title = "Bảng quyết toán trả phòng";
        String content = "Quản lý đã gửi bảng quyết toán trả phòng. Vui lòng kiểm tra và xác nhận.";
        sendNotification(checkoutRequest.getTenantUserId(), "CHECKOUT_SETTLEMENT_SENT", title, content, data);
    }

    @Override
    @Transactional
    public void acceptSettlement(Long checkoutRequestId, UUID tenantId) {
        CheckoutRequest checkoutRequest = checkoutRequestRepository.findById(checkoutRequestId)
                .orElseThrow(() -> new RuntimeException("Checkout request not found"));

        if (!checkoutRequest.getTenantUserId().equals(tenantId)) {
            throw new RuntimeException("Unauthorized access to this checkout request");
        }

        if (checkoutRequest.getStatus() != CheckoutRequestStatus.WAITING_TENANT) {
            throw new RuntimeException("Checkout request is not waiting for tenant confirmation");
        }

        checkoutRequest.setStatus(CheckoutRequestStatus.SETTLING);
        checkoutRequestRepository.save(checkoutRequest);

        UUID managerId = getManagerId(checkoutRequest.getTenantContract());
        if (managerId != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("screen", "CheckoutSettlement");
            Map<String, Object> params = new HashMap<>();
            params.put("checkoutId", checkoutRequest.getId());
            data.put("params", params);
            
            String roomStr = checkoutRequest.getTenantContract().getRoom() != null ? checkoutRequest.getTenantContract().getRoom().getRoomNumber() : "Nguyên căn";
            String title = "Khách đồng ý quyết toán";
            String content = "Khách thuê phòng " + roomStr + " đã đồng ý với bảng quyết toán.";
            sendNotification(managerId, "CHECKOUT_SETTLEMENT_ACCEPTED", title, content, data);
        }
    }

    @Override
    @Transactional
    public void disputeSettlement(Long checkoutRequestId, UUID tenantId, CheckoutDisputeRequest request) {
        CheckoutRequest checkoutRequest = checkoutRequestRepository.findById(checkoutRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Checkout request not found"));

        if (!checkoutRequest.getTenantUserId().equals(tenantId)) {
            throw new RuntimeException("Unauthorized access to this checkout request");
        }

        if (checkoutRequest.getStatus() != CheckoutRequestStatus.WAITING_TENANT) {
            throw new RuntimeException("Checkout request is not waiting for tenant confirmation");
        }

        // Verification constraint #1: Limit dispute count to 3
        if (checkoutRequest.getDisputeCount() != null && checkoutRequest.getDisputeCount() >= 3) {
            throw new RuntimeException("Maximum dispute limit (3) reached. You cannot dispute anymore.");
        }

        checkoutRequest.setDisputeCount((checkoutRequest.getDisputeCount() == null ? 0 : checkoutRequest.getDisputeCount()) + 1);
        checkoutRequest.setStatus(CheckoutRequestStatus.DISPUTED);
        checkoutRequest.setDisputeReason(request.getReason());
        if (request.getPhotos() != null) {
            checkoutRequest.setDisputePhotos(new ArrayList<>(request.getPhotos()));
        } else {
            checkoutRequest.setDisputePhotos(new ArrayList<>());
        }
        checkoutRequest.setDisputedAt(LocalDateTime.now());
        
        checkoutRequestRepository.save(checkoutRequest);

        UUID managerId = getManagerId(checkoutRequest.getTenantContract());
        if (managerId != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("screen", "CheckoutSettlement");
            Map<String, Object> params = new HashMap<>();
            params.put("checkoutId", checkoutRequest.getId());
            data.put("params", params);
            
            String roomStr = checkoutRequest.getTenantContract().getRoom() != null ? checkoutRequest.getTenantContract().getRoom().getRoomNumber() : "Nguyên căn";
            String title = "Khách phản đối quyết toán";
            String content = "Khách thuê phòng " + roomStr + " đã gửi khiếu nại bảng quyết toán.";
            sendNotification(managerId, "CHECKOUT_DISPUTED", title, content, data);
        }
    }

    @Override
    @Transactional
    public DepositRefundResponse refundByContractId(Long contractId, CheckoutRefundRequest request) {
        List<CheckoutRequest> requests = checkoutRequestRepository.findByTenantContractIdOrderByCreatedAtDesc(contractId);
        if (requests.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy hồ sơ trả phòng cho hợp đồng ID=" + contractId);
        }
        CheckoutRequest chosen = pickRefundableCheckout(requests);
        return refund(chosen.getId(), request);
    }

    @Override
    @Transactional
    public DepositRefundResponse refund(Long checkoutRequestId, CheckoutRefundRequest request) {
        CheckoutRequest checkoutRequest = checkoutRequestRepository.findById(checkoutRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu trả phòng ID=" + checkoutRequestId));

        assertCanRecordDepositRefund(checkoutRequest);

        TenantContract contract = checkoutRequest.getTenantContract();
        List<TenantInvoice> unpaid = tenantInvoiceRepository
                .findByTenantContractIdAndStatusNotIn(contract.getId(),
                        List.of(com.sep490.slms2026.enums.TenantInvoiceStatus.PAID, com.sep490.slms2026.enums.TenantInvoiceStatus.CANCELLED));
        if (!unpaid.isEmpty()) {
            BigDecimal owed = unpaid.stream().map(TenantInvoice::getGrandTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            throw new BusinessException("CHARGES_NOT_SETTLED",
                    "Khách còn " + unpaid.size() + " khoản chưa thanh toán ("
                    + owed.toBigInteger() + "đ). Thu đủ rồi mới hoàn cọc được.");
        }

        if (!REFUND_ALLOWED_STATUSES.contains(checkoutRequest.getStatus())) {
            throw new BusinessException(
                    "REFUND_NOT_ALLOWED",
                    "Chỉ ghi nhận hoàn cọc khi hồ sơ đang quyết toán hoặc đã thanh lý. Trạng thái hiện tại: "
                            + checkoutRequest.getStatus() + ".");
        }

        CheckoutSettlement settlement = checkoutSettlementRepository.findByCheckoutRequestId(checkoutRequestId)
                .orElseGet(() -> createRefundSettlement(checkoutRequest, request));

        if (settlement.getRefundPaidAt() != null) {
            throw new BusinessException("REFUND_ALREADY_RECORDED", "Đã ghi nhận hoàn cọc trước đó.");
        }

        BigDecimal remaining = nz(settlement.getRefundAmount());
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    "REFUND_NOT_ALLOWED",
                    "Không xác định được số tiền cọc của hợp đồng này.");
        }

        if (request.getPaidAt() == null) {
            throw new BusinessException("REFUND_INVALID", "Ngày chuyển hoàn cọc không được để trống");
        }
        
        // Hash check
        if (request.getProofUrl() != null && !request.getProofUrl().isBlank()) {
            String hash = hashFileContent(request.getProofUrl());
            if (hash != null && checkoutSettlementRepository.existsByRefundProofHash(hash)) {
                throw new BusinessException("DUPLICATE_PROOF", "Bằng chứng hoàn cọc này đã được sử dụng ở một khoản cọc khác. Vui lòng kiểm tra lại (Lỗi 409).");
            }
            settlement.setRefundProofHash(hash);
        }

        if (request.getAmount() != null) {
            settlement.setRefundAmount(request.getAmount());
        }
        settlement.setRefundMethod(request.getMethod());
        settlement.setRefundProofUrl(request.getProofUrl());
        settlement.setRefundPaidAt(request.getPaidAt().atStartOfDay());
        settlement.setRefundNote(request.getNote());
        
        checkoutSettlementRepository.save(settlement);
        
        // Audit log
        depositAuditLogRepository.save(DepositAuditLog.builder()
                .contractId(contract.getId())
                .action("REFUND_RECORDED")
                .actorUserId(SecurityUtils.requireCurrentUser().getId())
                .actorRole(SecurityUtils.requireCurrentUser().getRole().name())
                .payloadJson(String.format("{\"amount\":%s, \"method\":\"%s\"}", settlement.getRefundAmount(), settlement.getRefundMethod()))
                .build());
        
        // Notification
        UUID tenantUserId = checkoutRequest.getTenantUserId();
        if (tenantUserId != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("screen", "CheckoutDetail");
            Map<String, Object> params = new HashMap<>();
            params.put("requestId", checkoutRequest.getId());
            data.put("params", params);

            String roomStr = contract.getRoom() != null ? contract.getRoom().getRoomNumber() : "Nguyên căn";
            String title = "Đã nhận tiền hoàn cọc";
            String content = "Quản lý đã chuyển tiền hoàn cọc phòng " + roomStr + " cho bạn. Vui lòng xác nhận khi nhận được.";
            sendNotification(tenantUserId, "CHECKOUT_REFUND_TRANSFERRED", title, content, data);
            
            String tenantPhone = contract.getTenant() != null && contract.getTenant().getUser() != null ? contract.getTenant().getUser().getPhoneNumber() : null;
            if (tenantPhone != null && !tenantPhone.isBlank()) {
                String smsMessage = "Chu thue da chuyen tien hoan coc phong " + roomStr + ". Vui long vao app SLMS de xac nhan da nhan duoc tien.";
                twilioService.sendSms(tenantPhone, smsMessage);
            }
        }

        checkoutSettlementRepository.save(settlement);

        Map<String, Object> data = new HashMap<>();
        data.put("screen", "CheckoutDetail");
        Map<String, Object> params = new HashMap<>();
        params.put("requestId", checkoutRequest.getId());
        data.put("params", params);

        String title = "Xác nhận hoàn cọc";
        String content = "Đã ghi nhận hoàn tiền cọc cho bạn.";
        sendNotification(checkoutRequest.getTenantUserId(), "CHECKOUT_REFUNDED", title, content, data);

        contract = checkoutRequest.getTenantContract();
        return DepositRefundResponse.builder()
                .contractId(contract != null ? contract.getId() : null)
                .checkoutRequestId(checkoutRequest.getId())
                .status(DepositStatus.REFUNDED.name())
                .amount(settlement.getRefundAmount())
                .refundedAt(settlement.getRefundPaidAt().toLocalDate())
                .method(settlement.getRefundMethod())
                .proofUrl(settlement.getRefundProofUrl())
                .build();
    }

    private void sendNotification(UUID targetUserId, String type, String title, String content, Map<String, Object> data) {
        if (targetUserId == null) return;
        
        String screen = data != null ? (String) data.get("screen") : null;
        String paramsJson = null;
        if (data != null && data.get("params") != null) {
            try {
                paramsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data.get("params"));
            } catch (Exception e) {
                // Ignore parse error
            }
        }

        Notification notification = Notification.builder()
                .userId(targetUserId)
                .title(title)
                .content(content)
                .type(type)
                .screen(screen)
                .paramsJson(paramsJson)
                .read(false)
                .build();
        notificationRepository.save(notification);

        userRepository.findById(targetUserId).ifPresent(u -> {
            if (u.getPushToken() != null && !u.getPushToken().isBlank()) {
                pushNotificationService.sendPushNotification(u.getPushToken(), title, content, data);
            }
        });
    }

    private void requireInspectionEditable(CheckoutRequest checkoutRequest) {
        if (INSPECTION_EDITABLE_STATUSES.contains(checkoutRequest.getStatus())) {
            return;
        }
        List<String> allowed = INSPECTION_EDITABLE_STATUSES.stream().map(Enum::name).toList();
        Map<String, Object> details = new HashMap<>();
        details.put("currentStatus", checkoutRequest.getStatus().name());
        details.put("allowedStatuses", allowed);
        throw new BusinessException(
                "CHECKOUT_INVALID_STATUS",
                "Không thể lưu biên bản kiểm tra khi hồ sơ đang ở trạng thái "
                        + checkoutRequest.getStatus()
                        + ". Chỉ cho phép khi: " + String.join(", ", allowed) + ".",
                details);
    }

    private CheckoutRequest pickRefundableCheckout(List<CheckoutRequest> requests) {
        return requests.stream()
                .filter(r -> r.getStatus() == CheckoutRequestStatus.COMPLETED)
                .findFirst()
                .or(() -> requests.stream()
                        .filter(r -> r.getStatus() == CheckoutRequestStatus.SETTLING)
                        .findFirst())
                .orElseThrow(() -> new BusinessException(
                        "REFUND_NOT_ALLOWED",
                        "Hợp đồng chưa ở bước quyết toán hoặc thanh lý nên chưa ghi nhận hoàn cọc được."));
    }

    private CheckoutSettlement createRefundSettlement(CheckoutRequest checkoutRequest, CheckoutRefundRequest request) {
        TenantContract contract = checkoutRequest.getTenantContract();
        BigDecimal deposit = contract != null && contract.getDeposit() != null
                ? contract.getDeposit()
                : BigDecimal.ZERO;
        BigDecimal refundAmount = request.getAmount() != null ? request.getAmount() : deposit;
        return CheckoutSettlement.builder()
                .checkoutRequest(checkoutRequest)
                .depositAmount(deposit)
                .unpaidTotal(BigDecimal.ZERO)
                .damageTotal(BigDecimal.ZERO)
                .adjustmentTotal(BigDecimal.ZERO)
                .refundAmount(refundAmount)
                .extraChargeAmount(BigDecimal.ZERO)
                .settlementInvoices(new ArrayList<>())
                .settlementAdjustments(new ArrayList<>())
                .build();
    }

    private void assertCanRecordDepositRefund(CheckoutRequest checkoutRequest) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        boolean admin = hasRole(user, Role.ROLE_ADMIN);
        boolean owner = hasRole(user, Role.ROLE_OWNER);
        if (!admin && !owner) {
            throw new AccessDeniedException("Chỉ chủ nhà hoặc admin được ghi nhận hoàn cọc");
        }
        if (admin) {
            return;
        }
        TenantContract contract = checkoutRequest.getTenantContract();
        if (contract == null || contract.getProperty() == null) {
            throw new ResourceNotFoundException("Hồ sơ trả phòng không gắn tài sản");
        }
        // Host portal hiện xem toàn bộ sổ cọc (chưa có FK owner→property).
        // Giữ chỗ này để sau này lọc theo tài sản host sở hữu cùng gốc host-403.
    }

    private static boolean hasRole(CustomUserDetails user, Role role) {
        return user.getAuthorities().stream().anyMatch(a -> role.name().equals(a.getAuthority()));
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private UUID getManagerId(TenantContract contract) {
        if (contract.getProperty() != null) {
            if (contract.getProperty().getOperationManagerId() != null) return contract.getProperty().getOperationManagerId();
            if (contract.getProperty().getOperationManagerId() != null) return contract.getProperty().getOperationManagerId();
        }
        return null;
    }

    private void syncCompensationInvoice(TenantContract contract, LocalDate moveOutDate, List<CheckoutDamageItem> damages, LocalDateTime checkoutCreatedAt) {
        BigDecimal totalDamage = damages == null ? BigDecimal.ZERO : damages.stream().map(CheckoutDamageItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        
        Optional<com.sep490.slms2026.entity.TenantInvoice> existingOpt = tenantInvoiceRepository.findByTenantContractId(contract.getId()).stream()
                .filter(inv -> inv.getInvoiceType() == com.sep490.slms2026.enums.TenantInvoiceType.COMPENSATION
                        && inv.getCreatedAt() != null && !inv.getCreatedAt().isBefore(checkoutCreatedAt))
                .findFirst();

        if (totalDamage.compareTo(BigDecimal.ZERO) <= 0) {
            existingOpt.ifPresent(inv -> {
                if (inv.getStatus() != com.sep490.slms2026.enums.TenantInvoiceStatus.PAID) {
                    inv.setStatus(com.sep490.slms2026.enums.TenantInvoiceStatus.CANCELLED);
                    tenantInvoiceRepository.save(inv);
                }
            });
            return;
        }

        com.sep490.slms2026.entity.TenantInvoice invoice = existingOpt.orElseGet(() -> {
            com.sep490.slms2026.entity.TenantInvoice newInvoice = com.sep490.slms2026.entity.TenantInvoice.createInvoice(
                    contract,
                    com.sep490.slms2026.enums.TenantInvoiceType.COMPENSATION,
                    finalPeriodLabel(moveOutDate));
            newInvoice.setCode("CMP-" + System.currentTimeMillis());
            newInvoice.setBillingMonth(moveOutDate.getMonthValue());
            newInvoice.setBillingYear(moveOutDate.getYear());
            return newInvoice;
        });
        
        invoice.setTotalAmount(totalDamage);
        invoice.setGrandTotal(totalDamage);
        if (invoice.getStatus() == null || invoice.getStatus() == com.sep490.slms2026.enums.TenantInvoiceStatus.CANCELLED) {
            invoice.setStatus(com.sep490.slms2026.enums.TenantInvoiceStatus.PENDING);
        }
        
        tenantInvoiceRepository.save(invoice);
    }

    private com.sep490.slms2026.entity.UtilityInvoice createFinalUtilityInvoice(
            TenantContract contract, com.sep490.slms2026.enums.UtilityType type,
            BigDecimal finalReading, String meterImageUrl, LocalDate moveOutDate, BigDecimal fromRequest) {

        BigDecimal prev = resolvePrevReadingForCheckout(contract, type);
        BigDecimal unitPrice = resolveFinalUnitPrice(contract, type, fromRequest);

        BigDecimal consumption = finalReading.subtract(prev);
        if (consumption.signum() < 0) {
            throw new BusinessException("METER_ROLLBACK",
                    "Chỉ số cuối nhỏ hơn chỉ số kỳ trước — kiểm tra lại số đọc.");
        }

        String billingPeriod = finalPeriodLabel(moveOutDate);
        
        com.sep490.slms2026.entity.UtilityInvoice invoice = utilityInvoiceRepository
                .findByTenantContractIdAndUtilityTypeAndBillingPeriod(
                        contract.getId(), type, billingPeriod)
                .orElseGet(com.sep490.slms2026.entity.UtilityInvoice::new);

        boolean isNew = invoice.getId() == null;
        BigDecimal oldAmount = isNew ? null : invoice.getAmount();

        invoice.setProperty(contract.getProperty());
        invoice.setRoom(contract.getRoom());
        invoice.setTenantContract(contract);
        invoice.setUtilityType(type);
        invoice.setBillingPeriod(billingPeriod);
        invoice.setPrevReading(prev);
        invoice.setNewReading(finalReading);
        invoice.setConsumption(consumption);
        if (unitPrice == null || unitPrice.signum() <= 0) {
            throw new BusinessException("NO_UTILITY_UNIT_PRICE",
                    "Nhà " + contract.getProperty().getPropertyName() + " chưa có đơn giá "
                    + (type == com.sep490.slms2026.enums.UtilityType.ELECTRIC ? "điện" : "nước")
                    + ". Nhập đơn giá cho nhà rồi mới chốt được biên bản trả phòng.");
        }
        invoice.setUnitPrice(unitPrice);
        invoice.setAmount(consumption.multiply(unitPrice));
        invoice.setMeterImageUrl(meterImageUrl);
        invoice.setStatus(com.sep490.slms2026.enums.UtilityInvoiceStatus.SENT);
        if (isNew) {
            invoice.setCreatedAt(LocalDateTime.now());
            invoice.setCreatedBy(SecurityUtils.requireCurrentUser().getId());
        }
        invoice.setSentAt(LocalDateTime.now());

        invoice = utilityInvoiceRepository.save(invoice);

        MeterReading existingReading;
        if (contract.getRoom() != null) {
            existingReading = meterReadingRepository.findTopByPropertyIdAndRoomIdAndUtilityTypeAndPeriodOrderByRecordedAtDesc(
                    contract.getProperty().getId(), contract.getRoom().getId(), type, billingPeriod).orElseGet(MeterReading::new);
        } else {
            existingReading = meterReadingRepository.findTopByPropertyIdAndRoomIsNullAndUtilityTypeAndPeriodOrderByRecordedAtDesc(
                    contract.getProperty().getId(), type, billingPeriod).orElseGet(MeterReading::new);
        }

        existingReading.setProperty(contract.getProperty());
        existingReading.setRoom(contract.getRoom());
        existingReading.setUtilityType(type);
        existingReading.setPeriod(billingPeriod);
        existingReading.setReading(finalReading);
        existingReading.setImageUrl(meterImageUrl);
        existingReading.setRecordedAt(LocalDateTime.now());
        existingReading.setRecordedBy(SecurityUtils.requireCurrentUser().getId());
        meterReadingRepository.save(existingReading);

        com.sep490.slms2026.entity.TenantInvoice tenantInvoice = tenantBillingService.createFromUtilityInvoice(invoice, contract);

        Map<String, Object> data = new HashMap<>();
        data.put("screen", "InvoiceList");
        data.put("type", "UTILITY_INVOICE_CREATED");
        Map<String, Object> params = new HashMap<>();
        params.put("invoiceId", tenantInvoice.getId());
        data.put("params", params);

        String typeStr = type == com.sep490.slms2026.enums.UtilityType.ELECTRIC ? "Điện" : "Nước";
        String title = "Hoá đơn " + typeStr + " chốt trả phòng";
        String content = String.format("Quản lý vừa chốt số và phát hành hoá đơn %s kỳ %s. Số tiền: %,dđ.",
                typeStr, billingPeriod, invoice.getAmount().longValue());

        if (isNew || oldAmount == null || oldAmount.compareTo(invoice.getAmount()) != 0) {
            if (contract.getTenant() != null && contract.getTenant().getUser() != null) {
                String finalTitle = isNew ? title : "Cập nhật " + title.replace("Hoá đơn", "hoá đơn");
                sendNotification(contract.getTenant().getUser().getId(), "UTILITY_INVOICE_CREATED", finalTitle, content, data);
            }
        }

        return invoice;
    }

    private BigDecimal resolvePrevReadingForCheckout(TenantContract contract, com.sep490.slms2026.enums.UtilityType type) {
        Optional<com.sep490.slms2026.entity.UtilityInvoice> last = contract.getRoom() != null
                ? utilityInvoiceRepository.findTopByPropertyIdAndRoomIdAndUtilityTypeAndBillingPeriodNotLikeOrderByCreatedAtDesc(
                        contract.getProperty().getId(), contract.getRoom().getId(), type, "%chốt trả phòng%")
                : utilityInvoiceRepository.findTopByPropertyIdAndRoomIsNullAndUtilityTypeAndBillingPeriodNotLikeOrderByCreatedAtDesc(
                        contract.getProperty().getId(), type, "%chốt trả phòng%");

        if (last.isPresent() && contract.getId().equals(last.get().getTenantContract().getId())) {
            return last.get().getNewReading();
        }

        BigDecimal moveIn = type == com.sep490.slms2026.enums.UtilityType.ELECTRIC
                ? contract.getInitialElectricReading()
                : contract.getInitialWaterReading();

        if (moveIn == null) {
            throw new BusinessException("NO_BASELINE_READING",
                    "Hợp đồng không có chỉ số đầu kỳ — không tính được tiền điện/nước.");
        }
        return moveIn;
    }

    private BigDecimal resolveFinalUnitPrice(TenantContract contract, com.sep490.slms2026.enums.UtilityType type, BigDecimal fromRequest) {
        if (fromRequest != null && fromRequest.signum() > 0) {
            BigDecimal registered = type == com.sep490.slms2026.enums.UtilityType.ELECTRIC
                    ? contract.getProperty().getElectricityUnitPrice()
                    : contract.getProperty().getWaterUnitPrice();
            if (registered == null || registered.signum() <= 0) {
                if (type == com.sep490.slms2026.enums.UtilityType.ELECTRIC) {
                    contract.getProperty().setElectricityUnitPrice(fromRequest);
                } else {
                    contract.getProperty().setWaterUnitPrice(fromRequest);
                }
            }
            return fromRequest;
        }

        Optional<com.sep490.slms2026.entity.UtilityInvoice> last = contract.getRoom() != null
                ? utilityInvoiceRepository.findTopByPropertyIdAndRoomIdAndUtilityTypeAndBillingPeriodNotLikeOrderByCreatedAtDesc(
                        contract.getProperty().getId(), contract.getRoom().getId(), type, "%chốt trả phòng%")
                : utilityInvoiceRepository.findTopByPropertyIdAndRoomIsNullAndUtilityTypeAndBillingPeriodNotLikeOrderByCreatedAtDesc(
                        contract.getProperty().getId(), type, "%chốt trả phòng%");

        if (last.isPresent() && contract.getId().equals(last.get().getTenantContract().getId())
                && last.get().getUnitPrice() != null) {
            return last.get().getUnitPrice();
        }

        BigDecimal registered = type == com.sep490.slms2026.enums.UtilityType.ELECTRIC
                ? contract.getProperty().getElectricityUnitPrice()
                : contract.getProperty().getWaterUnitPrice();
        if (registered != null && registered.signum() > 0) {
            return registered;
        }

        throw new BusinessException("NO_UTILITY_UNIT_PRICE",
                "Nhà " + contract.getProperty().getPropertyName() + " chưa có đơn giá "
                + (type == com.sep490.slms2026.enums.UtilityType.ELECTRIC ? "điện" : "nước")
                + ". Nhập đơn giá cho nhà rồi mới chốt được biên bản trả phòng.");
    }

    private String finalPeriodLabel(LocalDate moveOutDate) {
        return String.format("%02d/%d (01/%02d–%02d/%02d, chốt trả phòng)", 
                moveOutDate.getMonthValue(), moveOutDate.getYear(),
                moveOutDate.getMonthValue(), moveOutDate.getDayOfMonth(), moveOutDate.getMonthValue());
    }

    private String hashString(String input) {
        if (input == null) return null;
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (int i = 0; i < encodedhash.length; i++) {
                String hex = Integer.toHexString(0xff & encodedhash[i]);
                if(hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String hashFileContent(String fileUrl) {
        if (fileUrl == null) return null;
        try {
            java.net.URL url = new java.net.URL(fileUrl);
            try (java.io.InputStream is = url.openStream()) {
                java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
                byte[] encodedhash = digest.digest();
                StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
                for (byte b : encodedhash) {
                    String hex = Integer.toHexString(0xff & b);
                    if (hex.length() == 1) {
                        hexString.append('0');
                    }
                    hexString.append(hex);
                }
                return hexString.toString();
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(CheckoutProcessServiceImpl.class).warn("Failed to hash file content: " + fileUrl, e);
            return null;
        }
    }
}

