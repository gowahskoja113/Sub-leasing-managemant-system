package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.response.ManagerInvoiceResponse;
import com.sep490.slms2026.dto.response.ManagerPaymentResponse;
import com.sep490.slms2026.dto.response.RentInvoiceSummaryResponse;

import java.util.List;
import java.util.UUID;

public interface ManagerBillingService {

    List<RentInvoiceSummaryResponse> listRentInvoices(Long propertyId, String month);

    List<ManagerInvoiceResponse> listInvoices(UUID managerUserId, boolean isAdmin,
                                              String period, String status, String type);

    List<ManagerPaymentResponse> listPayments(UUID managerUserId, boolean isAdmin, String status);

    ManagerPaymentResponse verifyPayment(UUID managerUserId, boolean isAdmin, Long claimId);

    ManagerPaymentResponse rejectPayment(UUID managerUserId, boolean isAdmin, Long claimId, String reason);
}
