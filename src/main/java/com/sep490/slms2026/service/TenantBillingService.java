package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.CreateRentInvoiceRequest;
import com.sep490.slms2026.dto.response.TenantInvoiceResponse;
import com.sep490.slms2026.dto.response.TenantPaymentResponse;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.entity.TenantInvoice;
import com.sep490.slms2026.entity.TenantPaymentClaim;
import com.sep490.slms2026.entity.UtilityInvoice;

import java.util.List;
import java.util.UUID;

public interface TenantBillingService {

    List<TenantInvoiceResponse> listInvoices(UUID tenantUserId, String status, String type);

    TenantInvoiceResponse getInvoice(UUID tenantUserId, Long invoiceId);

    TenantInvoiceResponse createPayment(UUID tenantUserId, Long invoiceId);

    TenantInvoiceResponse checkPayment(UUID tenantUserId, Long invoiceId);

    List<TenantPaymentResponse> listPayments(UUID tenantUserId);

    void markInvoicePaidByPayosOrderCode(Long payosOrderCode);

    TenantInvoice createFromUtilityInvoice(UtilityInvoice utilityInvoice, TenantContract contract);

    TenantInvoiceResponse createManagerRentInvoice(Long propertyId, Long roomId, CreateRentInvoiceRequest request);

    void approvePaymentClaim(TenantPaymentClaim claim, UUID verifiedBy);

    void createBankTransferClaim(TenantInvoice invoice, String transferContent);
}
