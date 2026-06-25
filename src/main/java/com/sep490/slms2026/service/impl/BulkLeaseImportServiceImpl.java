package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.*;
import com.sep490.slms2026.dto.response.BulkImportContractResultResponse;
import com.sep490.slms2026.dto.response.BulkImportErrorResponse;
import com.sep490.slms2026.dto.response.BulkImportResponse;
import com.sep490.slms2026.entity.*;
import com.sep490.slms2026.enums.EquipmentStatus;
import com.sep490.slms2026.enums.PropertyStatus;
import com.sep490.slms2026.exception.BulkImportValidationException;
import com.sep490.slms2026.imports.*;
import com.sep490.slms2026.repository.EquipmentCatalogRepository;
import com.sep490.slms2026.repository.HandoverEquipmentRepository;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.service.BulkLeaseImportService;
import com.sep490.slms2026.service.InboundContractService;
import com.sep490.slms2026.service.PropertyOnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static com.sep490.slms2026.imports.BulkImportSupport.*;
import static com.sep490.slms2026.imports.ExcelLeaseImportWorkbookReader.*;

@Service
@RequiredArgsConstructor
public class BulkLeaseImportServiceImpl implements BulkLeaseImportService {

    private final ExcelLeaseImportWorkbookReader workbookReader;
    private final BulkImportSupport bulkImportSupport;
    private final PropertyOnboardingService propertyOnboardingService;
    private final InboundContractService inboundContractService;
    private final EquipmentCatalogRepository equipmentCatalogRepository;
    private final HandoverEquipmentRepository handoverEquipmentRepository;
    private final PropertyRepository propertyRepository;
    private final ZoneImportResolver zoneImportResolver;

    @Override
    @Transactional
    public BulkImportResponse importLeaseWorkbook(MultipartFile file, boolean dryRun) {
        LeaseImportWorkbook workbook = workbookReader.read(file);
        Map<String, String> skippedContracts = bulkImportSupport.resolveSkippedLeaseContracts(
                workbook.getLeaseContracts());
        List<BulkImportErrorResponse> errors = validate(workbook, skippedContracts);

        if (!errors.isEmpty()) {
            throw new BulkImportValidationException("File Excel có lỗi validation", errors);
        }

        int importableCount = countImportable(workbook, skippedContracts);
        int handoverCount = countHandoverForImport(workbook, skippedContracts);

        if (dryRun) {
            return BulkImportResponse.builder()
                    .dryRun(true)
                    .contractsProcessed(importableCount)
                    .contractsSkipped(skippedContracts.size())
                    .renovationLinesImported(0)
                    .equipmentRowsImported(handoverCount)
                    .results(buildDryRunResults(workbook, skippedContracts))
                    .errors(List.of())
                    .build();
        }

        Map<String, List<HandoverEquipmentImportRow>> handoverByContract = workbook.getHandoverRows().stream()
                .collect(Collectors.groupingBy(HandoverEquipmentImportRow::getContractCode));

        List<BulkImportContractResultResponse> results = new ArrayList<>();
        int importedCount = 0;

        for (LeaseContractImportRow leaseRow : workbook.getLeaseContracts()) {
            String contractCode = leaseRow.getContractCode();
            if (skippedContracts.containsKey(contractCode)) {
                results.add(buildSkippedResult(contractCode, skippedContracts.get(contractCode), leaseRow));
                continue;
            }

            results.add(importContract(
                    leaseRow,
                    handoverByContract.getOrDefault(contractCode, List.of())));
            importedCount++;
        }

        return BulkImportResponse.builder()
                .dryRun(false)
                .contractsProcessed(importedCount)
                .contractsSkipped(skippedContracts.size())
                .renovationLinesImported(0)
                .equipmentRowsImported(handoverCount)
                .results(results)
                .errors(List.of())
                .build();
    }

