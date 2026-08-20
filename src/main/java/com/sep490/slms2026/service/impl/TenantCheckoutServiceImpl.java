package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.ApproveCheckoutRequest;
import com.sep490.slms2026.dto.request.CompleteCheckoutRequest;
import com.sep490.slms2026.dto.request.CreateCheckoutRequest;
import com.sep490.slms2026.dto.request.RejectCheckoutRequest;
import com.sep490.slms2026.dto.request.TerminateContractRequest;
import com.sep490.slms2026.dto.response.CheckoutRequestResponse;
import com.sep490.slms2026.entity.CheckoutRequest;
import com.sep490.slms2026.entity.Tenant;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.enums.CheckoutRequestStatus;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.enums.ContractTerminationType;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.CheckoutRequestRepository;
import com.sep490.slms2026.repository.TenantContractRepository;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.service.TenantCheckoutService;
import com.sep490.slms2026.service.TenantOnboardingService;
import com.sep490.slms2026.entity.Notification;
import com.sep490.slms2026.repository.NotificationRepository;
import com.sep490.slms2026.service.PushNotificationService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TenantCheckoutServiceImpl implements TenantCheckoutService {

    private static final List<CheckoutRequestStatus> OPEN_STATUSES =
            List.of(CheckoutRequestStatus.PENDING, CheckoutRequestStatus.APPROVED);

    private final CheckoutRequestRepository checkoutRequestRepository;
    private final TenantContractRepository tenantContractRepository;
    private final UserRepository userRepository;
    private final TenantOnboardingService tenantOnboardingService;
    private final NotificationRepository notificationRepository;
    private final PushNotificationService pushNotificationService;
    private final com.sep490.slms2026.repository.TenantInvoiceRepository tenantInvoiceRepository;
    private final com.sep490.slms2026.repository.CheckoutSettlementRepository checkoutSettlementRepository;

    @Override
    @Transactional
    public CheckoutRequestResponse createRequest(UUID tenantUserId, CreateCheckoutRequest request) {
        TenantContract contract = tenantContractRepository.findById(request.getContractId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy hợp đồng ID=" + request.getContractId()));

        if (contract.getTenant() == null || !contract.getTenant().getId().equals(tenantUserId)) {
            throw new BusinessException("Hợp đồng không thuộc tài khoản của bạn");
        }
        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw new BusinessException("Chỉ có thể yêu cầu trả phòng với hợp đồng đang hiệu lực");
        }
        if (request.getExpectedMoveOutDate().isBefore(LocalDate.now())) {
            throw new BusinessException("Ngày dự kiến trả phòng không được ở quá khứ");
        }
        if (checkoutRequestRepository.existsByTenantContractIdAndStatusIn(contract.getId(), OPEN_STATUSES)) {
            throw new BusinessException("Đã có yêu cầu trả phòng đang chờ xử lý cho hợp đồng này");
        }

        CheckoutRequest saved = checkoutRequestRepository.save(CheckoutRequest.builder()
                .tenantUserId(tenantUserId)
                .tenantContract(contract)
                .expectedMoveOutDate(request.getExpectedMoveOutDate())
                .reason(request.getReason().trim())
                .note(request.getNote())
                .status(CheckoutRequestStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build());

        UUID managerId = getManagerId(contract);
        if (managerId != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("screen", "CheckoutRequests");
            
            String roomStr = contract.getRoom() != null ? contract.getRoom().getRoomNumber() : "Nguyên căn";
            String title = "Yêu cầu trả phòng mới";
            String content = "Khách thuê phòng " + roomStr + " vừa gửi yêu cầu trả phòng.";
            sendNotification(managerId, "CHECKOUT_REQUESTED", title, content, data);
        }

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CheckoutRequestResponse> listRequests(UUID tenantUserId) {
        return checkoutRequestRepository.findByTenantUserIdOrderByCreatedAtDesc(tenantUserId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CheckoutRequestResponse getRequest(UUID tenantUserId, Long requestId) {
        return toResponse(loadOwned(requestId, tenantUserId));
    }

    @Override
    @Transactional
    public CheckoutRequestResponse cancelRequest(UUID tenantUserId, Long requestId) {
        CheckoutRequest checkoutRequest = loadOwned(requestId, tenantUserId);
        if (checkoutRequest.getStatus() != CheckoutRequestStatus.PENDING) {
            throw new BusinessException("Chỉ có thể hủy yêu cầu đang chờ duyệt");
        }
        checkoutRequest.setStatus(CheckoutRequestStatus.REJECTED);
        checkoutRequest.setRejectReason("Khách hủy yêu cầu");
        checkoutRequest.setReviewedAt(LocalDateTime.now());
        CheckoutRequest saved = checkoutRequestRepository.save(checkoutRequest);

        // Revert contract endDate if it was set (though it's only set in approve, but just in case)
        if (checkoutRequest.getTenantContract() != null && checkoutRequest.getTenantContract().getEndDate() != null) {
            if (checkoutRequest.getTenantContract().getEndDate().equals(checkoutRequest.getExpectedMoveOutDate())) {
                checkoutRequest.getTenantContract().setEndDate(null);
                tenantContractRepository.save(checkoutRequest.getTenantContract());
            }
        }

        UUID managerId = getManagerId(checkoutRequest.getTenantContract());
        if (managerId != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("screen", "CheckoutRequests");
            
            String roomStr = checkoutRequest.getTenantContract().getRoom() != null ? checkoutRequest.getTenantContract().getRoom().getRoomNumber() : "Nguyên căn";
            String title = "Khách hủy yêu cầu trả phòng";
            String content = "Khách thuê phòng " + roomStr + " đã hủy yêu cầu trả phòng.";
            sendNotification(managerId, "CHECKOUT_CANCELLED", title, content, data);
        }

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CheckoutRequestResponse> listRequestsForManager(String status) {
        List<CheckoutRequest> requests;
        if (status == null || status.isBlank()) {
            requests = checkoutRequestRepository.findAllByOrderByCreatedAtDesc();
        } else {
            try {
                CheckoutRequestStatus enumStatus = CheckoutRequestStatus.valueOf(status.toUpperCase());
                requests = checkoutRequestRepository.findByStatusOrderByCreatedAtDesc(enumStatus);
            } catch (IllegalArgumentException ex) {
                throw new BusinessException("Trạng thái yêu cầu trả phòng không hợp lệ: " + status);
            }
        }
        return requests.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public CheckoutRequestResponse createRequestForManager(UUID managerId, CreateCheckoutRequest request) {
        TenantContract contract = tenantContractRepository.findById(request.getContractId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy hợp đồng ID=" + request.getContractId()));

        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw new BusinessException("Chỉ có thể yêu cầu trả phòng với hợp đồng đang hiệu lực");
        }
        if (checkoutRequestRepository.existsByTenantContractIdAndStatusIn(contract.getId(), OPEN_STATUSES)) {
            throw new BusinessException("Đã có yêu cầu trả phòng đang chờ xử lý cho hợp đồng này");
        }

        CheckoutRequest saved = checkoutRequestRepository.save(CheckoutRequest.builder()
                .tenantUserId(contract.getTenant().getId())
                .tenantContract(contract)
                .expectedMoveOutDate(request.getExpectedMoveOutDate())
                .reason(request.getReason() != null ? request.getReason().trim() : "Quản lý tạo")
                .note(request.getNote())
                .status(CheckoutRequestStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build());
        
        Map<String, Object> data = new HashMap<>();
        data.put("screen", "CheckoutDetail");
        Map<String, Object> params = new HashMap<>();
        params.put("requestId", saved.getId());
        data.put("params", params);
        
        String title = "Yêu cầu trả phòng mới";
        String content = "Quản lý vừa tạo yêu cầu trả phòng cho bạn.";
        sendNotification(contract.getTenant().getId(), "CHECKOUT_REQUESTED_BY_MANAGER", title, content, data);

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public CheckoutRequestResponse getRequestForManager(Long requestId) {
        return toResponse(loadById(requestId));
    }

    @Override
    @Transactional
    public CheckoutRequestResponse approveRequest(
            Long requestId, UUID managerUserId, ApproveCheckoutRequest request) {
        CheckoutRequest checkoutRequest = loadById(requestId);
        assertPending(checkoutRequest);

        TenantContract contract = checkoutRequest.getTenantContract();
        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw new BusinessException("Hợp đồng không còn hiệu lực, không thể duyệt yêu cầu trả phòng");
        }

        checkoutRequest.setStatus(CheckoutRequestStatus.APPROVED);
        checkoutRequest.setReviewedAt(LocalDateTime.now());
        checkoutRequest.setReviewedBy(managerUserId);
        if (request != null && request.getManagerNote() != null && !request.getManagerNote().isBlank()) {
            checkoutRequest.setManagerNote(request.getManagerNote().trim());
        }
        CheckoutRequest saved = checkoutRequestRepository.save(checkoutRequest);

        // Update contract endDate
        contract.setEndDate(checkoutRequest.getExpectedMoveOutDate());
        tenantContractRepository.save(contract);

        // Recalculate rent invoice for the month of expectedMoveOutDate
        java.time.YearMonth ym = java.time.YearMonth.from(checkoutRequest.getExpectedMoveOutDate());
        com.sep490.slms2026.entity.TenantInvoice rentInvoice = 
                tenantInvoiceRepository.findByTenantContractIdAndInvoiceTypeAndBillingYearAndBillingMonth(
                    contract.getId(), com.sep490.slms2026.enums.TenantInvoiceType.RENT, ym.getYear(), ym.getMonthValue())
                .orElse(null);
        if (rentInvoice != null && rentInvoice.getStatus() != com.sep490.slms2026.enums.TenantInvoiceStatus.PAID) {
            java.time.LocalDate billStart = contract.getStartDate().isAfter(ym.atDay(1)) ? contract.getStartDate() : ym.atDay(1);
            if (!billStart.isAfter(contract.getEndDate())) {
                long days = java.time.temporal.ChronoUnit.DAYS.between(billStart, contract.getEndDate()) + 1;
                long daysInMonth = ym.lengthOfMonth();
                java.math.BigDecimal amount = contract.getRentAmount();
                if (days < daysInMonth) {
                    amount = amount.multiply(java.math.BigDecimal.valueOf(days)).divide(java.math.BigDecimal.valueOf(daysInMonth), 0, java.math.RoundingMode.HALF_UP);
                }
                if (amount.compareTo(rentInvoice.getGrandTotal()) != 0) {
                    rentInvoice.setTotalAmount(amount);
                    rentInvoice.setGrandTotal(amount);
                    // Also clear late fee if it was calculated on the old amount, or keep it? For simplicity just clear payos url.
                    rentInvoice.setPayosOrderCode(null);
                    rentInvoice.setPayosQrCode(null);
                    rentInvoice.setPayosCheckoutUrl(null);
                    tenantInvoiceRepository.save(rentInvoice);
                }
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("screen", "CheckoutDetail");
        Map<String, Object> params = new HashMap<>();
        params.put("requestId", saved.getId());
        data.put("params", params);
        
        String title = "Yêu cầu trả phòng được duyệt";
        String content = "Quản lý đã duyệt yêu cầu trả phòng của bạn.";
        sendNotification(saved.getTenantUserId(), "CHECKOUT_APPROVED", title, content, data);

        return toResponse(saved);
    }

    @Override
    @Transactional
    public CheckoutRequestResponse rejectRequest(
            Long requestId, UUID managerUserId, RejectCheckoutRequest request) {
        CheckoutRequest checkoutRequest = loadById(requestId);
        assertPending(checkoutRequest);

        checkoutRequest.setStatus(CheckoutRequestStatus.REJECTED);
        checkoutRequest.setReviewedAt(LocalDateTime.now());
        checkoutRequest.setReviewedBy(managerUserId);
        checkoutRequest.setRejectReason(request.getReason().trim());
        CheckoutRequest saved = checkoutRequestRepository.save(checkoutRequest);

        // Revert contract endDate if it was set
        if (checkoutRequest.getTenantContract() != null && checkoutRequest.getTenantContract().getEndDate() != null) {
            if (checkoutRequest.getTenantContract().getEndDate().equals(checkoutRequest.getExpectedMoveOutDate())) {
                checkoutRequest.getTenantContract().setEndDate(null);
                tenantContractRepository.save(checkoutRequest.getTenantContract());
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("screen", "CheckoutDetail");
        Map<String, Object> params = new HashMap<>();
        params.put("requestId", saved.getId());
        data.put("params", params);
        
        String title = "Yêu cầu trả phòng bị từ chối";
        String content = "Quản lý đã từ chối yêu cầu trả phòng của bạn: " + request.getReason().trim();
        sendNotification(saved.getTenantUserId(), "CHECKOUT_REJECTED", title, content, data);

        return toResponse(saved);
    }

    @Override
    @Transactional
    public CheckoutRequestResponse completeRequest(
            Long requestId, UUID managerUserId, CompleteCheckoutRequest request) {
        CheckoutRequest checkoutRequest = loadById(requestId);
        if (checkoutRequest.getStatus() != CheckoutRequestStatus.APPROVED && checkoutRequest.getStatus() != CheckoutRequestStatus.SETTLING) {
            throw new BusinessException("Chỉ hoàn tất được yêu cầu ở trạng thái APPROVED hoặc SETTLING");
        }

        TenantContract contract = checkoutRequest.getTenantContract();
        if (contract.getStatus() != ContractStatus.ACTIVE && contract.getStatus() != ContractStatus.EXPIRED) {
            throw new BusinessException("Hợp đồng không ở trạng thái có thể thanh lý");
        }

        LocalDate actualMoveOutDate = request != null && request.getActualMoveOutDate() != null
                ? request.getActualMoveOutDate()
                : LocalDate.now();
        if (actualMoveOutDate.isBefore(contract.getStartDate())) {
            throw new BusinessException("Ngày trả phòng thực tế không được trước ngày bắt đầu hợp đồng");
        }

        String completionNote = request != null ? request.getNote() : null;
        String terminateNote = "Hoàn tất yêu cầu trả phòng #" + checkoutRequest.getId();
        if (completionNote != null && !completionNote.isBlank()) {
            terminateNote += " — " + completionNote.trim();
        }
        if (checkoutRequest.getManagerNote() != null && !checkoutRequest.getManagerNote().isBlank()) {
            terminateNote += " | Ghi chú duyệt: " + checkoutRequest.getManagerNote();
        }

        TerminateContractRequest terminateRequest = new TerminateContractRequest();
        terminateRequest.setType(ContractTerminationType.EARLY_MOVE_OUT);
        terminateRequest.setReason(checkoutRequest.getReason());
        terminateRequest.setEffectiveDate(actualMoveOutDate);
        terminateRequest.setNote(terminateNote);

        tenantOnboardingService.terminateActiveContract(contract.getId(), terminateRequest);

        checkoutRequest.setStatus(CheckoutRequestStatus.COMPLETED);
        checkoutRequest.setCompletedAt(LocalDateTime.now());
        if (checkoutRequest.getReviewedBy() == null) {
            checkoutRequest.setReviewedBy(managerUserId);
        }
        CheckoutRequest saved = checkoutRequestRepository.save(checkoutRequest);

        Map<String, Object> data = new HashMap<>();
        data.put("screen", "CheckoutDetail");
        Map<String, Object> params = new HashMap<>();
        params.put("requestId", saved.getId());
        data.put("params", params);
        
        String title = "Hoàn tất trả phòng";
        String content = "Thủ tục trả phòng của bạn đã hoàn tất.";
        sendNotification(saved.getTenantUserId(), "CHECKOUT_COMPLETED", title, content, data);

        return toResponse(saved);
    }

    @Override
    @Transactional
    public CheckoutRequestResponse confirmRefund(Long requestId, UUID tenantUserId) {
        CheckoutRequest checkoutRequest = loadOwned(requestId, tenantUserId);

        com.sep490.slms2026.entity.CheckoutSettlement settlement = checkoutSettlementRepository.findByCheckoutRequestId(requestId)
                .orElseThrow(() -> new BusinessException("Chưa có quyết toán cho yêu cầu này"));

        if (settlement.getRefundPaidAt() == null) {
            throw new BusinessException("Quản lý chưa hoàn cọc, không thể xác nhận");
        }

        settlement.setRefundConfirmedAt(LocalDateTime.now());
        checkoutSettlementRepository.save(settlement);

        UUID managerId = getManagerId(checkoutRequest.getTenantContract());
        if (managerId != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("screen", "CheckoutDetail");
            Map<String, Object> params = new HashMap<>();
            params.put("requestId", checkoutRequest.getId());
            data.put("params", params);

            String roomStr = checkoutRequest.getTenantContract().getRoom() != null ? checkoutRequest.getTenantContract().getRoom().getRoomNumber() : "Nguyên căn";
            String title = "Khách đã nhận hoàn cọc";
            String content = "Khách thuê phòng " + roomStr + " đã xác nhận nhận được tiền hoàn cọc.";
            sendNotification(managerId, "CHECKOUT_REFUND_CONFIRMED", title, content, data);
        }

        return toResponse(checkoutRequest);
    }

    private CheckoutRequest loadOwned(Long requestId, UUID tenantUserId) {
        return checkoutRequestRepository.findByIdAndTenantUserId(requestId, tenantUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy yêu cầu trả phòng ID=" + requestId));
    }

    private CheckoutRequest loadById(Long requestId) {
        return checkoutRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy yêu cầu trả phòng ID=" + requestId));
    }

    private static void assertPending(CheckoutRequest checkoutRequest) {
        if (checkoutRequest.getStatus() != CheckoutRequestStatus.PENDING) {
            throw new BusinessException("Chỉ xử lý được yêu cầu đang chờ duyệt (PENDING)");
        }
    }

    private void sendNotification(UUID targetUserId, String type, String title, String content, Map<String, Object> data) {
        if (targetUserId == null) return;
        
        String screen = data != null ? (String) data.get("screen") : null;
        String paramsJson = null;
        if (data != null && data.get("params") != null) {
            try {
                paramsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data.get("params"));
            } catch (Exception e) {
                // Ignore parse error
            }
        }
        
        Notification notification = Notification.builder()
                .userId(targetUserId)
                .title(title)
                .content(content)
                .type(type)
                .screen(screen)
                .paramsJson(paramsJson)
                .read(false)
                .build();
        notificationRepository.save(notification);

        userRepository.findById(targetUserId).ifPresent(u -> {
            if (u.getPushToken() != null && !u.getPushToken().isBlank()) {
                pushNotificationService.sendPushNotification(u.getPushToken(), title, content, data);
            }
        });
    }

    private UUID getManagerId(TenantContract contract) {
        if (contract.getProperty() != null) {
            if (contract.getProperty().getOperationManagerId() != null) return contract.getProperty().getOperationManagerId();
            if (contract.getProperty().getOperationManagerId() != null) return contract.getProperty().getOperationManagerId();
        }
        return null;
    }

    private CheckoutRequestResponse toResponse(CheckoutRequest request) {
        TenantContract contract = request.getTenantContract();
        Tenant tenant = contract.getTenant();
        User tenantUser = tenant != null ? tenant.getUser() : null;
        User reviewer = request.getReviewedBy() != null
                ? userRepository.findById(request.getReviewedBy()).orElse(null)
                : null;

        return CheckoutRequestResponse.builder()
                .id(request.getId())
                .contractId(contract.getId())
                .contractCode(contract.getContractCode())
                .propertyName(contract.getProperty().getPropertyName())
                .roomNumber(contract.getRoom() != null ? contract.getRoom().getRoomNumber() : null)
                .tenantUserId(request.getTenantUserId())
                .tenantFullName(tenantUser != null ? tenantUser.getFullName() : null)
                .tenantPhone(tenantUser != null ? tenantUser.getPhoneNumber() : null)
                .expectedMoveOutDate(request.getExpectedMoveOutDate())
                .reason(request.getReason())
                .note(request.getNote())
                .status(request.getStatus().name())
                .disputeCount(request.getDisputeCount())
                .disputeReason(request.getDisputeReason())
                .disputePhotos(request.getDisputePhotos())
                .disputedAt(request.getDisputedAt())
                .createdAt(request.getCreatedAt())
                .reviewedAt(request.getReviewedAt())
                .reviewedBy(request.getReviewedBy())
                .reviewedByName(reviewer != null ? reviewer.getFullName() : null)
                .managerNote(request.getManagerNote())
                .rejectReason(request.getRejectReason())
                .completedAt(request.getCompletedAt())
                .build();
    }
}

