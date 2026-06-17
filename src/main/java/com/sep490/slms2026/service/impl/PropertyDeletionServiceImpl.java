package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.response.PropertyPurgeResponse;
import com.sep490.slms2026.entity.InboundContract;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.enums.PropertyStatus;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.*;
import com.sep490.slms2026.service.PropertyDeletionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PropertyDeletionServiceImpl implements PropertyDeletionService {

    private final PropertyRepository propertyRepository;
    private final EquipmentRepository equipmentRepository;
    private final EquipmentManifestRepository equipmentManifestRepository;
    private final RenovationLineRepository renovationLineRepository;
    private final RenovationSessionRepository renovationSessionRepository;
    private final RoomRepository roomRepository;
    private final InboundContractRepository inboundContractRepository;
    private final DepreciationResultRepository depreciationResultRepository;
    private final MonthlyReadingRepository monthlyReadingRepository;

    @Override
    @Transactional
    public PropertyPurgeResponse purgeProperty(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tòa nhà ID=" + propertyId));

        if (property.getStatus() == PropertyStatus.ACTIVE) {
            throw new BusinessException(
                    "Không thể xóa căn nhà đang ACTIVE. Vui lòng disable hoặc thanh lý hợp đồng trước.");
        }
        if (monthlyReadingRepository.existsByPropertyId(propertyId)) {
            throw new BusinessException(
                    "Không thể xóa căn nhà đã có chỉ số điện nước. Chỉ dùng cho dữ liệu onboarding/import sai.");
        }

        String contractCode = inboundContractRepository.findByPropertyId(propertyId)
                .map(InboundContract::getContractCode)
                .orElse(null);

        int equipmentsDeleted = (int) equipmentRepository.countByPropertyId(propertyId);
        int equipmentManifestsDeleted = equipmentManifestRepository.findByPropertyId(propertyId).size();
        int renovationLinesDeleted = renovationLineRepository.findByPropertyId(propertyId).size();
        int renovationSessionsDeleted = (int) renovationSessionRepository.countByPropertyId(propertyId);
        int roomsDeleted = (int) roomRepository.countAllByPropertyIdIncludingDeleted(propertyId);
        int depreciationResultsDeleted = depreciationResultRepository.findAllRoomLevelByPropertyId(propertyId).size();
        if (depreciationResultRepository.existsByInboundContractPropertyIdAndRoomIsNull(propertyId)) {
            depreciationResultsDeleted++;
        }
        int monthlyReadingsDeleted = (int) monthlyReadingRepository.countByPropertyId(propertyId);

        deleteDependentRecords(propertyId);
        propertyRepository.delete(property);

        return PropertyPurgeResponse.builder()
                .propertyId(propertyId)
                .propertyName(property.getPropertyName())
                .contractCode(contractCode)
                .equipmentsDeleted(equipmentsDeleted)
                .equipmentManifestsDeleted(equipmentManifestsDeleted)
                .renovationLinesDeleted(renovationLinesDeleted)
                .renovationSessionsDeleted(renovationSessionsDeleted)
                .roomsDeleted(roomsDeleted)
                .depreciationResultsDeleted(depreciationResultsDeleted)
                .monthlyReadingsDeleted(monthlyReadingsDeleted)
                .build();
    }

    /**
     * Thứ tự xóa theo phụ thuộc FK — khớp docs/BE-import-excel-status-constraint.md §BUG #2.
     */
    private void deleteDependentRecords(Long propertyId) {
        depreciationResultRepository.deleteByPropertyId(propertyId);
        equipmentRepository.deleteByPropertyId(propertyId);
        monthlyReadingRepository.deleteByPropertyId(propertyId);
        inboundContractRepository.deleteByPropertyId(propertyId);
        renovationLineRepository.deleteByPropertyId(propertyId);
        renovationSessionRepository.deleteByPropertyId(propertyId);
        equipmentManifestRepository.deleteByPropertyId(propertyId);
        roomRepository.deleteAllByPropertyId(propertyId);
    }
}
