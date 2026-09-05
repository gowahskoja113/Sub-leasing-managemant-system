package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.CreateInvoiceDisputeRequest;
import com.sep490.slms2026.dto.request.ResolveInvoiceDisputeRequest;
import com.sep490.slms2026.dto.response.AdminInvoiceDisputeResponse;
import com.sep490.slms2026.dto.response.InvoiceDisputeResponse;
import com.sep490.slms2026.dto.response.TenantInvoiceResponse;
import com.sep490.slms2026.entity.TenantInvoice;
import com.sep490.slms2026.entity.UtilityInvoice;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface InvoiceDisputeService {

    InvoiceDisputeResponse create(UUID tenantUserId, Long tenantInvoiceId, CreateInvoiceDisputeRequest request);

    InvoiceDisputeResponse withdraw(UUID tenantUserId, Long tenantInvoiceId);

    List<AdminInvoiceDisputeResponse> listForAdmin();

    AdminInvoiceDisputeResponse resolve(UUID adminUserId, Long disputeId, ResolveInvoiceDisputeRequest request);

    void enrichTenantInvoice(TenantInvoice invoice, TenantInvoiceResponse response);

    Set<Long> openDisputeTenantInvoiceIds();

    void attachReplacementIfPresent(UtilityInvoice newUtilityInvoice);
}
