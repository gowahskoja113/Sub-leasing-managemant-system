package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.response.PropertyPurgeResponse;
import com.sep490.slms2026.entity.InboundContract;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.enums.RoomStatus;
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
    private final TenantContractRepository tenantContractRepository;
    private final EquipmentRepository equipmentRepository;
    private final EquipmentManifestRepository equipmentManifestRepository;
    private final RenovationLineRepository renovationLineRepository;
    private final RenovationSessionRepository renovationSessionRepository;
    private final RoomRepository roomRepository;
    private final InboundContractRepository inboundContractRepository;
    private final DepreciationResultRepository depreciationResultRepository;
    private final MonthlyReadingRepository monthlyReadingRepository;
    private final HandoverEquipmentRepository handoverEquipmentRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public void assertNoActiveTenants(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tòa nhà ID=" + propertyId));

        if (Boolean.TRUE.equals(property.getWholeHouse())) {
            if (tenantContractRepository.existsByPropertyIdAndRoomIsNullAndStatus(
                    propertyId, ContractStatus.ACTIVE)) {
                throw new BusinessException("Còn khách thuê — không thể vô hiệu/xóa");
            }
            return;
        }

        long rentedCount = roomRepository.countByPropertyIdAndStatus(propertyId, RoomStatus.RENTED);
        if (rentedCount > 0) {
            throw new BusinessException(
                    "Còn " + rentedCount + " phòng đang có khách thuê — không thể vô hiệu/xóa");
        }
    }

    @Override
    @Transactional
    public PropertyPurgeResponse purgeProperty(Long propertyId) {
        var nameAndStatus = propertyRepository.findNameAndStatusById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tòa nhà ID=" + propertyId));

        String propertyName = nameAndStatus.getPropertyName();

        assertNoActiveTenants(propertyId);
        if (monthlyReadingRepository.existsByPropertyId(propertyId)) {
            throw new BusinessException(
                    "Không thể xóa căn nhà đã có chỉ số điện nước. Chỉ dùng cho dữ liệu onboarding/import sai.");
        }

        String contractCode = inboundContractRepository.findFirstByPropertyIdOrderByIdDesc(propertyId)
                .map(InboundContract::getContractCode)
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
        handoverEquipmentRepository.deleteByPropertyId(propertyId);
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
