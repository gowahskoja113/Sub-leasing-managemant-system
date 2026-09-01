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
        // --- con của tenant_invoices (phải trước khi xoá tenant_invoices) ---
        jdbcTemplate.update("DELETE FROM tenant_payments WHERE tenant_invoice_id IN (SELECT id FROM tenant_invoices WHERE tenant_contract_id IN (SELECT id FROM tenant_contracts WHERE property_id = ?))", propertyId);
        jdbcTemplate.update("DELETE FROM tenant_invoice_payos_orders WHERE invoice_id IN (SELECT id FROM tenant_invoices WHERE tenant_contract_id IN (SELECT id FROM tenant_contracts WHERE property_id = ?))", propertyId);
        jdbcTemplate.update("DELETE FROM invoice_dispute_photos WHERE dispute_id IN (SELECT id FROM invoice_disputes WHERE tenant_contract_id IN (SELECT id FROM tenant_contracts WHERE property_id = ?))", propertyId);
        jdbcTemplate.update("DELETE FROM invoice_disputes WHERE tenant_contract_id IN (SELECT id FROM tenant_contracts WHERE property_id = ?)", propertyId);

        // --- con của tenant_contracts ---
        jdbcTemplate.update("DELETE FROM checkout_settlement_invoices WHERE checkout_settlement_id IN (SELECT s.id FROM checkout_settlements s JOIN checkout_requests r ON s.checkout_request_id = r.id WHERE r.tenant_contract_id IN (SELECT id FROM tenant_contracts WHERE property_id = ?))", propertyId);
        jdbcTemplate.update("DELETE FROM checkout_settlement_adjustments WHERE checkout_settlement_id IN (SELECT s.id FROM checkout_settlements s JOIN checkout_requests r ON s.checkout_request_id = r.id WHERE r.tenant_contract_id IN (SELECT id FROM tenant_contracts WHERE property_id = ?))", propertyId);
        jdbcTemplate.update("DELETE FROM checkout_damage_items WHERE checkout_inspection_id IN (SELECT i.id FROM checkout_inspections i JOIN checkout_requests r ON i.checkout_request_id = r.id WHERE r.tenant_contract_id IN (SELECT id FROM tenant_contracts WHERE property_id = ?))", propertyId);
        jdbcTemplate.update("DELETE FROM checkout_inspection_photos WHERE inspection_id IN (SELECT i.id FROM checkout_inspections i JOIN checkout_requests r ON i.checkout_request_id = r.id WHERE r.tenant_contract_id IN (SELECT id FROM tenant_contracts WHERE property_id = ?))", propertyId);
        jdbcTemplate.update("DELETE FROM checkout_settlements WHERE checkout_request_id IN (SELECT id FROM checkout_requests WHERE tenant_contract_id IN (SELECT id FROM tenant_contracts WHERE property_id = ?))", propertyId);
        jdbcTemplate.update("DELETE FROM checkout_inspections WHERE checkout_request_id IN (SELECT id FROM checkout_requests WHERE tenant_contract_id IN (SELECT id FROM tenant_contracts WHERE property_id = ?))", propertyId);
        jdbcTemplate.update("DELETE FROM checkout_request_dispute_photos WHERE checkout_request_id IN (SELECT id FROM checkout_requests WHERE tenant_contract_id IN (SELECT id FROM tenant_contracts WHERE property_id = ?))", propertyId);
        jdbcTemplate.update("DELETE FROM checkout_requests WHERE tenant_contract_id IN (SELECT id FROM tenant_contracts WHERE property_id = ?)", propertyId);
        jdbcTemplate.update("DELETE FROM tenant_pending_charges WHERE tenant_contract_id IN (SELECT id FROM tenant_contracts WHERE property_id = ?)", propertyId);

        // --- bảo trì (con trước, cha sau) ---
        jdbcTemplate.update("DELETE FROM outstanding_damage_photos WHERE record_id IN (SELECT id FROM outstanding_damage_records WHERE maintenance_request_id IN (SELECT id FROM maintenance_requests WHERE property_id = ?))", propertyId);
        jdbcTemplate.update("DELETE FROM outstanding_damage_records WHERE maintenance_request_id IN (SELECT id FROM maintenance_requests WHERE property_id = ?)", propertyId);
        jdbcTemplate.update("DELETE FROM maintenance_images WHERE maintenance_request_id IN (SELECT id FROM maintenance_requests WHERE property_id = ?)", propertyId);
        jdbcTemplate.update("DELETE FROM maintenance_timelines WHERE maintenance_request_id IN (SELECT id FROM maintenance_requests WHERE property_id = ?)", propertyId);
        jdbcTemplate.update("DELETE FROM maintenance_history WHERE maintenance_request_id IN (SELECT id FROM maintenance_requests WHERE property_id = ?)", propertyId);
        jdbcTemplate.update("DELETE FROM equipment_maintenance_histories WHERE maintenance_request_id IN (SELECT id FROM maintenance_requests WHERE property_id = ?) OR equipment_id IN (SELECT id FROM equipments WHERE property_id = ?)", propertyId, propertyId);
        jdbcTemplate.update("DELETE FROM maintenance_requests WHERE property_id = ?", propertyId);

        // --- điện nước / tài chính / lead theo property ---
        jdbcTemplate.update("DELETE FROM utility_invoices WHERE property_id = ?", propertyId);
        jdbcTemplate.update("DELETE FROM utility_bills WHERE property_id = ?", propertyId);
        jdbcTemplate.update("DELETE FROM meter_readings WHERE property_id = ?", propertyId);
        jdbcTemplate.update("DELETE FROM invoices WHERE property_id = ?", propertyId);
        jdbcTemplate.update("DELETE FROM expenses WHERE property_id = ?", propertyId);
        jdbcTemplate.update("DELETE FROM host_expenses WHERE property_id = ?", propertyId);
        jdbcTemplate.update("DELETE FROM master_leases WHERE property_id = ?", propertyId);
        jdbcTemplate.update("DELETE FROM viewing_lead_properties WHERE property_id = ?", propertyId);

        jdbcTemplate.update("DELETE FROM tenant_payment_claims WHERE tenant_invoice_id IN (SELECT id FROM tenant_invoices WHERE tenant_contract_id IN (SELECT id FROM tenant_contracts WHERE property_id = ?))", propertyId);
        jdbcTemplate.update("DELETE FROM tenant_invoices WHERE tenant_contract_id IN (SELECT id FROM tenant_contracts WHERE property_id = ?)", propertyId);
        jdbcTemplate.update("DELETE FROM household_members WHERE tenant_contract_id IN (SELECT id FROM tenant_contracts WHERE property_id = ?)", propertyId);
        jdbcTemplate.update("DELETE FROM tenant_contract_equipments WHERE tenant_contract_id IN (SELECT id FROM tenant_contracts WHERE property_id = ?)", propertyId);
        jdbcTemplate.update("DELETE FROM tenant_contract_condition_photos WHERE tenant_contract_id IN (SELECT id FROM tenant_contracts WHERE property_id = ?)", propertyId);
        jdbcTemplate.update("DELETE FROM tenant_contracts WHERE property_id = ?", propertyId);

        jdbcTemplate.update(
            "DELETE FROM depreciation_results WHERE room_id IN (SELECT id FROM rooms WHERE property_id = ?) "
          + "OR inbound_contract_id IN (SELECT id FROM inbound_contracts WHERE property_id = ?)",
            propertyId, propertyId);
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
