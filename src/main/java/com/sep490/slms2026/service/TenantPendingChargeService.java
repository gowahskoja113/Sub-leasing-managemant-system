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
     * Hạn thanh toán mặc định {@link #MAINTENANCE_CHARGE_DUE_DAYS} ngày.
     */
    TenantInvoiceResponse createAndIssueMaintenanceCharge(
            TenantContract contract,
            BigDecimal amount,
            Long maintenanceRequestId,
            String note);

    /** Số ngày hạn thanh toán hoá đơn bồi thường bảo trì (tenant tự trả). */
    int MAINTENANCE_CHARGE_DUE_DAYS = 5;
}
