package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.response.ManagerInvoiceResponse;
import com.sep490.slms2026.dto.response.ManagerPaymentHistoryResponse;
import com.sep490.slms2026.dto.response.ManagerPaymentResponse;
import com.sep490.slms2026.dto.response.RentInvoiceSummaryResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.sep490.slms2026.dto.response.ManagerDepositDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ManagerBillingService {

    List<RentInvoiceSummaryResponse> listRentInvoices(Long propertyId, String month);

    List<ManagerInvoiceResponse> listInvoices(UUID managerUserId, boolean isAdmin,
                                              Long propertyId, String period, String status, String type);

    /** Chi tiết 1 hoá đơn kèm {@code items[]} (onboard: tiền nhà + cọc). */
    ManagerInvoiceResponse getInvoice(UUID managerUserId, boolean isAdmin, Long invoiceId);

    List<ManagerPaymentResponse> listPayments(UUID managerUserId, boolean isAdmin,
                                              Long propertyId, String status);

    /** Lịch sử thu thật từ {@code tenant_payments} — khác hàng chờ đối soát {@link #listPayments}. */
    Page<ManagerPaymentHistoryResponse> listPaymentHistory(UUID managerUserId, boolean isAdmin,
                                                           Long propertyId, Long contractId,
                                                           LocalDate from, LocalDate to,
                                                           Pageable pageable);

    ManagerPaymentResponse verifyPayment(UUID managerUserId, boolean isAdmin, Long claimId);

    ManagerPaymentResponse rejectPayment(UUID managerUserId, boolean isAdmin, Long claimId, String reason);
    
    Page<ManagerDepositDto> getManagerDeposits(UUID managerUserId, String status, Pageable pageable);
}
