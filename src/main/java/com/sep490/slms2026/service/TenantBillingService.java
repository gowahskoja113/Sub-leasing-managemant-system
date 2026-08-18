package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.billing.InvoicePaymentContext;
import com.sep490.slms2026.dto.request.CreateRentInvoiceRequest;
import com.sep490.slms2026.dto.request.ManagerPaymentQrRequest;
import com.sep490.slms2026.dto.response.ManagerPaymentQrResponse;
import com.sep490.slms2026.dto.response.TenantInvoiceResponse;
import com.sep490.slms2026.dto.response.TenantPaymentResponse;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.entity.TenantInvoice;
import com.sep490.slms2026.entity.TenantPaymentClaim;
import com.sep490.slms2026.entity.UtilityInvoice;

import java.time.LocalDateTime;
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

    List<TenantInvoiceResponse> getRentInvoicesForProperty(Long propertyId, String month);

    void generateProratedRentForNewContract(TenantContract contract);

    /**
     * Ghi nhận tiền nhà chu kỳ đầu đã thu gộp QR onboard (PAID, không tạo TenantPayment riêng).
     */
    void recordPaidFirstRentFromOnboard(TenantContract contract, Long payosOrderCode, String method,
                                        LocalDateTime paidAt);

    ManagerPaymentQrResponse createManagerPaymentQr(UUID managerUserId, Long invoiceId,
                                                    ManagerPaymentQrRequest request);

    /** Gọi từ listener AFTER_COMMIT: WebSocket (dữ liệu đã commit, FE nạp lại khớp ngay). */
    void handleInvoicePaidAfterCommit(Long invoiceId, InvoicePaymentContext context);

    /** Ghi notification + push sau commit — TX riêng, gọi từ listener. */
    void sendPaymentNotificationsAfterCommit(Long invoiceId, InvoicePaymentContext context);
}
