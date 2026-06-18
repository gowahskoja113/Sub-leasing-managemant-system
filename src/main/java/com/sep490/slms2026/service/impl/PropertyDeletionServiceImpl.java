package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.response.PropertyPurgeResponse;
import com.sep490.slms2026.enums.PropertyStatus;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.*;
import com.sep490.slms2026.service.PropertyDeletionService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public PropertyPurgeResponse purgeProperty(Long propertyId) {
        var nameAndStatus = propertyRepository.findNameAndStatusById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tòa nhà ID=" + propertyId));

        String propertyName = nameAndStatus.getPropertyName();
        PropertyStatus status = nameAndStatus.getStatus();

        if (status == PropertyStatus.ACTIVE) {
            throw new BusinessException(
                    "Không thể xóa căn nhà đang ACTIVE. Vui lòng disable hoặc thanh lý hợp đồng trước.");
        }
        if (monthlyReadingRepository.existsByPropertyId(propertyId)) {
            throw new BusinessException(
                    "Không thể xóa căn nhà đã có chỉ số điện nước. Chỉ dùng cho dữ liệu onboarding/import sai.");
        }

        String contractCode = inboundContractRepository.findContractCodeByPropertyId(propertyId)
                .orElse(null);

        int equipmentsDeleted = (int) equipmentRepository.countByPropertyId(propertyId);
        int equipmentManifestsDeleted = (int) equipmentManifestRepository.countByPropertyId(propertyId);
        int renovationLinesDeleted = (int) renovationLineRepository.countByPropertyId(propertyId);
        int renovationSessionsDeleted = (int) renovationSessionRepository.countByPropertyId(propertyId);
        int roomsDeleted = (int) roomRepository.countAllByPropertyIdIncludingDeleted(propertyId);
        int depreciationResultsDeleted = (int) depreciationResultRepository.countByPropertyId(propertyId);
        int monthlyReadingsDeleted = (int) monthlyReadingRepository.countByPropertyId(propertyId);

        bulkDeleteDependentRecords(propertyId);

        return PropertyPurgeResponse.builder()
                .propertyId(propertyId)
                .propertyName(propertyName)
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
     * Chỉ bulk DELETE (JPQL / native) — không load entity con, không gọi repository.delete(entity).
     * Thứ tự con → cha: docs/BE-import-excel-status-constraint.md
     */
    private void bulkDeleteDependentRecords(Long propertyId) {
        depreciationResultRepository.deleteByPropertyId(propertyId);
        equipmentRepository.deleteByPropertyId(propertyId);
        monthlyReadingRepository.deleteByPropertyId(propertyId);
        inboundContractRepository.deleteByPropertyId(propertyId);
        renovationLineRepository.deleteByPropertyId(propertyId);
        renovationSessionRepository.deleteByPropertyId(propertyId);
        equipmentManifestRepository.deleteByPropertyId(propertyId);
        jdbcTemplate.update("DELETE FROM property_images WHERE property_id = ?", propertyId);
        roomRepository.deleteAllByPropertyId(propertyId);
        jdbcTemplate.update("DELETE FROM properties WHERE id = ?", propertyId);
    }
}
