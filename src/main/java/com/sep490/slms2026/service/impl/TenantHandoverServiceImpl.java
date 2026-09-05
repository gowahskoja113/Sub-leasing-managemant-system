package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.response.ContractEvidencePhotoResponse;
import com.sep490.slms2026.dto.response.TenantHandoverResponse;
import com.sep490.slms2026.entity.ContractEvidencePhoto;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.TenantContractRepository;
import com.sep490.slms2026.service.ContractEquipmentService;
import com.sep490.slms2026.service.TenantHandoverService;
import com.sep490.slms2026.util.TenantActiveContractResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantHandoverServiceImpl implements TenantHandoverService {

    private static final List<ContractStatus> READABLE =
            List.of(ContractStatus.ACTIVE, ContractStatus.PENDING);

    private static final Comparator<TenantContract> CONTRACT_SORT = Comparator
            .comparing(TenantContract::getStartDate, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(TenantContract::getId, Comparator.reverseOrder());

    private final TenantContractRepository tenantContractRepository;
    private final ContractEquipmentService contractEquipmentService;

    @Override
    @Transactional(readOnly = true)
    public TenantHandoverResponse getHandover(UUID tenantUserId, Long contractId) {
        TenantContract contract = resolveReadableContract(tenantUserId, contractId);
        return toResponse(contract);
    }

    @Override
    @Transactional
    public TenantHandoverResponse acknowledgeHandover(UUID tenantUserId, Long contractId) {
        TenantContract contract = resolveActiveContract(tenantUserId, contractId);
        if (contract.getHandoverAcknowledgedAt() != null) {
            throw new BusinessException("Bạn đã xác nhận biên bản bàn giao trước đó");
        }
        contract.setHandoverAcknowledgedAt(LocalDateTime.now());
        return toResponse(tenantContractRepository.save(contract));
    }

    private TenantContract resolveActiveContract(UUID tenantUserId, Long contractId) {
        return TenantActiveContractResolver.resolve(
                tenantContractRepository.findByTenantId(tenantUserId),
                contractId,
                false);
    }

    private TenantContract resolveReadableContract(UUID tenantUserId, Long contractId) {
        List<TenantContract> readable = listReadable(tenantContractRepository.findByTenantId(tenantUserId));
        if (readable.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy hợp đồng đang hiệu lực");
        }
        if (contractId != null) {
            return readable.stream()
                    .filter(c -> contractId.equals(c.getId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(
                            "Hợp đồng không thuộc tài khoản của bạn hoặc không đang hiệu lực"));
        }
        List<TenantContract> active = readable.stream()
                .filter(c -> c.getStatus() == ContractStatus.ACTIVE)
                .toList();
        if (!active.isEmpty()) {
            if (active.size() == 1) {
                return active.get(0);
            }
            throw new BusinessException(
                    "Bạn đang thuê nhiều nhà. Vui lòng chọn hợp đồng (truyền contractId)");
        }
        if (readable.size() == 1) {
            return readable.get(0);
        }
        throw new BusinessException(
                "Bạn đang thuê nhiều nhà. Vui lòng chọn hợp đồng (truyền contractId)");
    }

    private static List<TenantContract> listReadable(List<TenantContract> contracts) {
        if (contracts == null || contracts.isEmpty()) {
            return List.of();
        }
        return contracts.stream()
                .filter(c -> READABLE.contains(c.getStatus()))
                .sorted(CONTRACT_SORT)
                .toList();
    }

    private TenantHandoverResponse toResponse(TenantContract contract) {
        List<ContractEvidencePhoto> photos = contract.getRoomConditionPhotos() != null
                ? contract.getRoomConditionPhotos()
                : List.of();
        List<String> urls = photos.stream()
                .map(ContractEvidencePhoto::getImageUrl)
                .filter(u -> u != null && !u.isBlank())
                .toList();
        List<ContractEvidencePhotoResponse> photoResponses = photos.stream()
                .map(p -> ContractEvidencePhotoResponse.builder()
                        .url(p.getImageUrl())
                        .capturedAt(p.getCapturedAt())
                        .build())
                .toList();

        return TenantHandoverResponse.builder()
                .contractId(contract.getId())
                .contractCode(contract.getContractCode())
                .propertyName(contract.getProperty().getPropertyName())
                .roomNumber(contract.getRoom() != null ? contract.getRoom().getRoomNumber() : null)
                .initialElectricReading(contract.getInitialElectricReading())
                .initialWaterReading(contract.getInitialWaterReading())
                .electricMeterImageUrl(contract.getElectricMeterImageUrl())
                .electricMeterCapturedAt(contract.getElectricMeterCapturedAt())
                .waterMeterImageUrl(contract.getWaterMeterImageUrl())
                .waterMeterCapturedAt(contract.getWaterMeterCapturedAt())
                .roomConditionUrls(urls)
                .roomConditionPhotos(photoResponses)
                .roomConditionNote(contract.getRoomConditionNote())
                .equipmentSnapshot(contract.getEquipmentSnapshot())
                .equipmentList(contractEquipmentService.mapSelectedToItems(contract))
                .acknowledged(contract.getHandoverAcknowledgedAt() != null)
                .acknowledgedAt(contract.getHandoverAcknowledgedAt())
                .build();
    }
}
