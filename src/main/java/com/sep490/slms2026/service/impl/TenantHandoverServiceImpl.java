package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.response.TenantHandoverResponse;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.TenantContractRepository;
import com.sep490.slms2026.service.ContractEquipmentService;
import com.sep490.slms2026.service.TenantHandoverService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantHandoverServiceImpl implements TenantHandoverService {

    private final TenantContractRepository tenantContractRepository;
    private final ContractEquipmentService contractEquipmentService;

    @Override
    @Transactional(readOnly = true)
    public TenantHandoverResponse getHandover(UUID tenantUserId) {
        TenantContract contract = findActiveContract(tenantUserId);
        return toResponse(contract);
    }

    @Override
    @Transactional
    public TenantHandoverResponse acknowledgeHandover(UUID tenantUserId) {
        TenantContract contract = findActiveContract(tenantUserId);
        if (contract.getHandoverAcknowledgedAt() != null) {
            throw new BusinessException("Bạn đã xác nhận biên bản bàn giao trước đó");
        }
        contract.setHandoverAcknowledgedAt(LocalDateTime.now());
        return toResponse(tenantContractRepository.save(contract));
    }

    private TenantContract findActiveContract(UUID tenantUserId) {
        return tenantContractRepository.findByTenantId(tenantUserId).stream()
                .filter(c -> c.getStatus() == ContractStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy hợp đồng đang hiệu lực"));
    }

    private TenantHandoverResponse toResponse(TenantContract contract) {
        List<String> photos = contract.getRoomConditionUrls() != null
                ? contract.getRoomConditionUrls()
                : List.of();

        return TenantHandoverResponse.builder()
                .contractId(contract.getId())
                .contractCode(contract.getContractCode())
                .propertyName(contract.getProperty().getPropertyName())
                .roomNumber(contract.getRoom() != null ? contract.getRoom().getRoomNumber() : null)
                .initialElectricReading(contract.getInitialElectricReading())
                .initialWaterReading(contract.getInitialWaterReading())
                .electricMeterImageUrl(contract.getElectricMeterImageUrl())
                .waterMeterImageUrl(contract.getWaterMeterImageUrl())
                .roomConditionUrls(photos)
                .roomConditionNote(contract.getRoomConditionNote())
                .equipmentSnapshot(contract.getEquipmentSnapshot())
                .equipmentList(contractEquipmentService.mapSelectedToItems(contract))
                .acknowledged(contract.getHandoverAcknowledgedAt() != null)
                .acknowledgedAt(contract.getHandoverAcknowledgedAt())
                .build();
    }
}
