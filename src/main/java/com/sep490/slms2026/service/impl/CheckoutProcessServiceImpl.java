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
                        .label(d.getLabel())
                        .amount(d.getAmount())
                        .note(d.getNote())
                        .photos(d.getPhotos() != null ? d.getPhotos() : new ArrayList<>())
                        .build();
                inspection.getDamages().add(item);
            }
        }

        checkoutInspectionRepository.save(inspection);

        TenantContract contract = checkoutRequest.getTenantContract();
        if (contract != null && checkoutRequest.getExpectedMoveOutDate() != null) {
            LocalDate moveOutDate = checkoutRequest.getExpectedMoveOutDate();
            if (checkoutRequest.getCompletedAt() != null) {
                moveOutDate = checkoutRequest.getCompletedAt().toLocalDate();
            }
            if (request.getElectricityFinalReading() != null) {
                createFinalUtilityInvoice(contract, com.sep490.slms2026.enums.UtilityType.ELECTRIC, 
                        BigDecimal.valueOf(request.getElectricityFinalReading()), 
                        request.getElectricMeterImageUrl(), moveOutDate);
            }
            if (request.getWaterFinalReading() != null) {
                createFinalUtilityInvoice(contract, com.sep490.slms2026.enums.UtilityType.WATER, 
                        BigDecimal.valueOf(request.getWaterFinalReading()), 
                        request.getWaterMeterImageUrl(), moveOutDate);
            }
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
                .orElseThrow(() -> new RuntimeException("Checkout request not found"));

        CheckoutInspection inspection = checkoutInspectionRepository.findByCheckoutRequestId(checkoutRequestId)
                .orElseThrow(() -> new RuntimeException("Inspection not found. Cannot calculate settlement."));

        BigDecimal deposit = checkoutRequest.getTenantContract().getDeposit();
        if (deposit == null) deposit = BigDecimal.ZERO;

        TenantContract contract = checkoutRequest.getTenantContract();

        List<com.sep490.slms2026.entity.TenantInvoice> unpaidInvoices = tenantInvoiceRepository
                .findByTenantContractIdAndStatusNotIn(contract.getId(), List.of(com.sep490.slms2026.enums.TenantInvoiceStatus.PAID, com.sep490.slms2026.enums.TenantInvoiceStatus.CANCELLED));
        BigDecimal unpaidTotal = unpaidInvoices.stream()
                .map(com.sep490.slms2026.entity.TenantInvoice::getGrandTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

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

        BigDecimal finalAmount = deposit.subtract(unpaidTotal).subtract(damageTotal).add(adjustmentTotal);

        BigDecimal refundAmount = BigDecimal.ZERO;
        BigDecimal extraChargeAmount = BigDecimal.ZERO;

        if (finalAmount.compareTo(BigDecimal.ZERO) > 0) {
            refundAmount = finalAmount;
        } else if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
            extraChargeAmount = finalAmount.abs();
        }

        Optional<CheckoutSettlement> savedOpt = checkoutSettlementRepository.findByCheckoutRequestId(checkoutRequestId);
        if (savedOpt.isPresent()) {
            CheckoutSettlement saved = savedOpt.get();
            boolean extraChargePaid = false;
            if (saved.getExtraChargeInvoiceId() != null) {
                Invoice inv = invoiceRepository.findById(saved.getExtraChargeInvoiceId()).orElse(null);
                if (inv != null && inv.getStatus() == InvoiceStatus.PAID) {
                    extraChargePaid = true;
                }
            }
            return CheckoutSettlementResponse.builder()
                    .depositAmount(saved.getDepositAmount())
                    .unpaidInvoices(saved.getSettlementInvoices().stream().map(inv -> CheckoutSettlementResponse.InvoiceResponse.builder()
                            .id(inv.getInvoiceId())
                            .code(inv.getInvoiceCode())
                            .type(inv.getInvoiceType())
                            .amount(inv.getAmount())
                            .build()).collect(Collectors.toList()))
                    .unpaidTotal(saved.getUnpaidTotal())
                    .damages(inspection.getDamages().stream().map(d -> CheckoutSettlementResponse.DamageResponse.builder()
                            .label(d.getLabel())
                            .amount(d.getAmount())
                            .build()).collect(Collectors.toList()))
                    .damageTotal(saved.getDamageTotal())
                    .adjustments(saved.getSettlementAdjustments().stream().map(adj -> CheckoutSettlementResponse.AdjustmentResponse.builder()
                            .label(adj.getLabel())
                            .amount(adj.getAmount())
                            .build()).collect(Collectors.toList()))
                    .adjustmentTotal(saved.getAdjustmentTotal())
                    .refundAmount(saved.getRefundAmount())
                    .extraChargeAmount(saved.getExtraChargeAmount())
                    .extraChargeInvoiceId(saved.getExtraChargeInvoiceId())
                    .refundProofUrl(saved.getRefundProofUrl())
                    .refundedAt(saved.getRefundPaidAt() != null ? saved.getRefundPaidAt().toLocalDate() : null)
                    .extraChargePaid(extraChargePaid)
                    .build();
        }

        List<CheckoutSettlementResponse.InvoiceResponse> unpaidInvoiceResponses = new ArrayList<>();
        for (var inv : unpaidInvoices) {
            unpaidInvoiceResponses.add(CheckoutSettlementResponse.InvoiceResponse.builder()
                    .id(inv.getId())
                    .code(inv.getCode() != null ? inv.getCode() : "RENT")
                    .type(inv.getInvoiceType() != null ? inv.getInvoiceType().name() : "RENT")
                    .amount(inv.getGrandTotal())
                    .build());
        }

        return CheckoutSettlementResponse.builder()
                .depositAmount(deposit)
                .unpaidInvoices(unpaidInvoiceResponses)
                .unpaidTotal(unpaidTotal)
                .damages(inspection.getDamages().stream().map(d -> CheckoutSettlementResponse.DamageResponse.builder()
                        .label(d.getLabel())
                        .amount(d.getAmount())
                        .build()).collect(Collectors.toList()))
                .damageTotal(damageTotal)
                .adjustments(adjustments)
                .adjustmentTotal(adjustmentTotal)
                .refundAmount(refundAmount)
                .extraChargeAmount(extraChargeAmount)
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
        settlement.setUnpaidTotal(settlementData.getUnpaidTotal());
        settlement.setDamageTotal(settlementData.getDamageTotal());
        settlement.setAdjustmentTotal(settlementData.getAdjustmentTotal());
        settlement.setRefundAmount(settlementData.getRefundAmount());
        settlement.setExtraChargeAmount(settlementData.getExtraChargeAmount());

        if (settlement.getSettlementInvoices() != null) {
            settlement.getSettlementInvoices().clear();
        } else {
            settlement.setSettlementInvoices(new ArrayList<>());
        }

        if (settlementData.getUnpaidInvoices() != null) {
            for (var inv : settlementData.getUnpaidInvoices()) {
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
                    "Quyết toán không còn số tiền hoàn cọc (cọc đã trừ hết hoặc khách còn phải trả thêm).");
        }

        if (request.getPaidAt() == null) {
            throw new BusinessException("REFUND_INVALID", "Ngày chuyển hoàn cọc không được để trống");
        }
        if (request.getAmount() != null) {
            settlement.setRefundAmount(request.getAmount());
        }
        settlement.setRefundMethod(request.getMethod());
        settlement.setRefundProofUrl(request.getProofUrl());
        settlement.setRefundPaidAt(request.getPaidAt().atStartOfDay());
        settlement.setRefundNote(request.getNote());

        checkoutSettlementRepository.save(settlement);

        Map<String, Object> data = new HashMap<>();
        data.put("screen", "CheckoutDetail");
        Map<String, Object> params = new HashMap<>();
        params.put("requestId", checkoutRequest.getId());
        data.put("params", params);

        String title = "Xác nhận hoàn cọc";
        String content = "Đã ghi nhận hoàn tiền cọc cho bạn.";
        sendNotification(checkoutRequest.getTenantUserId(), "CHECKOUT_REFUNDED", title, content, data);

        TenantContract contract = checkoutRequest.getTenantContract();
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
            if (contract.getProperty().getManagedBy() != null) return contract.getProperty().getManagedBy();
            if (contract.getProperty().getOperationManagerId() != null) return contract.getProperty().getOperationManagerId();
        }
        return null;
    }

    private com.sep490.slms2026.entity.UtilityInvoice createFinalUtilityInvoice(
            TenantContract contract, com.sep490.slms2026.enums.UtilityType type,
            BigDecimal finalReading, String meterImageUrl, LocalDate moveOutDate) {

        BigDecimal prev = resolvePrevReading(contract, type);
        BigDecimal unitPrice = resolveFinalUnitPrice(contract, type);

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

        invoice.setProperty(contract.getProperty());
        invoice.setRoom(contract.getRoom());
        invoice.setTenantContract(contract);
        invoice.setUtilityType(type);
        invoice.setBillingPeriod(billingPeriod);
        invoice.setPrevReading(prev);
        invoice.setNewReading(finalReading);
        invoice.setConsumption(consumption);
        invoice.setUnitPrice(unitPrice);
        invoice.setAmount(consumption.multiply(unitPrice));
        invoice.setMeterImageUrl(meterImageUrl);
        invoice.setStatus(com.sep490.slms2026.enums.UtilityInvoiceStatus.SENT);
        if (invoice.getId() == null) {
            invoice.setCreatedAt(LocalDateTime.now());
            invoice.setCreatedBy(SecurityUtils.requireCurrentUser().getId());
        }
        invoice.setSentAt(LocalDateTime.now());

        invoice = utilityInvoiceRepository.save(invoice);

        meterReadingRepository.save(MeterReading.builder()
                .property(contract.getProperty())
                .room(contract.getRoom())
                .utilityType(type)
                .period(billingPeriod)
                .reading(finalReading)
                .imageUrl(meterImageUrl)
                .recordedAt(LocalDateTime.now())
                .recordedBy(SecurityUtils.requireCurrentUser().getId())
                .build());

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

        if (contract.getTenant() != null && contract.getTenant().getUser() != null) {
            sendNotification(contract.getTenant().getUser().getId(), "UTILITY_INVOICE_CREATED", title, content, data);
        }

        return invoice;
    }

    private BigDecimal resolvePrevReading(TenantContract contract, com.sep490.slms2026.enums.UtilityType type) {
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

    private BigDecimal resolveFinalUnitPrice(TenantContract contract, com.sep490.slms2026.enums.UtilityType type) {
        if (Boolean.TRUE.equals(contract.getProperty().getWholeHouse())) {
            Optional<com.sep490.slms2026.entity.UtilityInvoice> last = contract.getRoom() != null
                    ? utilityInvoiceRepository.findTopByPropertyIdAndRoomIdAndUtilityTypeAndBillingPeriodNotLikeOrderByCreatedAtDesc(
                            contract.getProperty().getId(), contract.getRoom().getId(), type, "%chốt trả phòng%")
                    : utilityInvoiceRepository.findTopByPropertyIdAndRoomIsNullAndUtilityTypeAndBillingPeriodNotLikeOrderByCreatedAtDesc(
                            contract.getProperty().getId(), type, "%chốt trả phòng%");
            if (last.isPresent() && contract.getId().equals(last.get().getTenantContract().getId())) {
                return last.get().getUnitPrice();
            }
        }
        return type == com.sep490.slms2026.enums.UtilityType.ELECTRIC
                ? contract.getProperty().getElectricityUnitPrice()
                : contract.getProperty().getWaterUnitPrice();
    }

    private String finalPeriodLabel(LocalDate moveOutDate) {
        return String.format("%02d/%d (01/%02d–%02d/%02d, chốt trả phòng)", 
                moveOutDate.getMonthValue(), moveOutDate.getYear(),
                moveOutDate.getMonthValue(), moveOutDate.getDayOfMonth(), moveOutDate.getMonthValue());
    }
}
