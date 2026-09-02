package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.CreateExtensionRequest;
import com.sep490.slms2026.dto.request.ExtendContractRequest;
import com.sep490.slms2026.dto.request.ExtensionNoteRequest;
import com.sep490.slms2026.dto.request.RejectExtensionRequest;
import com.sep490.slms2026.dto.response.ExtensionOptionsResponse;
import com.sep490.slms2026.dto.response.ExtensionRequestResponse;
import com.sep490.slms2026.entity.ExtensionRequest;
import com.sep490.slms2026.entity.InboundContract;
import com.sep490.slms2026.entity.Notification;
import com.sep490.slms2026.entity.Tenant;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.enums.ExtensionRequestStatus;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.ExtensionRequestRepository;
import com.sep490.slms2026.repository.InboundContractRepository;
import com.sep490.slms2026.repository.NotificationRepository;
import com.sep490.slms2026.repository.TenantContractRepository;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.service.ExtensionRequestService;
import com.sep490.slms2026.service.PushNotificationService;
import com.sep490.slms2026.service.TenantOnboardingService;
import com.sep490.slms2026.util.InboundLeaseRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExtensionRequestServiceImpl implements ExtensionRequestService {

    private final ExtensionRequestRepository extensionRequestRepository;
    private final TenantContractRepository tenantContractRepository;
    private final InboundContractRepository inboundContractRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final PushNotificationService pushNotificationService;
    private final TenantOnboardingService tenantOnboardingService;

    @Override
    @Transactional(readOnly = true)
    public ExtensionOptionsResponse getExtensionOptions(UUID tenantUserId, Long contractId) {
        TenantContract contract = loadOwnedContract(contractId, tenantUserId);
        
        if (contract.getEndDate() == null) {
            return ExtensionOptionsResponse.builder().maxMonths(0).build();
        }

        InboundContract lease = inboundContractRepository.findFirstByPropertyIdOrderByIdDesc(contract.getProperty().getId()).orElse(null);
        if (lease == null || lease.getEndDate() == null || !lease.getEndDate().isAfter(contract.getEndDate())) {
            return ExtensionOptionsResponse.builder().maxMonths(0).build();
        }

        long maxMonths = ChronoUnit.MONTHS.between(contract.getEndDate(), lease.getEndDate().plusDays(1));
        return ExtensionOptionsResponse.builder().maxMonths((int) Math.max(0, maxMonths)).build();
    }

    @Override
    @Transactional
    public ExtensionRequestResponse createRequest(UUID tenantUserId, Long contractId, CreateExtensionRequest request) {
        TenantContract contract = loadOwnedContract(contractId, tenantUserId);
        
        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw new BusinessException("Chỉ có thể xin gia hạn hợp đồng đang hiệu lực");
        }
        
        LocalDate today = LocalDate.now();
        if (contract.getEndDate() == null || today.isAfter(contract.getEndDate().minusDays(1))) {
            throw new BusinessException("Hết hạn nộp đơn gia hạn (đã đóng cửa gia hạn vào ngày cuối của hợp đồng)");
        }

        if (extensionRequestRepository.existsByTenantContractIdAndStatus(contractId, ExtensionRequestStatus.PENDING)) {
            throw new BusinessException("Đã có đơn xin gia hạn đang chờ duyệt");
        }
        
        InboundContract lease = inboundContractRepository.findFirstByPropertyIdOrderByIdDesc(contract.getProperty().getId()).orElse(null);
        LocalDate newEndDate = contract.getEndDate().plusMonths(request.getMonths());
        
        try {
            InboundLeaseRules.assertOccupancyWindow(contract.getMoveInDate(), newEndDate, lease);
        } catch (BusinessException e) {
            throw new BusinessException("Hiện chưa gia hạn thêm được thời hạn này. Vui lòng liên hệ quản lý.");
        }

        ExtensionRequest saved = extensionRequestRepository.save(ExtensionRequest.builder()
                .tenantContract(contract)
                .tenantUserId(tenantUserId)
                .months(request.getMonths())
                .newEndDate(newEndDate)
                .note(request.getNote() != null ? request.getNote().trim() : null)
                .status(ExtensionRequestStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build());

        String roomStr = contract.getRoom() != null ? contract.getRoom().getRoomNumber() : "Nguyên căn";
        String title = "Đơn xin gia hạn mới";
        String content = "Khách thuê phòng " + roomStr + " xin gia hạn thêm " + request.getMonths() + " tháng.";
        
        Map<String, Object> data = new HashMap<>();
        data.put("screen", "ExtensionRequests");
        
        // Notify Manager
        UUID managerId = getManagerId(contract);
        if (managerId != null) {
            sendNotification(managerId, "EXTENSION_REQUESTED", title, content, data);
        }
        
        // Notify Admin and Host
        userRepository.findByRole(com.sep490.slms2026.enums.Role.ROLE_ADMIN).forEach(admin -> {
            sendNotification(admin.getId(), "EXTENSION_REQUESTED", title, content, data);
        });
        userRepository.findByRole(com.sep490.slms2026.enums.Role.ROLE_OWNER).forEach(owner -> {
            sendNotification(owner.getId(), "EXTENSION_REQUESTED", title, content, data);
        });

        return toResponse(saved);
    }

    @Override
    @Transactional
    public ExtensionRequestResponse withdrawRequest(UUID tenantUserId, Long requestId) {
        ExtensionRequest req = extensionRequestRepository.findByIdAndTenantUserId(requestId, tenantUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn xin gia hạn ID=" + requestId));
                
        if (req.getStatus() != ExtensionRequestStatus.PENDING) {
            throw new BusinessException("Chỉ có thể rút đơn đang chờ duyệt");
        }
        
        req.setStatus(ExtensionRequestStatus.WITHDRAWN);
        ExtensionRequest saved = extensionRequestRepository.save(req);
        
        String roomStr = saved.getTenantContract().getRoom() != null ? saved.getTenantContract().getRoom().getRoomNumber() : "Nguyên căn";
        String title = "Khách rút đơn xin gia hạn";
        String content = "Khách thuê phòng " + roomStr + " đã rút đơn xin gia hạn.";
        
        UUID managerId = getManagerId(saved.getTenantContract());
        if (managerId != null) {
            sendNotification(managerId, "EXTENSION_WITHDRAWN", title, content, null);
        }
        userRepository.findByRole(com.sep490.slms2026.enums.Role.ROLE_ADMIN).forEach(admin -> {
            sendNotification(admin.getId(), "EXTENSION_WITHDRAWN", title, content, null);
        });
        
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExtensionRequestResponse> listRequestsForTenant(UUID tenantUserId, Long contractId) {
        loadOwnedContract(contractId, tenantUserId);
        
        List<ExtensionRequest> requests = extensionRequestRepository.findByTenantContractIdOrderByCreatedAtDesc(contractId);
        return requests.stream()
                .map(req -> {
                    ExtensionRequestResponse res = toResponse(req);
                    res.setManagerNote(null);
                    return res;
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExtensionRequestResponse> listRequestsForManager(String status) {
        return listRequestsByStatus(status);
    }

    @Override
    @Transactional
    public ExtensionRequestResponse addManagerNote(UUID managerUserId, Long requestId, ExtensionNoteRequest request) {
        ExtensionRequest req = extensionRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn xin gia hạn ID=" + requestId));
                
        if (req.getStatus() != ExtensionRequestStatus.PENDING) {
            throw new BusinessException("Chỉ có thể thêm ý kiến vào đơn đang chờ duyệt");
        }
        
        req.setManagerNote(request.getNote().trim());
        return toResponse(extensionRequestRepository.save(req));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExtensionRequestResponse> listRequestsForAdmin(String status) {
        return listRequestsByStatus(status);
    }

    @Override
    @Transactional
    public ExtensionRequestResponse approveRequest(UUID adminUserId, Long requestId) {
        ExtensionRequest req = extensionRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn xin gia hạn ID=" + requestId));
                
        if (req.getStatus() != ExtensionRequestStatus.PENDING) {
            throw new BusinessException("Chỉ có thể duyệt đơn đang chờ (PENDING)");
        }
        
        req.setStatus(ExtensionRequestStatus.APPROVED);
        req.setReviewedAt(LocalDateTime.now());
        req.setReviewedBy(adminUserId);
        
        // Execute extend contract using existing service (preserves rent by passing null)
        ExtendContractRequest extendReq = new ExtendContractRequest();
        extendReq.setNewEndDate(req.getNewEndDate());
        extendReq.setNewRentAmount(null);
        tenantOnboardingService.extendContract(req.getTenantContract().getId(), extendReq);
        
        ExtensionRequest saved = extensionRequestRepository.save(req);
        
        String title = "Đơn xin gia hạn được duyệt";
        String content = "Yêu cầu gia hạn thêm " + saved.getMonths() + " tháng của bạn đã được duyệt.";
        sendNotification(saved.getTenantUserId(), "EXTENSION_APPROVED", title, content, null);
        
        UUID managerId = getManagerId(saved.getTenantContract());
        if (managerId != null) {
            String roomStr = saved.getTenantContract().getRoom() != null ? saved.getTenantContract().getRoom().getRoomNumber() : "Nguyên căn";
            sendNotification(managerId, "EXTENSION_APPROVED_MGR", "Gia hạn thành công", "Đơn gia hạn của phòng " + roomStr + " đã được admin duyệt.", null);
        }
        
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ExtensionRequestResponse rejectRequest(UUID adminUserId, Long requestId, RejectExtensionRequest request) {
        ExtensionRequest req = extensionRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn xin gia hạn ID=" + requestId));
                
        if (req.getStatus() != ExtensionRequestStatus.PENDING) {
            throw new BusinessException("Chỉ có thể từ chối đơn đang chờ (PENDING)");
        }
        
        req.setStatus(ExtensionRequestStatus.REJECTED);
        req.setReviewedAt(LocalDateTime.now());
        req.setReviewedBy(adminUserId);
        req.setRejectReason(request.getReason().trim());
        
        ExtensionRequest saved = extensionRequestRepository.save(req);
        
        String title = "Đơn xin gia hạn bị từ chối";
        String content = "Yêu cầu gia hạn của bạn đã bị từ chối: " + saved.getRejectReason();
        sendNotification(saved.getTenantUserId(), "EXTENSION_REJECTED", title, content, null);
        
        UUID managerId = getManagerId(saved.getTenantContract());
        if (managerId != null) {
            String roomStr = saved.getTenantContract().getRoom() != null ? saved.getTenantContract().getRoom().getRoomNumber() : "Nguyên căn";
            sendNotification(managerId, "EXTENSION_REJECTED_MGR", "Gia hạn bị từ chối", "Đơn gia hạn của phòng " + roomStr + " đã bị từ chối.", null);
        }
        
        return toResponse(saved);
    }
    
    private List<ExtensionRequestResponse> listRequestsByStatus(String status) {
        List<ExtensionRequest> requests;
        if (status == null || status.isBlank()) {
            requests = extensionRequestRepository.findAllByOrderByCreatedAtDesc();
        } else {
            try {
                ExtensionRequestStatus enumStatus = ExtensionRequestStatus.valueOf(status.toUpperCase());
                requests = extensionRequestRepository.findByStatusOrderByCreatedAtDesc(enumStatus);
            } catch (IllegalArgumentException ex) {
                throw new BusinessException("Trạng thái không hợp lệ: " + status);
            }
        }
        return requests.stream().map(this::toResponse).toList();
    }

    private TenantContract loadOwnedContract(Long contractId, UUID tenantUserId) {
        TenantContract contract = tenantContractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hợp đồng ID=" + contractId));
        if (contract.getTenant() == null || !contract.getTenant().getId().equals(tenantUserId)) {
            throw new BusinessException("Hợp đồng không thuộc tài khoản của bạn");
        }
        return contract;
    }

    private UUID getManagerId(TenantContract contract) {
        if (contract.getProperty() != null && contract.getProperty().getOperationManagerId() != null) {
            return contract.getProperty().getOperationManagerId();
        }
        return null;
    }

    private void sendNotification(UUID targetUserId, String type, String title, String content, Map<String, Object> data) {
        if (targetUserId == null) return;
        
        String screen = data != null ? (String) data.get("screen") : null;
        String paramsJson = null;
        if (data != null && data.get("params") != null) {
            try {
                paramsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data.get("params"));
            } catch (Exception e) {
                // Ignore
            }
        }
        
        notificationRepository.save(Notification.builder()
                .userId(targetUserId)
                .title(title)
                .content(content)
                .type(type)
                .screen(screen)
                .paramsJson(paramsJson)
                .read(false)
                .build());

        userRepository.findById(targetUserId).ifPresent(u -> {
            if (u.getPushToken() != null && !u.getPushToken().isBlank()) {
                pushNotificationService.sendPushNotification(u.getPushToken(), title, content, data);
            }
        });
    }

    private ExtensionRequestResponse toResponse(ExtensionRequest req) {
        TenantContract contract = req.getTenantContract();
        Tenant tenant = contract.getTenant();
        User tenantUser = tenant != null ? tenant.getUser() : null;
        User reviewer = req.getReviewedBy() != null ? userRepository.findById(req.getReviewedBy()).orElse(null) : null;
        
        return ExtensionRequestResponse.builder()
                .id(req.getId())
                .contractId(contract.getId())
                .contractCode(contract.getContractCode())
                .propertyName(contract.getProperty() != null ? contract.getProperty().getPropertyName() : null)
                .roomNumber(contract.getRoom() != null ? contract.getRoom().getRoomNumber() : null)
                .tenantUserId(req.getTenantUserId())
                .tenantFullName(tenantUser != null ? tenantUser.getFullName() : null)
                .tenantPhone(tenantUser != null ? tenantUser.getPhoneNumber() : null)
                .months(req.getMonths())
                .newEndDate(req.getNewEndDate())
                .note(req.getNote())
                .status(req.getStatus().name())
                .createdAt(req.getCreatedAt())
                .reviewedAt(req.getReviewedAt())
                .reviewedBy(req.getReviewedBy())
                .reviewedByName(reviewer != null ? reviewer.getFullName() : null)
                .managerNote(req.getManagerNote())
                .rejectReason(req.getRejectReason())
                .build();
    }
}
