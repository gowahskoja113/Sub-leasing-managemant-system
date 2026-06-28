package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.*;
import com.sep490.slms2026.dto.response.BulkImportContractResultResponse;
import com.sep490.slms2026.dto.response.BulkImportErrorResponse;
import com.sep490.slms2026.dto.response.BulkImportResponse;
import com.sep490.slms2026.dto.response.PropertyPurgeResponse;
import com.sep490.slms2026.entity.EquipmentCatalog;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.RenovationCategory;
import com.sep490.slms2026.entity.Zone;
import com.sep490.slms2026.enums.EquipmentSource;
import com.sep490.slms2026.enums.EquipmentStatus;
import com.sep490.slms2026.enums.HouseArea;
import com.sep490.slms2026.enums.PropertyType;
import com.sep490.slms2026.exception.BulkImportValidationException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.imports.*;
import com.sep490.slms2026.repository.EquipmentCatalogRepository;
import com.sep490.slms2026.repository.InboundContractRepository;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.RenovationCategoryRepository;
import com.sep490.slms2026.repository.RoomRepository;
import com.sep490.slms2026.service.BulkOnboardingImportService;
import com.sep490.slms2026.service.InboundContractService;
import com.sep490.slms2026.service.PropertyOnboardingService;
import com.sep490.slms2026.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BulkOnboardingImportServiceImpl implements BulkOnboardingImportService {

    private static final String SHEET_LEASE = "1. Hop_Dong_Thue";
    private static final String SHEET_RENOVATION = "2. Hop_Dong_Cai_Tao";
    private static final String SHEET_EQUIPMENT = "3. Phan_Bo_Thiet_Bi";
    private static final String IMPORT_STATUS_IMPORTED = "IMPORTED";
    private static final String IMPORT_STATUS_SKIPPED = "SKIPPED";
    private static final String SKIP_REASON_DUPLICATE_CONTRACT = "Mã hợp đồng đã tồn tại — bỏ qua";
    private static final String SKIP_REASON_DUPLICATE_ADDRESS = "Địa chỉ đã được dùng cho tòa nhà khác — bỏ qua";
    private static final String SKIP_REASON_DUPLICATE_ADDRESS_IN_FILE = "Địa chỉ bị trùng trong file — bỏ qua";

    private final ExcelOnboardingWorkbookReader workbookReader;
    private final PropertyOnboardingService propertyOnboardingService;
    private final InboundContractService inboundContractService;
    private final RoomService roomService;
    private final RenovationCategoryRepository renovationCategoryRepository;
    private final EquipmentCatalogRepository equipmentCatalogRepository;
    private final InboundContractRepository inboundContractRepository;
    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final ZoneImportResolver zoneImportResolver;

    @Override
    @Transactional
    public BulkImportResponse importWorkbook(MultipartFile file, boolean dryRun) {
        OnboardingImportWorkbook workbook = workbookReader.read(file);
        Map<String, String> skippedContracts = resolveSkippedContracts(workbook);
        List<BulkImportErrorResponse> errors = validate(workbook, skippedContracts);

        if (!errors.isEmpty()) {
            throw new BulkImportValidationException("File Excel có lỗi validation", errors);
        }

        int importableCount = countImportableContracts(workbook, skippedContracts);

        if (dryRun) {
            return BulkImportResponse.builder()
                    .dryRun(true)
                    .contractsProcessed(importableCount)
                    .contractsSkipped(skippedContracts.size())
                    .renovationLinesImported(countRenovationLinesForImport(workbook, skippedContracts))
                    .equipmentRowsImported(countEquipmentRowsForImport(workbook, skippedContracts))
                    .results(buildDryRunResults(workbook, skippedContracts))
                    .errors(List.of())
                    .build();
        }

        Map<String, List<RenovationImportRow>> renovationsByContract = workbook.getRenovationLines().stream()
                .collect(Collectors.groupingBy(RenovationImportRow::getContractCode));
        Map<String, List<EquipmentImportRow>> equipmentByContract = workbook.getEquipmentRows().stream()
                .collect(Collectors.groupingBy(EquipmentImportRow::getContractCode));

        List<BulkImportContractResultResponse> results = new ArrayList<>();
        int renovationCount = 0;
        int equipmentCount = 0;
        int importedCount = 0;

        for (LeaseContractImportRow leaseRow : workbook.getLeaseContracts()) {
            String contractCode = leaseRow.getContractCode();
            if (skippedContracts.containsKey(contractCode)) {
                results.add(buildSkippedResult(
                        contractCode,
                        skippedContracts.get(contractCode),
                        tryBuildFullAddress(leaseRow)));
                continue;
            }

            BulkImportContractResultResponse result = importContract(
                    leaseRow,
                    renovationsByContract.getOrDefault(contractCode, List.of()),
                    equipmentByContract.getOrDefault(contractCode, List.of()));
            results.add(result);
            importedCount++;
            renovationCount += renovationsByContract.getOrDefault(contractCode, List.of()).size();
            equipmentCount += equipmentByContract.getOrDefault(contractCode, List.of()).size();
        }

        return BulkImportResponse.builder()
                .dryRun(false)
                .contractsProcessed(importedCount)
                .contractsSkipped(skippedContracts.size())
                .renovationLinesImported(renovationCount)
                .equipmentRowsImported(equipmentCount)
                .results(results)
                .errors(List.of())
                .build();
    }

    @Override
    @Transactional
    public PropertyPurgeResponse purgeByContractCode(String contractCode) {
        Long propertyId = inboundContractRepository.findByContractCode(contractCode)
                .map(contract -> contract.getProperty().getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy hợp đồng inbound với mã: " + contractCode));
        return propertyOnboardingService.purgeProperty(propertyId);
    }

    private BulkImportContractResultResponse importContract(LeaseContractImportRow leaseRow,
                                                            List<RenovationImportRow> renovationRows,
                                                            List<EquipmentImportRow> equipmentRows) {
        Zone districtZone = zoneImportResolver.resolveDistrictZone(
                leaseRow.getProvince(), leaseRow.getDistrict());
        String shortAddress = leaseRow.getAddress() == null ? "" : leaseRow.getAddress().trim();

        var draftRequest = new PropertyDraftRequest();
        draftRequest.setPropertyName(leaseRow.getPropertyName());
        draftRequest.setAddress(shortAddress);
        draftRequest.setDescriptions(leaseRow.getDescriptions());
        draftRequest.setZoneId(districtZone.getId());
        draftRequest.setAreaSize(leaseRow.getAreaSize());
        draftRequest.setLength(leaseRow.getLength());
        draftRequest.setWidth(leaseRow.getWidth());
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

        boolean wholeHouse = ImportContractClassifier.isWholeHouse(
                leaseRow.getContractCode(), leaseRow.getDescriptions());
        boolean hasRenovation = !renovationRows.isEmpty();

        var optionsRequest = new OnboardingOptionsRequest();
        optionsRequest.setWholeHouse(wholeHouse);
        optionsRequest.setHasRenovation(hasRenovation);
        propertyOnboardingService.setOnboardingOptions(propertyId, optionsRequest);

        for (RenovationImportRow renovationRow : renovationRows) {
            RenovationCategory category = renovationCategoryRepository.findByCode(renovationRow.getCategoryCode())
                    .orElseThrow();
            var renovationRequest = new AddRenovationLineRequest();
            renovationRequest.setCategoryId(category.getId());
            renovationRequest.setCost(renovationRow.getCost());
            renovationRequest.setNote(renovationRow.getNote());
            propertyOnboardingService.addRenovationLine(propertyId, renovationRequest);
        }

        Map<String, Long> roomIdByNumber = createRoomsForContract(
                propertyId, leaseRow, equipmentRows, wholeHouse);

        List<EquipmentManifestItemRequest> manifestItems = buildManifestItems(equipmentRows);
        var manifestRequest = new SaveEquipmentManifestRequest();
        manifestRequest.setItems(manifestItems);
        propertyOnboardingService.saveEquipmentManifest(propertyId, manifestRequest);

        for (EquipmentImportRow equipmentRow : equipmentRows) {
            AssignEquipmentRequest assignRequest = buildAssignRequest(equipmentRow, roomIdByNumber);
            propertyOnboardingService.assignEquipment(propertyId, assignRequest);
        }

        propertyOnboardingService.completeRenovation(propertyId);

        Property property = propertyRepository.findById(propertyId).orElseThrow();
        return BulkImportContractResultResponse.builder()
                .importStatus(IMPORT_STATUS_IMPORTED)
                .contractCode(leaseRow.getContractCode())
                .propertyId(propertyId)
                .propertyName(property.getPropertyName())
                .finalStatus(property.getStatus().name())
                .build();
    }

    private Map<String, String> resolveSkippedContracts(OnboardingImportWorkbook workbook) {
        Map<String, String> skipped = new LinkedHashMap<>();
        Set<String> seenAddressesInFile = new HashSet<>();

        for (LeaseContractImportRow row : workbook.getLeaseContracts()) {
            String code = normalizeOptional(row.getContractCode());
            if (code.isBlank() || skipped.containsKey(code)) {
                continue;
            }

            if (inboundContractRepository.existsByContractCode(code)) {
                skipped.put(code, SKIP_REASON_DUPLICATE_CONTRACT);
                continue;
            }

            String fullAddress = tryBuildFullAddress(row);
            if (fullAddress == null) {
                continue;
            }

            if (propertyRepository.existsByAddressIgnoreCase(fullAddress)) {
                skipped.put(code, SKIP_REASON_DUPLICATE_ADDRESS);
                continue;
            }

            if (!seenAddressesInFile.add(fullAddress.toLowerCase(Locale.ROOT))) {
                skipped.put(code, SKIP_REASON_DUPLICATE_ADDRESS_IN_FILE);
            }
        }
        return skipped;
    }

    private String tryBuildFullAddress(LeaseContractImportRow row) {
        try {
            Zone districtZone = zoneImportResolver.resolveDistrictZone(row.getProvince(), row.getDistrict());
            String shortAddress = row.getAddress() == null ? "" : row.getAddress().trim();
            return ZoneImportResolver.buildFullAddress(shortAddress, districtZone);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private int countImportableContracts(OnboardingImportWorkbook workbook, Map<String, String> skippedContracts) {
        return (int) workbook.getLeaseContracts().stream()
                .map(LeaseContractImportRow::getContractCode)
                .filter(code -> !skippedContracts.containsKey(code))
                .count();
    }

    private int countRenovationLinesForImport(OnboardingImportWorkbook workbook, Map<String, String> skippedContracts) {
        return (int) workbook.getRenovationLines().stream()
                .filter(row -> !skippedContracts.containsKey(row.getContractCode()))
                .count();
    }

    private int countEquipmentRowsForImport(OnboardingImportWorkbook workbook, Map<String, String> skippedContracts) {
        return (int) workbook.getEquipmentRows().stream()
                .filter(row -> !skippedContracts.containsKey(row.getContractCode()))
                .count();
    }

    private List<BulkImportContractResultResponse> buildDryRunResults(OnboardingImportWorkbook workbook,
                                                                        Map<String, String> skippedContracts) {
        List<BulkImportContractResultResponse> results = new ArrayList<>();
        for (LeaseContractImportRow row : workbook.getLeaseContracts()) {
            String code = row.getContractCode();
            if (skippedContracts.containsKey(code)) {
                results.add(buildSkippedResult(code, skippedContracts.get(code), tryBuildFullAddress(row)));
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
                                                                String fullAddress) {
        if (SKIP_REASON_DUPLICATE_CONTRACT.equals(message)) {
            return inboundContractRepository.findByContractCode(contractCode)
                    .map(contract -> {
                        Property property = contract.getProperty();
                        return BulkImportContractResultResponse.builder()
                                .importStatus(IMPORT_STATUS_SKIPPED)
                                .contractCode(contractCode)
                                .propertyId(property.getId())
                                .propertyName(property.getPropertyName())
                                .finalStatus(property.getStatus().name())
                                .message(message)
                                .build();
                    })
                    .orElseGet(() -> BulkImportContractResultResponse.builder()
                            .importStatus(IMPORT_STATUS_SKIPPED)
                            .contractCode(contractCode)
                            .message(message)
                            .build());
        }

        if (fullAddress != null) {
            return propertyRepository.findFirstByAddressIgnoreCase(fullAddress)
                    .map(property -> BulkImportContractResultResponse.builder()
                            .importStatus(IMPORT_STATUS_SKIPPED)
                            .contractCode(contractCode)
                            .propertyId(property.getId())
                            .propertyName(property.getPropertyName())
                            .finalStatus(property.getStatus().name())
                            .message(message)
                            .build())
                    .orElseGet(() -> BulkImportContractResultResponse.builder()
                            .importStatus(IMPORT_STATUS_SKIPPED)
                            .contractCode(contractCode)
                            .message(message)
                            .build());
        }

        return BulkImportContractResultResponse.builder()
                .importStatus(IMPORT_STATUS_SKIPPED)
                .contractCode(contractCode)
                .message(message)
                .build();
    }

    private List<BulkImportErrorResponse> validate(OnboardingImportWorkbook workbook,
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

        for (RenovationImportRow row : workbook.getRenovationLines()) {
            if (skippedContracts.containsKey(row.getContractCode())) {
                continue;
            }
            validateRenovationRow(row, leaseByCode, errors);
        }

        for (EquipmentImportRow row : workbook.getEquipmentRows()) {
            if (skippedContracts.containsKey(row.getContractCode())) {
                continue;
            }
            validateEquipmentRow(row, leaseByCode, errors);
        }

        for (LeaseContractImportRow leaseRow : workbook.getLeaseContracts()) {
            if (skippedContracts.containsKey(leaseRow.getContractCode())) {
                continue;
            }
            long renovationCount = workbook.getRenovationLines().stream()
                    .filter(line -> line.getContractCode().equals(leaseRow.getContractCode()))
                    .count();
            boolean expectsRenovation = ImportContractClassifier.inferHasRenovationForPhase1(
                    leaseRow.getContractCode());
            if (!expectsRenovation && renovationCount > 0) {
                errors.add(error(SHEET_LEASE, leaseRow.getRowNumber(), leaseRow.getContractCode(),
                        "Mã hợp đồng",
                        "Hợp đồng NORENO nhưng sheet 2 vẫn có chi phí cải tạo"));
            }
            if (expectsRenovation && renovationCount == 0) {
                errors.add(error(SHEET_LEASE, leaseRow.getRowNumber(), leaseRow.getContractCode(),
                        "Mã hợp đồng",
                        "Hợp đồng cần cải tạo nhưng sheet 2 không có dòng cải tạo"));
            }
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
        if (row.getLength() == null || row.getLength() <= 0) {
            errors.add(error(SHEET_LEASE, row.getRowNumber(), row.getContractCode(), "Chiều dài (m)",
                    "Chiều dài phải lớn hơn 0"));
        }
        if (row.getWidth() == null || row.getWidth() <= 0) {
            errors.add(error(SHEET_LEASE, row.getRowNumber(), row.getContractCode(), "Chiều rộng (m)",
                    "Chiều rộng phải lớn hơn 0"));
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

    private void validateRenovationRow(RenovationImportRow row,
                                       Map<String, LeaseContractImportRow> leaseByCode,
                                       List<BulkImportErrorResponse> errors) {
        if (!leaseByCode.containsKey(row.getContractCode())) {
            errors.add(error(SHEET_RENOVATION, row.getRowNumber(), row.getContractCode(), "Mã hợp đồng thuê",
                    "Không tìm thấy mã hợp đồng ở sheet 1"));
            return;
        }

        requireText(errors, SHEET_RENOVATION, row.getRowNumber(), row.getContractCode(),
                "Mã danh mục cải tạo", row.getCategoryCode());

        if (row.getCost() == null || row.getCost().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add(error(SHEET_RENOVATION, row.getRowNumber(), row.getContractCode(),
                    "Chi phí cải tạo (VNĐ)", "Chi phí phải lớn hơn 0"));
        }

        renovationCategoryRepository.findByCode(row.getCategoryCode())
                .orElseGet(() -> {
                    errors.add(error(SHEET_RENOVATION, row.getRowNumber(), row.getContractCode(),
                            "Mã danh mục cải tạo", "Không tìm thấy mã danh mục trong hệ thống"));
                    return null;
                });
    }

    private void validateEquipmentRow(EquipmentImportRow row,
                                        Map<String, LeaseContractImportRow> leaseByCode,
                                        List<BulkImportErrorResponse> errors) {
        if (!leaseByCode.containsKey(row.getContractCode())) {
            errors.add(error(SHEET_EQUIPMENT, row.getRowNumber(), row.getContractCode(), "Mã hợp đồng thuê",
                    "Không tìm thấy mã hợp đồng ở sheet 1"));
            return;
        }

        String roomNumber = normalizeOptional(row.getRoomNumber());
        String houseAreaRaw = normalizeOptional(row.getHouseAreaRaw());
        boolean hasRoom = !roomNumber.isBlank();
        boolean hasArea = !houseAreaRaw.isBlank();

        if (hasRoom == hasArea) {
            errors.add(error(SHEET_EQUIPMENT, row.getRowNumber(), row.getContractCode(), "Vị trí",
                    "Phải điền Số phòng hoặc Khu vực chung, không được điền cả hai hoặc bỏ trống cả hai"));
        }
        if (hasArea) {
            try {
                HouseArea.valueOf(houseAreaRaw);
            } catch (IllegalArgumentException ex) {
                errors.add(error(SHEET_EQUIPMENT, row.getRowNumber(), row.getContractCode(), "Khu vực chung",
                        "Giá trị không hợp lệ. Chọn một trong: LIVING_ROOM, BEDROOM, KITCHEN, BATHROOM, BALCONY, GARAGE, OTHER"));
            }
        }

        requireText(errors, SHEET_EQUIPMENT, row.getRowNumber(), row.getContractCode(),
                "Tên Catalog thiết bị", row.getCatalogName());
        equipmentCatalogRepository.findFirstByNameIgnoreCaseAndActiveTrue(row.getCatalogName())
                .orElseGet(() -> {
                    errors.add(error(SHEET_EQUIPMENT, row.getRowNumber(), row.getContractCode(),
                            "Tên Catalog thiết bị", "Không tìm thấy catalog trong hệ thống"));
                    return null;
                });

        EquipmentSource source;
        try {
            source = EquipmentSource.valueOf(normalizeOptional(row.getSourceRaw()));
        } catch (IllegalArgumentException ex) {
            errors.add(error(SHEET_EQUIPMENT, row.getRowNumber(), row.getContractCode(), "Nguồn gốc thiết bị",
                    "Chỉ chấp nhận INITIAL_HANDOVER hoặc PURCHASED"));
            source = null;
        }

        EquipmentStatus status;
        try {
            status = EquipmentStatus.valueOf(normalizeOptional(row.getStatusRaw()));
        } catch (IllegalArgumentException ex) {
            errors.add(error(SHEET_EQUIPMENT, row.getRowNumber(), row.getContractCode(), "Trạng thái thiết bị",
                    "Giá trị enum không hợp lệ"));
            status = null;
        }
        if (status != null && status != EquipmentStatus.NEW && status != EquipmentStatus.GOOD) {
            errors.add(error(SHEET_EQUIPMENT, row.getRowNumber(), row.getContractCode(), "Trạng thái thiết bị",
                    "Khi import onboarding chỉ chấp nhận NEW hoặc GOOD"));
        }

        if (row.getQuantity() == null || row.getQuantity() <= 0) {
            errors.add(error(SHEET_EQUIPMENT, row.getRowNumber(), row.getContractCode(), "Số lượng",
                    "Số lượng phải là số nguyên dương"));
        }

        if (source == EquipmentSource.PURCHASED) {
            if (row.getPrice() == null || row.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                errors.add(error(SHEET_EQUIPMENT, row.getRowNumber(), row.getContractCode(), "Đơn giá (VNĐ)",
                        "Thiết bị PURCHASED phải có đơn giá > 0"));
            }
        } else if (source == EquipmentSource.INITIAL_HANDOVER) {
            if (row.getPrice() != null && row.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                errors.add(error(SHEET_EQUIPMENT, row.getRowNumber(), row.getContractCode(), "Đơn giá (VNĐ)",
                        "Thiết bị INITIAL_HANDOVER phải có đơn giá = 0"));
            }
        }
    }

    private Map<String, Long> createRoomsForContract(Long propertyId,
                                                     LeaseContractImportRow leaseRow,
                                                     List<EquipmentImportRow> equipmentRows,
                                                     boolean wholeHouse) {
        Set<String> roomNumbers = equipmentRows.stream()
                .map(EquipmentImportRow::getRoomNumber)
                .map(this::normalizeOptional)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (!wholeHouse) {
            ensureRoomNumbersForIndividualProperty(roomNumbers, leaseRow.getTotalRooms());
        }

        Map<String, Long> roomIdByNumber = new HashMap<>();
        PropertyType propertyType = wholeHouse ? PropertyType.WHOLE_HOUSE : PropertyType.INDIVIDUAL_ROOM;
        double defaultArea = Math.max(1.0,
                leaseRow.getAreaSize() / Math.max(leaseRow.getTotalRooms(), 1));
        double defaultLength = wholeHouse && leaseRow.getLength() != null && leaseRow.getLength() > 0
                ? leaseRow.getLength()
                : Math.max(1.0, Math.sqrt(defaultArea * 1.4));
        double defaultWidth = wholeHouse && leaseRow.getWidth() != null && leaseRow.getWidth() > 0
                ? leaseRow.getWidth()
                : Math.max(1.0, defaultArea / defaultLength);

        for (String roomNumber : roomNumbers) {
            if (roomRepository.existsByPropertyIdAndRoomNumberAndDeletedIsFalse(propertyId, roomNumber)) {
                roomRepository.findByPropertyIdAndRoomNumberAndDeletedIsFalse(propertyId, roomNumber)
                        .ifPresent(room -> roomIdByNumber.put(roomNumber, room.getId()));
                continue;
            }

            var addRoomRequest = AddRoomRequest.builder()
                    .roomNumber(roomNumber)
                    .floor(inferFloor(roomNumber, leaseRow.getTotalFloor()))
                    .area(defaultArea)
                    .length(defaultLength)
                    .width(defaultWidth)
                    .maxOccupants(2)
                    .propertyType(propertyType)
                    .build();
            var roomResponse = roomService.addRoom(propertyId, addRoomRequest);
            roomIdByNumber.put(roomNumber, roomResponse.getId());
        }
        return roomIdByNumber;
    }

    /**
     * Nhà chia phòng: đảm bảo đủ {@code totalRooms} phòng (101, 102, …) kể cả sheet 3 chưa khai báo hết.
     */
    private void ensureRoomNumbersForIndividualProperty(Set<String> roomNumbers, Integer totalRooms) {
        if (totalRooms == null || totalRooms <= 0) {
            return;
        }
        if (roomNumbers.size() >= totalRooms) {
            return;
        }
        int floor = 1;
        int indexOnFloor = 1;
        while (roomNumbers.size() < totalRooms) {
            roomNumbers.add(String.format("%d%02d", floor, indexOnFloor));
            indexOnFloor++;
            if (indexOnFloor > 99) {
                indexOnFloor = 1;
                floor++;
            }
        }
    }

    private List<EquipmentManifestItemRequest> buildManifestItems(List<EquipmentImportRow> equipmentRows) {
        Map<String, EquipmentManifestItemRequest> grouped = new LinkedHashMap<>();

        for (EquipmentImportRow row : equipmentRows) {
            EquipmentCatalog catalog = equipmentCatalogRepository
                    .findFirstByNameIgnoreCaseAndActiveTrue(row.getCatalogName())
                    .orElseThrow();
            EquipmentSource source = EquipmentSource.valueOf(normalizeOptional(row.getSourceRaw()));
            EquipmentStatus status = EquipmentStatus.valueOf(normalizeOptional(row.getStatusRaw()));
            BigDecimal price = source == EquipmentSource.INITIAL_HANDOVER
                    ? BigDecimal.ZERO
                    : row.getPrice();

            String key = catalog.getId() + "|" + source + "|" + status + "|" + price;
            EquipmentManifestItemRequest item = grouped.get(key);
            if (item == null) {
                item = new EquipmentManifestItemRequest();
                item.setCatalogId(catalog.getId());
                item.setQuantity(0);
                item.setStatus(status);
                item.setSource(source);
                item.setPrice(price);
                grouped.put(key, item);
            }
            item.setQuantity(item.getQuantity() + row.getQuantity());
        }

        return new ArrayList<>(grouped.values());
    }

    private AssignEquipmentRequest buildAssignRequest(EquipmentImportRow row,
                                                      Map<String, Long> roomIdByNumber) {
        EquipmentCatalog catalog = equipmentCatalogRepository
                .findFirstByNameIgnoreCaseAndActiveTrue(row.getCatalogName())
                .orElseThrow();

        AssignEquipmentRequest request = new AssignEquipmentRequest();
        request.setCatalogId(catalog.getId());
        request.setQuantity(row.getQuantity());
        request.setStatus(EquipmentStatus.valueOf(normalizeOptional(row.getStatusRaw())));
        request.setSource(EquipmentSource.valueOf(normalizeOptional(row.getSourceRaw())));
        request.setNote(row.getNote());

        String roomNumber = normalizeOptional(row.getRoomNumber());
        if (!roomNumber.isBlank()) {
            request.setRoomId(roomIdByNumber.get(roomNumber));
        } else {
            request.setHouseArea(HouseArea.valueOf(normalizeOptional(row.getHouseAreaRaw())));
        }

        if (request.getSource() == EquipmentSource.PURCHASED) {
            request.setPrice(row.getPrice());
        } else {
            request.setPrice(BigDecimal.ZERO);
        }
        return request;
    }

    private int inferFloor(String roomNumber, Integer totalFloor) {
        String digits = roomNumber.replaceAll("\\D", "");
        if (digits.length() >= 3) {
            int floor = Integer.parseInt(digits.substring(0, digits.length() - 2));
            if (floor >= 1 && (totalFloor == null || floor <= totalFloor)) {
                return floor;
            }
        }
        return 1;
    }

    private String normalizeOptional(String value) {
        return value == null ? "" : value.trim();
    }

    private void requireText(List<BulkImportErrorResponse> errors,
                             String sheet,
                             int rowNumber,
                             String contractCode,
                             String field,
                             String value) {
        if (normalizeOptional(value).isBlank()) {
            errors.add(error(sheet, rowNumber, contractCode, field, field + " không được để trống"));
        }
    }

    private BulkImportErrorResponse error(String sheet,
                                          int rowNumber,
                                          String contractCode,
                                          String field,
                                          String message) {
        return BulkImportErrorResponse.builder()
                .sheet(sheet)
                .rowNumber(rowNumber)
                .contractCode(contractCode)
                .field(field)
                .message(message)
                .build();
    }
}
