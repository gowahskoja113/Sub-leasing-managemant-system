package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.CreateInboundContractRequest;
import com.sep490.slms2026.dto.response.InboundContractResponse;
import com.sep490.slms2026.entity.InboundContract;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.enums.PropertyStatus;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.InboundContractRepository;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.service.InboundContractService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InboundContractServiceImpl implements InboundContractService {

    private final InboundContractRepository inboundContractRepository;
    private final PropertyRepository propertyRepository;

    @Override
    @Transactional
    public InboundContractResponse signContract(Long propertyId, CreateInboundContractRequest request) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy tòa nhà với ID: " + propertyId));

        if (!request.getEndDate().isAfter(request.getStartDate())) {
            throw new BusinessException("Ngày kết thúc hợp đồng phải sau ngày bắt đầu");
        }

        if (property.getStatus() != PropertyStatus.DRAFT) {
            if (mayReturnExistingContract(property.getStatus())) {
                return inboundContractRepository.findFirstByPropertyIdOrderByIdDesc(propertyId)
                        .map(this::toResponse)
                        .orElseThrow(() -> new BusinessException(
                                "Chỉ có thể ký hợp đồng khi tòa nhà đang ở trạng thái DRAFT"));
            }
            throw new BusinessException("Chỉ có thể ký hợp đồng khi tòa nhà đang ở trạng thái DRAFT");
        }

        InboundContract contract = inboundContractRepository.findFirstByPropertyIdOrderByIdDesc(propertyId)
                .orElseGet(() -> InboundContract.builder()
                        .property(property)
                        .status(ContractStatus.ACTIVE)
                        .build());

        contract.setContractCode(request.getContractCode());
        contract.setOwnerName(request.getOwnerName());
        contract.setTotalRentAmount(request.getTotalRentAmount());
        contract.setStartDate(request.getStartDate());
        contract.setEndDate(request.getEndDate());
        contract.setContractScanUrl(request.getContractScanUrl());

        InboundContract saved = inboundContractRepository.save(contract);
        property.setStatus(PropertyStatus.PENDING);
        propertyRepository.save(property);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public InboundContractResponse getContractByProperty(Long propertyId) {
        InboundContract contract = inboundContractRepository.findFirstByPropertyIdOrderByIdDesc(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy hợp đồng inbound cho tòa nhà ID: " + propertyId));
        return toResponse(contract);
    }

    private static boolean mayReturnExistingContract(PropertyStatus status) {
        return status.isOnboardingEditable()
                || status == PropertyStatus.PENDING_HOST_REVIEW
                || status == PropertyStatus.PENDING_OPERATION_MANAGER;
    }

    private InboundContractResponse toResponse(InboundContract contract) {
        return InboundContractResponse.builder()
                .id(contract.getId())
                .propertyId(contract.getProperty().getId())
                .contractCode(contract.getContractCode())
                .ownerName(contract.getOwnerName())
                .totalRentAmount(contract.getTotalRentAmount())
                .startDate(contract.getStartDate())
                .endDate(contract.getEndDate())
                .contractScanUrl(contract.getContractScanUrl())
                .status(contract.getStatus())
                .build();
    }
}
