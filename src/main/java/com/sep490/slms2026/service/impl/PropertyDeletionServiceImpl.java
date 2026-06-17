package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.response.PropertyPurgeResponse;
import com.sep490.slms2026.entity.InboundContract;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.enums.PropertyStatus;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.*;
import com.sep490.slms2026.service.PropertyDeletionService;
import jakarta.persistence.EntityManager;
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
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;

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

        entityManager.detach(property);
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
     * Thứ tự xóa con → cha (docs/BE-import-excel-status-constraint.md):
     * depreciation_results → equipments → monthly_readings → inbound_contracts
     * → renovation_lines → renovation_sessions → equipment_manifests
     * → property_images → rooms → properties
     */
    private void deleteDependentRecords(Long propertyId) {
        depreciationResultRepository.deleteByPropertyId(propertyId);
        equipmentRepository.deleteByPropertyId(propertyId);
        monthlyReadingRepository.deleteByPropertyId(propertyId);
        inboundContractRepository.deleteByPropertyId(propertyId);

        // renovation_lines.session_id → renovation_sessions: xóa lines TRƯỚC sessions
        renovationLineRepository.deleteByPropertyId(propertyId);
        entityManager.flush();
        renovationSessionRepository.deleteByPropertyId(propertyId);

        equipmentManifestRepository.deleteByPropertyId(propertyId);
        jdbcTemplate.update("DELETE FROM property_images WHERE property_id = ?", propertyId);
        roomRepository.deleteAllByPropertyId(propertyId);
    }
}
