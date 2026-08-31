package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.ApproveCheckoutRequest;
import com.sep490.slms2026.dto.request.CompleteCheckoutRequest;
import com.sep490.slms2026.dto.request.CreateCheckoutRequest;
import com.sep490.slms2026.dto.request.RejectCheckoutRequest;
import com.sep490.slms2026.dto.response.CheckoutRequestResponse;
import java.util.List;
import java.util.UUID;

public interface TenantCheckoutService {

    CheckoutRequestResponse createRequest(UUID tenantUserId, CreateCheckoutRequest request);

    List<CheckoutRequestResponse> listRequests(UUID tenantUserId);

    CheckoutRequestResponse getRequest(UUID tenantUserId, Long requestId);

    /** Tenant hủy yêu cầu đang PENDING. */
    CheckoutRequestResponse cancelRequest(UUID tenantUserId, Long requestId);

    List<CheckoutRequestResponse> listRequestsForManager(String status);

    CheckoutRequestResponse createRequestForManager(UUID managerId, CreateCheckoutRequest request);

    CheckoutRequestResponse getRequestForManager(Long requestId);

    CheckoutRequestResponse approveRequest(Long requestId, UUID managerUserId, ApproveCheckoutRequest request);

    CheckoutRequestResponse rejectRequest(Long requestId, UUID managerUserId, RejectCheckoutRequest request);

    CheckoutRequestResponse completeRequest(Long requestId, UUID managerUserId, CompleteCheckoutRequest request);

    CheckoutRequestResponse confirmRefund(Long requestId, UUID tenantUserId);

    CheckoutRequestResponse disputeRefund(Long requestId, UUID tenantUserId, com.sep490.slms2026.dto.request.DisputeRefundRequest request);

    CheckoutRequestResponse forceSettle(Long requestId, UUID adminUserId, com.sep490.slms2026.dto.request.ForceSettleRequest request);

    CheckoutRequestResponse resolveRefundDispute(Long requestId, UUID adminUserId, com.sep490.slms2026.dto.request.ResolveRefundDisputeRequest request);

    void processExpiredContracts();

    void processContractExpirationReminders();
}
