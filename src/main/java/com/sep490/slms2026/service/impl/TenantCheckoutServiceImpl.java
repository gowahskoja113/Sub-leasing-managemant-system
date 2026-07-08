package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.CreateCheckoutRequest;
import com.sep490.slms2026.dto.response.CheckoutRequestResponse;
import com.sep490.slms2026.entity.CheckoutRequest;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.enums.CheckoutRequestStatus;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.CheckoutRequestRepository;
import com.sep490.slms2026.repository.TenantContractRepository;
import com.sep490.slms2026.service.TenantCheckoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantCheckoutServiceImpl implements TenantCheckoutService {

    private final CheckoutRequestRepository checkoutRequestRepository;
    private final TenantContractRepository tenantContractRepository;

    @Override
    @Transactional
    public CheckoutRequestResponse createRequest(UUID tenantUserId, CreateCheckoutRequest request) {
        TenantContract contract = tenantContractRepository.findById(request.getContractId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy hợp đồng ID=" + request.getContractId()));

        if (!contract.getTenant().getId().equals(tenantUserId)) {
            throw new BusinessException("Hợp đồng không thuộc tài khoản của bạn");
        }
        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw new BusinessException("Chỉ có thể yêu cầu trả phòng với hợp đồng đang hiệu lực");
        }
        if (request.getExpectedMoveOutDate().isBefore(java.time.LocalDate.now())) {
            throw new BusinessException("Ngày dự kiến trả phòng không được ở quá khứ");
        }

        CheckoutRequest saved = checkoutRequestRepository.save(CheckoutRequest.builder()
                .tenantUserId(tenantUserId)
                .tenantContract(contract)
                .expectedMoveOutDate(request.getExpectedMoveOutDate())
                .reason(request.getReason())
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

    private CheckoutRequest loadOwned(Long requestId, UUID tenantUserId) {
        return checkoutRequestRepository.findByIdAndTenantUserId(requestId, tenantUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy yêu cầu trả phòng ID=" + requestId));
    }

    private CheckoutRequestResponse toResponse(CheckoutRequest request) {
        TenantContract contract = request.getTenantContract();
        return CheckoutRequestResponse.builder()
                .id(request.getId())
                .contractId(contract.getId())
                .contractCode(contract.getContractCode())
                .propertyName(contract.getProperty().getPropertyName())
                .roomNumber(contract.getRoom() != null ? contract.getRoom().getRoomNumber() : null)
                .expectedMoveOutDate(request.getExpectedMoveOutDate())
                .reason(request.getReason())
                .note(request.getNote())
                .status(request.getStatus().name())
                .createdAt(request.getCreatedAt())
                .build();
    }
}