    private BulkImportContractResultResponse importContract(LeaseContractImportRow leaseRow,
                                                            List<HandoverEquipmentImportRow> handoverRows) {
        Zone districtZone = zoneImportResolver.resolveDistrictZone(
                leaseRow.getProvince(), leaseRow.getDistrict());
        String shortAddress = leaseRow.getAddress() == null ? "" : leaseRow.getAddress().trim();

        var draftRequest = new PropertyDraftRequest();
        draftRequest.setPropertyName(leaseRow.getPropertyName());
        draftRequest.setAddress(shortAddress);
        draftRequest.setDescriptions(leaseRow.getDescriptions());
        draftRequest.setZoneId(districtZone.getId());
        draftRequest.setAreaSize(leaseRow.getAreaSize());
        draftRequest.setTotalFloor(leaseRow.getTotalFloor());
        draftRequest.setTotalRooms(leaseRow.getTotalRooms());

        var propertyResponse = propertyOnboardingService.createDraft(draftRequest);
        Long propertyId = propertyResponse.getId();

        var contractRequest = CreateInboundContractRequest.builder()
                .contractCode(leaseRow.getContractCode())
                .ownerName(leaseRow.getOwnerName())
                .totalRentAmount(leaseRow.getTotalRentAmount())
                .startDate(leaseRow.getStartDate())
                .endDate(leaseRow.getEndDate())
                .build();
        inboundContractService.signContract(propertyId, contractRequest);

        boolean hasRenovation = ImportContractClassifier.expectsPhase2(leaseRow.getContractCode());

        var optionsRequest = new OnboardingOptionsRequest();
        optionsRequest.setWholeHouse(true);
        optionsRequest.setHasRenovation(hasRenovation);
        propertyOnboardingService.setOnboardingOptions(propertyId, optionsRequest);

        saveHandoverEquipment(propertyId, handoverRows);

        if (!hasRenovation) {
            Property property = propertyRepository.findById(propertyId).orElseThrow();
            property.setStatus(PropertyStatus.RENOVATION_COMPLETED);
            propertyRepository.save(property);
            propertyOnboardingService.submitToHost(propertyId);
        }

        Property property = propertyRepository.findById(propertyId).orElseThrow();
        return BulkImportContractResultResponse.builder()
                .importStatus(IMPORT_STATUS_IMPORTED)
                .contractCode(leaseRow.getContractCode())
                .propertyId(propertyId)
                .propertyName(property.getPropertyName())
                .finalStatus(property.getStatus().name())
                .build();
    }

    private void saveHandoverEquipment(Long propertyId, List<HandoverEquipmentImportRow> handoverRows) {
        Property property = propertyRepository.findById(propertyId).orElseThrow();
        for (HandoverEquipmentImportRow row : handoverRows) {
            EquipmentCatalog catalog = equipmentCatalogRepository
                    .findFirstByNameIgnoreCaseAndActiveTrue(row.getEquipmentName())
                    .orElseThrow();

            String locationNote = normalizeOptional(row.getLocationNote());
            String note = normalizeOptional(row.getNote());
            String combinedNote = locationNote.isBlank() ? note
                    : note.isBlank() ? locationNote : locationNote + " — " + note;

            handoverEquipmentRepository.save(HandoverEquipment.builder()
                    .property(property)
                    .catalog(catalog)
                    .description(normalizeOptional(row.getDescription()))
                    .roomNumber(null)
                    .houseArea(null)
                    .status(EquipmentStatus.valueOf(normalizeOptional(row.getStatusRaw())))
                    .quantity(row.getQuantity())
                    .note(combinedNote.isBlank() ? null : combinedNote)
                    .build());
        }
    }

