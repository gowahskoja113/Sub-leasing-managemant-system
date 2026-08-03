package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.CheckoutDisputeRequest;
import com.sep490.slms2026.dto.request.CheckoutInspectionRequest;
import com.sep490.slms2026.dto.request.CheckoutRefundRequest;
import com.sep490.slms2026.dto.response.CheckoutInspectionResponse;
import com.sep490.slms2026.dto.response.CheckoutSettlementResponse;
import com.sep490.slms2026.entity.*;
import com.sep490.slms2026.enums.CheckoutRequestStatus;
import com.sep490.slms2026.enums.InvoiceStatus;
import com.sep490.slms2026.repository.CheckoutInspectionRepository;
import com.sep490.slms2026.repository.CheckoutRequestRepository;
import com.sep490.slms2026.repository.CheckoutSettlementRepository;
import com.sep490.slms2026.repository.InvoiceRepository;
import com.sep490.slms2026.service.CheckoutProcessService;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CheckoutProcessServiceImpl implements CheckoutProcessService {

    private final CheckoutRequestRepository checkoutRequestRepository;
    private final CheckoutInspectionRepository checkoutInspectionRepository;
    private final CheckoutSettlementRepository checkoutSettlementRepository;
    private final InvoiceRepository invoiceRepository;

    @Override
    @Transactional
    public void saveInspection(Long checkoutRequestId, CheckoutInspectionRequest request) {
        CheckoutRequest checkoutRequest = checkoutRequestRepository.findById(checkoutRequestId)
                .orElseThrow(() -> new RuntimeException("Checkout request not found"));

        if (checkoutRequest.getStatus() != CheckoutRequestStatus.APPROVED && 
            checkoutRequest.getStatus() != CheckoutRequestStatus.INSPECTING) {
            throw new RuntimeException("Invalid status for inspection");
        }

        CheckoutInspection inspection = checkoutInspectionRepository.findByCheckoutRequestId(checkoutRequestId)
                .orElse(new CheckoutInspection());

        inspection.setCheckoutRequest(checkoutRequest);
        inspection.setRoomConditionNote(request.getRoomConditionNote());
        inspection.setElectricityFinalReading(request.getElectricityFinalReading());
        inspection.setWaterFinalReading(request.getWaterFinalReading());
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
        
        if (checkoutRequest.getStatus() != CheckoutRequestStatus.INSPECTING) {
            checkoutRequest.setStatus(CheckoutRequestStatus.INSPECTING);
            checkoutRequestRepository.save(checkoutRequest);
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
                .waterFinalReading(inspection.getWaterFinalReading())
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

        List<Invoice> unpaidInvoicesEntity = invoiceRepository.findByTenantIdAndStatusAndDeletedFalse(
                checkoutRequest.getTenantUserId(), InvoiceStatus.UNPAID);

        BigDecimal unpaidTotal = unpaidInvoicesEntity.stream()
                .map(Invoice::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal damageTotal = inspection.getDamages().stream()
                .map(CheckoutDamageItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate adjustments, e.g. for partial month rent if needed.
        // For simplicity, we just use 0 here, or one could calculate it based on dates.
        BigDecimal adjustmentTotal = BigDecimal.ZERO;
        List<CheckoutSettlementResponse.AdjustmentResponse> adjustments = new ArrayList<>();

        BigDecimal finalAmount = deposit.subtract(unpaidTotal).subtract(damageTotal).add(adjustmentTotal);

        BigDecimal refundAmount = BigDecimal.ZERO;
        BigDecimal extraChargeAmount = BigDecimal.ZERO;

        if (finalAmount.compareTo(BigDecimal.ZERO) > 0) {
            refundAmount = finalAmount;
        } else if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
            extraChargeAmount = finalAmount.abs();
        }

        return CheckoutSettlementResponse.builder()
                .depositAmount(deposit)
                .unpaidInvoices(unpaidInvoicesEntity.stream().map(inv -> CheckoutSettlementResponse.InvoiceResponse.builder()
                        .id(inv.getId())
                        .code(inv.getMonth()) // just using month as code for now if code doesn't exist
                        .type("RENT") // simplified
                        .amount(inv.getAmount())
                        .build()).collect(Collectors.toList()))
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
        
        // TODO: Save dispute reason, photos and Notify Host/Manager

        checkoutRequestRepository.save(checkoutRequest);
    }

    @Override
    @Transactional
    public void refund(Long checkoutRequestId, CheckoutRefundRequest request) {
        CheckoutRequest checkoutRequest = checkoutRequestRepository.findById(checkoutRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Checkout request not found"));

        if (checkoutRequest.getStatus() != CheckoutRequestStatus.SETTLING) {
            throw new BusinessException("REFUND_NOT_ALLOWED", "Hồ sơ chưa ở bước quyết toán nên chưa ghi nhận hoàn cọc được.");
        }

        CheckoutSettlement settlement = checkoutSettlementRepository.findByCheckoutRequestId(checkoutRequestId)
                .orElseThrow(() -> new RuntimeException("Settlement not found"));

        settlement.setRefundMethod(request.getMethod());
        settlement.setRefundProofUrl(request.getProofUrl());
        settlement.setRefundPaidAt(request.getPaidAt() != null ? request.getPaidAt().atStartOfDay() : null);
        settlement.setRefundNote(request.getNote());

        checkoutSettlementRepository.save(settlement);
    }
}
