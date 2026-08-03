package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.CheckoutDisputeRequest;
import com.sep490.slms2026.dto.request.CheckoutInspectionRequest;
import com.sep490.slms2026.dto.request.CheckoutRefundRequest;
import com.sep490.slms2026.dto.response.CheckoutInspectionResponse;
import com.sep490.slms2026.dto.response.CheckoutSettlementResponse;

import java.util.UUID;

public interface CheckoutProcessService {
    void saveInspection(Long checkoutRequestId, CheckoutInspectionRequest request);
    CheckoutInspectionResponse getInspection(Long checkoutRequestId);
    CheckoutSettlementResponse getSettlement(Long checkoutRequestId);
    void submitSettlement(Long checkoutRequestId);
    void acceptSettlement(Long checkoutRequestId, UUID tenantId);
    void disputeSettlement(Long checkoutRequestId, UUID tenantId, CheckoutDisputeRequest request);
    void refund(Long checkoutRequestId, CheckoutRefundRequest request);
}