    private List<BulkImportErrorResponse> validate(LeaseImportWorkbook workbook,
                                                   Map<String, String> skippedContracts) {
        List<BulkImportErrorResponse> errors = new ArrayList<>();

        if (workbook.getLeaseContracts().isEmpty()) {
            errors.add(error(SHEET_LEASE, 1, null, null, "Không có dòng hợp đồng thuê nào để import"));
            return errors;
        }

        Set<String> contractCodes = new HashSet<>();
        Map<String, LeaseContractImportRow> leaseByCode = new LinkedHashMap<>();

        for (LeaseContractImportRow row : workbook.getLeaseContracts()) {
            validateLeaseRow(row, contractCodes, skippedContracts, errors);
            leaseByCode.put(row.getContractCode(), row);
        }

        for (HandoverEquipmentImportRow row : workbook.getHandoverRows()) {
            if (skippedContracts.containsKey(row.getContractCode())) {
                continue;
            }
            validateHandoverRow(row, leaseByCode, errors);
        }

        return errors;
    }

    private void validateLeaseRow(LeaseContractImportRow row,
                                  Set<String> contractCodes,
                                  Map<String, String> skippedContracts,
                                  List<BulkImportErrorResponse> errors) {
        boolean willSkip = skippedContracts.containsKey(normalizeOptional(row.getContractCode()));

        requireText(errors, SHEET_LEASE, row.getRowNumber(), row.getContractCode(), "Mã hợp đồng", row.getContractCode());
        if (willSkip) {
            if (!contractCodes.add(row.getContractCode())) {
                errors.add(error(SHEET_LEASE, row.getRowNumber(), row.getContractCode(), "Mã hợp đồng",
                        "Mã hợp đồng bị trùng trong file"));
            }
            return;
        }

        requireText(errors, SHEET_LEASE, row.getRowNumber(), row.getContractCode(), "Tên tòa nhà", row.getPropertyName());
        requireText(errors, SHEET_LEASE, row.getRowNumber(), row.getContractCode(), "Địa chỉ chi tiết", row.getAddress());
        requireText(errors, SHEET_LEASE, row.getRowNumber(), row.getContractCode(), "Quận/Huyện", row.getDistrict());
        requireText(errors, SHEET_LEASE, row.getRowNumber(), row.getContractCode(), "Tỉnh/Thành phố", row.getProvince());
        requireText(errors, SHEET_LEASE, row.getRowNumber(), row.getContractCode(), "Tên chủ nhà", row.getOwnerName());
        requireText(errors, SHEET_LEASE, row.getRowNumber(), row.getContractCode(), "Mô tả chi tiết", row.getDescriptions());

        if (!contractCodes.add(row.getContractCode())) {
            errors.add(error(SHEET_LEASE, row.getRowNumber(), row.getContractCode(), "Mã hợp đồng",
                    "Mã hợp đồng bị trùng trong file"));
        }

        if (row.getAreaSize() == null || row.getAreaSize() <= 0) {
            errors.add(error(SHEET_LEASE, row.getRowNumber(), row.getContractCode(), "Diện tích (m²)",
                    "Diện tích phải lớn hơn 0"));
        }
        if (row.getTotalFloor() == null || row.getTotalFloor() <= 0) {
            errors.add(error(SHEET_LEASE, row.getRowNumber(), row.getContractCode(), "Tổng số tầng",
                    "Tổng số tầng phải lớn hơn 0"));
        }
        if (row.getTotalRooms() == null || row.getTotalRooms() <= 0) {
            errors.add(error(SHEET_LEASE, row.getRowNumber(), row.getContractCode(), "Tổng số phòng",
                    "Tổng số phòng phải lớn hơn 0"));
        }
        if (row.getTotalRentAmount() == null || row.getTotalRentAmount().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add(error(SHEET_LEASE, row.getRowNumber(), row.getContractCode(), "Tổng tiền thuê",
                    "Tổng tiền thuê phải lớn hơn 0"));
        }
        if (row.getStartDate() == null) {
            errors.add(error(SHEET_LEASE, row.getRowNumber(), row.getContractCode(), "Ngày bắt đầu",
                    "Ngày bắt đầu không hợp lệ (YYYY-MM-DD)"));
        }
        if (row.getEndDate() == null) {
            errors.add(error(SHEET_LEASE, row.getRowNumber(), row.getContractCode(), "Ngày kết thúc",
                    "Ngày kết thúc không hợp lệ (YYYY-MM-DD)"));
        }
        if (row.getStartDate() != null && row.getEndDate() != null && !row.getEndDate().isAfter(row.getStartDate())) {
            errors.add(error(SHEET_LEASE, row.getRowNumber(), row.getContractCode(), "Ngày kết thúc",
                    "Ngày kết thúc phải sau ngày bắt đầu"));
        }

        try {
            zoneImportResolver.resolveDistrictZone(row.getProvince(), row.getDistrict());
        } catch (IllegalArgumentException ex) {
            errors.add(error(SHEET_LEASE, row.getRowNumber(), row.getContractCode(),
                    "Quận/Huyện / Tỉnh/Thành phố", ex.getMessage()));
        }
    }

