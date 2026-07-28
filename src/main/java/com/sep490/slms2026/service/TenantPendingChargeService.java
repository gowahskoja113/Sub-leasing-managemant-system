package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.IssueInvoiceRequest;
import com.sep490.slms2026.dto.response.TenantInvoiceResponse;
import com.sep490.slms2026.dto.response.TenantPendingChargeResponse;
import com.sep490.slms2026.entity.TenantContract;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface TenantPendingChargeService {
    List<TenantPendingChargeResponse> getPendingChargesForManager(UUID managerId, boolean isAdmin, Long propertyId, String status);
    List<TenantPendingChargeResponse> getPendingChargesForTenant(UUID tenantId);
    TenantInvoiceResponse issueInvoiceFromCharges(Long contractId, IssueInvoiceRequest request);

    /**
     * Tạo 1 TenantPendingCharge gắn ticket bảo trì rồi issue hoá đơn MAINTENANCE + QR PayOS.
     */
    TenantInvoiceResponse createAndIssueMaintenanceCharge(
            TenantContract contract,
            BigDecimal amount,
            Long maintenanceRequestId,
            String note);
}
