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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
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
        return toResponse(checkoutRequestRepository.save(checkoutRequest));
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
        return toResponse(checkoutRequestRepository.save(checkoutRequest));
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
        return toResponse(checkoutRequestRepository.save(checkoutRequest));
    }

    @Override
    @Transactional
    public CheckoutRequestResponse completeRequest(
            Long requestId, UUID managerUserId, CompleteCheckoutRequest request) {
        CheckoutRequest checkoutRequest = loadById(requestId);
        if (checkoutRequest.getStatus() != CheckoutRequestStatus.APPROVED) {
            throw new BusinessException("Chỉ hoàn tất được yêu cầu đã được duyệt (APPROVED)");
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
        return toResponse(checkoutRequestRepository.save(checkoutRequest));
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