    private void validateHandoverRow(HandoverEquipmentImportRow row,
                                     Map<String, LeaseContractImportRow> leaseByCode,
                                     List<BulkImportErrorResponse> errors) {
        if (!leaseByCode.containsKey(row.getContractCode())) {
            errors.add(error(SHEET_HANDOVER, row.getRowNumber(), row.getContractCode(), "Mã hợp đồng thuê",
                    "Không tìm thấy mã hợp đồng ở sheet 1"));
            return;
        }

        requireText(errors, SHEET_HANDOVER, row.getRowNumber(), row.getContractCode(),
                "Tên thiết bị", row.getEquipmentName());
        equipmentCatalogRepository.findFirstByNameIgnoreCaseAndActiveTrue(row.getEquipmentName())
                .orElseGet(() -> {
                    errors.add(error(SHEET_HANDOVER, row.getRowNumber(), row.getContractCode(),
                            "Tên thiết bị", "Không tìm thấy catalog trong hệ thống"));
                    return null;
                });

        try {
            EquipmentStatus.valueOf(normalizeOptional(row.getStatusRaw()));
        } catch (IllegalArgumentException ex) {
            errors.add(error(SHEET_HANDOVER, row.getRowNumber(), row.getContractCode(), "Trạng thái thiết bị",
                    "Giá trị enum không hợp lệ (NEW, GOOD, DAMAGED, BROKEN)"));
        }

        if (row.getQuantity() == null || row.getQuantity() <= 0) {
            errors.add(error(SHEET_HANDOVER, row.getRowNumber(), row.getContractCode(), "Số lượng",
                    "Số lượng phải là số nguyên dương"));
        }
    }

    private int countImportable(LeaseImportWorkbook workbook, Map<String, String> skippedContracts) {
        return (int) workbook.getLeaseContracts().stream()
                .map(LeaseContractImportRow::getContractCode)
                .filter(code -> !skippedContracts.containsKey(code))
                .count();
    }

    private int countHandoverForImport(LeaseImportWorkbook workbook, Map<String, String> skippedContracts) {
        return (int) workbook.getHandoverRows().stream()
                .filter(row -> !skippedContracts.containsKey(row.getContractCode()))
                .count();
    }

    private List<BulkImportContractResultResponse> buildDryRunResults(LeaseImportWorkbook workbook,
                                                                      Map<String, String> skippedContracts) {
        List<BulkImportContractResultResponse> results = new ArrayList<>();
        for (LeaseContractImportRow row : workbook.getLeaseContracts()) {
            String code = row.getContractCode();
            if (skippedContracts.containsKey(code)) {
                results.add(buildSkippedResult(code, skippedContracts.get(code), row));
            } else {
                results.add(BulkImportContractResultResponse.builder()
                        .importStatus(IMPORT_STATUS_IMPORTED)
                        .contractCode(code)
                        .propertyName(row.getPropertyName())
                        .message("Dry run — sẽ được tạo mới")
                        .build());
            }
        }
        return results;
    }

    private BulkImportContractResultResponse buildSkippedResult(String contractCode,
                                                                String message,
                                                                LeaseContractImportRow row) {
        return BulkImportContractResultResponse.builder()
                .importStatus(IMPORT_STATUS_SKIPPED)
                .contractCode(contractCode)
                .message(message)
                .build();
    }
}
