package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.*;
import com.sep490.slms2026.dto.response.BulkImportContractResultResponse;
import com.sep490.slms2026.dto.response.BulkImportErrorResponse;
import com.sep490.slms2026.dto.response.BulkImportResponse;
import com.sep490.slms2026.entity.*;
import com.sep490.slms2026.enums.EquipmentImportAction;
import com.sep490.slms2026.enums.EquipmentSource;
import com.sep490.slms2026.enums.EquipmentStatus;
import com.sep490.slms2026.enums.HouseArea;
import com.sep490.slms2026.enums.PropertyStatus;
import com.sep490.slms2026.enums.PropertyType;
import com.sep490.slms2026.exception.BulkImportValidationException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.imports.*;
import com.sep490.slms2026.repository.*;
import com.sep490.slms2026.service.BulkRenovationImportService;
import com.sep490.slms2026.service.PropertyOnboardingService;
import com.sep490.slms2026.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static com.sep490.slms2026.imports.BulkImportSupport.*;
import static com.sep490.slms2026.imports.ExcelRenovationImportWorkbookReader.*;

@Service
@RequiredArgsConstructor
public class BulkRenovationImportServiceImpl implements BulkRenovationImportService {

    private static final String SKIP_REASON_ALREADY_SUBMITTED = "Đã hoàn thành đợt 2 / đã gửi Host — bỏ qua";
    private static final Set<String> VALID_EXPLOITATION_TYPES = Set.of(
            "NGUYEN_CAN", "WHOLE_HOUSE", "THEO_PHONG", "INDIVIDUAL_ROOM");

    private final ExcelRenovationImportWorkbookReader workbookReader;
    private final PropertyOnboardingService propertyOnboardingService;
    private final RenovationCategoryRepository renovationCategoryRepository;
    private final EquipmentCatalogRepository equipmentCatalogRepository;
    private final InboundContractRepository inboundContractRepository;
    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final RoomService roomService;
    private final RenovationPhaseSupport renovationPhaseSupport;
    private final EquipmentRepository equipmentRepository;

    @Override
    @Transactional
    public BulkImportResponse importRenovationWorkbook(MultipartFile file, boolean dryRun) {
        RenovationImportWorkbook workbook = workbookReader.read(file);
        List<ExploitationConfigImportRow> configRows = workbook.getConfigRows();
        Map<String, String> skippedContracts = resolveSkippedContracts(configRows);
        List<BulkImportErrorResponse> errors = validate(workbook, skippedContracts);

        if (!errors.isEmpty()) {
            throw new BulkImportValidationException("File Excel có lỗi validation", errors);
        }

        if (configRows.isEmpty()) {
            throw new BulkImportValidationException("File Excel có lỗi validation",
                    List.of(error(SHEET_CONFIG, 1, null, null,
                            "Không có dòng cấu hình khai thác nào để import")));
        }

        int importableCount = countImportable(configRows, skippedContracts);

        if (dryRun) {
            return BulkImportResponse.builder()
                    .dryRun(true)
                    .contractsProcessed(importableCount)
                    .contractsSkipped(skippedContracts.size())
                    .renovationLinesImported(countRenovationForImport(workbook, skippedContracts))
                    .equipmentRowsImported(countPurchasedForImport(workbook, skippedContracts))
                    .results(buildDryRunResults(configRows, skippedContracts))
                    .errors(List.of())
                    .build();
        }

        Map<String, List<RenovationImportRow>> renovationsByContract = workbook.getRenovationLines().stream()
                .collect(Collectors.groupingBy(RenovationImportRow::getContractCode));
        Map<String, List<PurchasedEquipmentImportRow>> purchasedByContract = workbook.getPurchasedRows().stream()
                .collect(Collectors.groupingBy(PurchasedEquipmentImportRow::getContractCode));
        Map<String, List<RoomImportRow>> roomsByContract = workbook.getRoomRows().stream()
                .collect(Collectors.groupingBy(RoomImportRow::getContractCode));

        List<BulkImportContractResultResponse> results = new ArrayList<>();
        int renovationCount = 0;
        int equipmentCount = 0;
        int importedCount = 0;

        for (ExploitationConfigImportRow configRow : configRows) {
            String contractCode = configRow.getContractCode();
            if (skippedContracts.containsKey(contractCode)) {
                results.add(buildSkippedResult(contractCode, skippedContracts.get(contractCode)));
                continue;
            }

            List<RenovationImportRow> renovationRows = renovationsByContract.getOrDefault(contractCode, List.of());
            List<PurchasedEquipmentImportRow> purchasedRows = purchasedByContract.getOrDefault(contractCode, List.of());
            List<RoomImportRow> roomRows = roomsByContract.getOrDefault(contractCode, List.of());

            results.add(importContract(configRow, renovationRows, purchasedRows, roomRows));
            importedCount++;
            renovationCount += renovationRows.size();
            equipmentCount += purchasedRows.size();
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

    private BulkImportContractResultResponse importContract(ExploitationConfigImportRow configRow,
                                                            List<RenovationImportRow> renovationRows,
                                                            List<PurchasedEquipmentImportRow> purchasedRows,
                                                            List<RoomImportRow> roomRows) {
        String contractCode = configRow.getContractCode();
        InboundContract contract = inboundContractRepository.findByContractCode(contractCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy hợp đồng inbound với mã: " + contractCode));
        Long propertyId = contract.getProperty().getId();
        Property property = propertyRepository.findById(propertyId).orElseThrow();

        boolean wholeHouse = configRow.isWholeHouse();
        property.setWholeHouse(wholeHouse);
        if (!wholeHouse && configRow.getExploitationRoomCount() != null) {
            property.setTotalRooms(configRow.getExploitationRoomCount());
        }
        propertyRepository.save(property);

        if (!wholeHouse) {
            createRoomsFromSheet(propertyId, property, roomRows);
        }

        for (RenovationImportRow renovationRow : renovationRows) {
            RenovationCategory category = renovationCategoryRepository.findByCode(renovationRow.getCategoryCode())
                    .orElseThrow();
            var renovationRequest = new AddRenovationLineRequest();
            renovationRequest.setCategoryId(category.getId());
            renovationRequest.setCost(renovationRow.getCost());
            renovationRequest.setNote(renovationRow.getNote());
            propertyOnboardingService.addRenovationLine(propertyId, renovationRequest);
        }

        Map<String, Long> roomIdByNumber = buildRoomIdMap(propertyId);

        if (!purchasedRows.isEmpty()) {
            List<EquipmentManifestItemRequest> manifestItems = buildManifestItems(purchasedRows);
            var manifestRequest = new SaveEquipmentManifestRequest();
            manifestRequest.setItems(manifestItems);
            propertyOnboardingService.saveEquipmentManifest(propertyId, manifestRequest);

            for (PurchasedEquipmentImportRow purchasedRow : purchasedRows) {
                AssignEquipmentRequest assignRequest = buildAssignRequest(purchasedRow, roomIdByNumber);
                propertyOnboardingService.assignEquipment(propertyId, assignRequest);
            }
        } else if (property.getStatus() == PropertyStatus.UNDER_RENOVATION) {
            property.setStatus(PropertyStatus.PENDING_EQUIPMENT_INSTALLATION);
            propertyRepository.save(property);
        }

        propertyOnboardingService.completeRenovation(propertyId);
        propertyOnboardingService.submitToHost(propertyId);

        property = propertyRepository.findById(propertyId).orElseThrow();
        return BulkImportContractResultResponse.builder()
                .importStatus(IMPORT_STATUS_IMPORTED)
                .contractCode(contractCode)
                .propertyId(propertyId)
                .propertyName(property.getPropertyName())
                .finalStatus(property.getStatus().name())
                .build();
    }

    private void createRoomsFromSheet(Long propertyId, Property property, List<RoomImportRow> roomRows) {
        PropertyType propertyType = PropertyType.INDIVIDUAL_ROOM;
        for (RoomImportRow roomRow : roomRows) {
            var addRoomRequest = AddRoomRequest.builder()
                    .roomNumber(roomRow.getRoomNumber())
                    .floor(roomRow.getFloor())
                    .area(roomRow.getArea())
                    .length(roomRow.getLength())
                    .width(roomRow.getWidth())
                    .maxOccupants(2)
                    .propertyType(propertyType)
                    .build();
            roomService.addRoom(propertyId, addRoomRequest);
        }
    }

    private Map<String, Long> buildRoomIdMap(Long propertyId) {
        Map<String, Long> roomIdByNumber = new HashMap<>();
        roomRepository.findByPropertyIdAndDeletedIsFalse(propertyId)
                .forEach(room -> roomIdByNumber.put(room.getRoomNumber(), room.getId()));
        return roomIdByNumber;
    }

    private List<EquipmentManifestItemRequest> buildManifestItems(List<PurchasedEquipmentImportRow> purchasedRows) {
        Map<String, EquipmentManifestItemRequest> grouped = new LinkedHashMap<>();

        for (PurchasedEquipmentImportRow row : purchasedRows) {
            EquipmentCatalog catalog = equipmentCatalogRepository
                    .findFirstByNameIgnoreCaseAndActiveTrue(row.getCatalogName())
                    .orElseThrow();
            EquipmentStatus status = EquipmentStatus.valueOf(normalizeOptional(row.getStatusRaw()));

            String key = catalog.getId() + "|" + status + "|" + row.getPrice();
            EquipmentManifestItemRequest item = grouped.get(key);
            if (item == null) {
                item = new EquipmentManifestItemRequest();
                item.setCatalogId(catalog.getId());
                item.setQuantity(0);
                item.setStatus(status);
                item.setSource(EquipmentSource.PURCHASED);
                item.setPrice(row.getPrice());
                grouped.put(key, item);
            }
            item.setQuantity(item.getQuantity() + row.getQuantity());
        }

        return new ArrayList<>(grouped.values());
    }

    private AssignEquipmentRequest buildAssignRequest(PurchasedEquipmentImportRow row,
                                                      Map<String, Long> roomIdByNumber) {
        EquipmentCatalog catalog = equipmentCatalogRepository
                .findFirstByNameIgnoreCaseAndActiveTrue(row.getCatalogName())
                .orElseThrow();

        AssignEquipmentRequest request = new AssignEquipmentRequest();
        request.setCatalogId(catalog.getId());
        request.setQuantity(row.getQuantity());
        request.setStatus(EquipmentStatus.valueOf(normalizeOptional(row.getStatusRaw())));
        request.setSource(EquipmentSource.PURCHASED);
        request.setPrice(row.getPrice());
        request.setNote(row.getNote());
        request.setWarrantyMonths(row.getWarrantyMonths());
        request.setWarrantyStartDate(row.getWarrantyStartDate());
        request.setWarrantyEndDate(row.getWarrantyEndDate());
        request.setImportAction(EquipmentImportAction.parse(row.getActionRaw()));

        String roomNumber = normalizeOptional(row.getRoomNumber());
        if (!roomNumber.isBlank()) {
            Long roomId = roomIdByNumber.get(roomNumber);
            if (roomId == null) {
                throw new IllegalArgumentException("Không tìm thấy phòng " + roomNumber + " cho mã HĐ " + row.getContractCode());
            }
            request.setRoomId(roomId);
        } else {
            request.setHouseArea(HouseArea.valueOf(normalizeOptional(row.getHouseAreaRaw())));
        }
        return request;
    }

    private Map<String, String> resolveSkippedContracts(List<ExploitationConfigImportRow> configRows) {
        Map<String, String> skipped = new LinkedHashMap<>();
        for (ExploitationConfigImportRow row : configRows) {
            String code = row.getContractCode();
            Optional<InboundContract> contractOpt = inboundContractRepository.findByContractCode(code);
            if (contractOpt.isEmpty()) {
                continue;
            }
            PropertyStatus status = contractOpt.get().getProperty().getStatus();
            if (status == PropertyStatus.PENDING_HOST_REVIEW
                    || status == PropertyStatus.PENDING_OPERATION_MANAGER
                    || status == PropertyStatus.ACTIVE
                    || status == PropertyStatus.RENTED
                    || status == PropertyStatus.RENOVATION_COMPLETED) {
                skipped.put(code, SKIP_REASON_ALREADY_SUBMITTED);
            }
        }
        return skipped;
    }

    private List<BulkImportErrorResponse> validate(RenovationImportWorkbook workbook,
                                                   Map<String, String> skippedContracts) {
        List<BulkImportErrorResponse> errors = new ArrayList<>();
        List<ExploitationConfigImportRow> configRows = workbook.getConfigRows();
        Set<String> configCodes = new HashSet<>();
        Map<String, ExploitationConfigImportRow> configByCode = new LinkedHashMap<>();

        for (ExploitationConfigImportRow row : configRows) {
            if (skippedContracts.containsKey(row.getContractCode())) {
                configCodes.add(row.getContractCode());
                continue;
            }
            validateConfigRow(row, configCodes, errors);
            configByCode.put(row.getContractCode(), row);
        }

        for (RenovationImportRow row : workbook.getRenovationLines()) {
            if (skippedContracts.containsKey(row.getContractCode())) {
                continue;
            }
            if (!configByCode.containsKey(row.getContractCode())) {
                errors.add(error(SHEET_RENOVATION, row.getRowNumber(), row.getContractCode(), "Mã hợp đồng thuê",
                        "Không tìm thấy mã ở sheet cấu hình khai thác"));
                continue;
            }
            validateRenovationRow(row, errors);
        }

        for (PurchasedEquipmentImportRow row : workbook.getPurchasedRows()) {
            if (skippedContracts.containsKey(row.getContractCode())) {
                continue;
            }
            if (!configByCode.containsKey(row.getContractCode())) {
                errors.add(error(SHEET_PURCHASED, row.getRowNumber(), row.getContractCode(), "Mã hợp đồng thuê",
                        "Không tìm thấy mã ở sheet cấu hình khai thác"));
                continue;
            }
            validatePurchasedRow(row, configByCode.get(row.getContractCode()), workbook, errors);
        }

        for (RoomImportRow row : workbook.getRoomRows()) {
            if (skippedContracts.containsKey(row.getContractCode())) {
                continue;
            }
            ExploitationConfigImportRow config = configByCode.get(row.getContractCode());
            if (config == null) {
                errors.add(error(SHEET_ROOMS, row.getRowNumber(), row.getContractCode(), "Mã hợp đồng thuê",
                        "Không tìm thấy mã ở sheet cấu hình khai thác"));
                continue;
            }
            validateRoomRow(row, config, errors);
        }

        for (ExploitationConfigImportRow config : configByCode.values()) {
            String code = config.getContractCode();
            if (!inboundContractRepository.existsByContractCode(code)) {
                errors.add(error(SHEET_CONFIG, config.getRowNumber(), code, "Mã hợp đồng thuê",
                        "Chưa khởi tạo tòa nhà cho mã HĐ này"));
                continue;
            }
            Property property = inboundContractRepository.findByContractCode(code)
                    .orElseThrow().getProperty();
            if (property.getStatus() != PropertyStatus.UNDER_RENOVATION) {
                errors.add(error(SHEET_CONFIG, config.getRowNumber(), code, "Mã hợp đồng thuê",
                        "Tòa nhà phải ở trạng thái UNDER_RENOVATION (chờ đợt 2), hiện tại: "
                                + property.getStatus()));
            } else if (renovationPhaseSupport.isSupplementRenovationPhase(property.getId())) {
                errors.add(error(SHEET_CONFIG, config.getRowNumber(), code, "Mã hợp đồng thuê",
                        "Nhà đang trong đợt cải tạo bổ sung — dùng POST /import/renovation-supplement-excel"));
            }

            if (!config.isWholeHouse()) {
                long roomCount = workbook.getRoomRows().stream()
                        .filter(r -> r.getContractCode().equals(code))
                        .count();
                int expected = config.getExploitationRoomCount() != null ? config.getExploitationRoomCount() : 0;
                if (expected <= 0) {
                    errors.add(error(SHEET_CONFIG, config.getRowNumber(), code, "Số phòng khai thác",
                            "Bắt buộc khi Hình thức khai thác là THEO_PHONG"));
                } else if (roomCount != expected) {
                    errors.add(error(SHEET_ROOMS, config.getRowNumber(), code, "Số phòng khai thác",
                            "Phải tạo đủ " + expected + " phòng chi tiết (hiện có " + roomCount + ")"));
                }
            } else {
                long roomCount = workbook.getRoomRows().stream()
                        .filter(r -> r.getContractCode().equals(code))
                        .count();
                if (roomCount > 0) {
                    errors.add(error(SHEET_ROOMS, config.getRowNumber(), code, "Số phòng",
                            "Nhà nguyên căn không cần sheet danh sách phòng"));
                }
            }
        }

        return errors;
    }

    private void validateConfigRow(ExploitationConfigImportRow row,
                                   Set<String> configCodes,
                                   List<BulkImportErrorResponse> errors) {
        requireText(errors, SHEET_CONFIG, row.getRowNumber(), row.getContractCode(),
                "Mã hợp đồng thuê", row.getContractCode());
        requireText(errors, SHEET_CONFIG, row.getRowNumber(), row.getContractCode(),
                "Hình thức khai thác", row.getExploitationTypeRaw());

        if (!configCodes.add(row.getContractCode())) {
            errors.add(error(SHEET_CONFIG, row.getRowNumber(), row.getContractCode(), "Mã hợp đồng thuê",
                    "Mã hợp đồng bị trùng trong file"));
        }

        String typeRaw = normalizeOptional(row.getExploitationTypeRaw()).toUpperCase();
        if (!VALID_EXPLOITATION_TYPES.contains(typeRaw)) {
            errors.add(error(SHEET_CONFIG, row.getRowNumber(), row.getContractCode(), "Hình thức khai thác",
                    "Giá trị không hợp lệ. Chọn NGUYEN_CAN hoặc THEO_PHONG"));
        }
    }

    private void validateRoomRow(RoomImportRow row,
                                 ExploitationConfigImportRow config,
                                 List<BulkImportErrorResponse> errors) {
        if (config.isWholeHouse()) {
            errors.add(error(SHEET_ROOMS, row.getRowNumber(), row.getContractCode(), "Số phòng",
                    "Nhà nguyên căn không cần sheet danh sách phòng"));
            return;
        }

        requireText(errors, SHEET_ROOMS, row.getRowNumber(), row.getContractCode(), "Số phòng", row.getRoomNumber());

        InboundContract contract = inboundContractRepository.findByContractCode(row.getContractCode()).orElse(null);
        Integer totalFloor = contract != null ? contract.getProperty().getTotalFloor() : null;
        if (row.getFloor() == null || row.getFloor() < 1
                || (totalFloor != null && row.getFloor() > totalFloor)) {
            errors.add(error(SHEET_ROOMS, row.getRowNumber(), row.getContractCode(), "Tầng",
                    "Tầng phải từ 1 đến " + totalFloor));
        }
        if (row.getArea() == null || row.getArea() <= 0) {
            errors.add(error(SHEET_ROOMS, row.getRowNumber(), row.getContractCode(), "Diện tích phòng (m²)",
                    "Diện tích phòng phải lớn hơn 0"));
        }
        if (row.getLength() == null || row.getLength() <= 0) {
            errors.add(error(SHEET_ROOMS, row.getRowNumber(), row.getContractCode(), "Chiều dài (m)",
                    "Chiều dài phải lớn hơn 0"));
        }
        if (row.getWidth() == null || row.getWidth() <= 0) {
            errors.add(error(SHEET_ROOMS, row.getRowNumber(), row.getContractCode(), "Chiều rộng (m)",
                    "Chiều rộng phải lớn hơn 0"));
        }
    }

    private void validateRenovationRow(RenovationImportRow row, List<BulkImportErrorResponse> errors) {
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

    private void validatePurchasedRow(PurchasedEquipmentImportRow row,
                                      ExploitationConfigImportRow config,
                                      RenovationImportWorkbook workbook,
                                      List<BulkImportErrorResponse> errors) {
        String roomNumber = normalizeOptional(row.getRoomNumber());
        String houseAreaRaw = normalizeOptional(row.getHouseAreaRaw());
        boolean hasRoom = !roomNumber.isBlank();
        boolean hasArea = !houseAreaRaw.isBlank();

        if (hasRoom == hasArea) {
            errors.add(error(SHEET_PURCHASED, row.getRowNumber(), row.getContractCode(), "Vị trí",
                    "Phải điền Số phòng hoặc Khu vực chung, không được điền cả hai hoặc bỏ trống cả hai"));
        }

        if (!config.isWholeHouse() && hasRoom) {
            boolean roomExists = workbook.getRoomRows().stream()
                    .anyMatch(r -> r.getContractCode().equals(row.getContractCode())
                            && r.getRoomNumber().equals(roomNumber));
            if (!roomExists) {
                errors.add(error(SHEET_PURCHASED, row.getRowNumber(), row.getContractCode(), "Số phòng",
                        "Số phòng \"" + roomNumber + "\" không có trong sheet danh sách phòng"));
            }
        }

        if (hasArea) {
            try {
                HouseArea.valueOf(houseAreaRaw);
            } catch (IllegalArgumentException ex) {
                errors.add(error(SHEET_PURCHASED, row.getRowNumber(), row.getContractCode(), "Khu vực chung",
                        "Giá trị không hợp lệ. Chọn một trong: LIVING_ROOM, KITCHEN, BATHROOM, BALCONY, GARAGE, OTHER"));
            }
        }

        requireText(errors, SHEET_PURCHASED, row.getRowNumber(), row.getContractCode(),
                "Tên Catalog thiết bị", row.getCatalogName());
        equipmentCatalogRepository.findFirstByNameIgnoreCaseAndActiveTrue(row.getCatalogName())
                .orElseGet(() -> {
                    errors.add(error(SHEET_PURCHASED, row.getRowNumber(), row.getContractCode(),
                            "Tên Catalog thiết bị", "Không tìm thấy catalog trong hệ thống"));
                    return null;
                });

        try {
            EquipmentStatus status = EquipmentStatus.valueOf(normalizeOptional(row.getStatusRaw()));
            if (status != EquipmentStatus.NEW && status != EquipmentStatus.GOOD) {
                errors.add(error(SHEET_PURCHASED, row.getRowNumber(), row.getContractCode(), "Trạng thái thiết bị",
                        "Khi import chỉ chấp nhận NEW hoặc GOOD"));
            }
        } catch (IllegalArgumentException ex) {
            errors.add(error(SHEET_PURCHASED, row.getRowNumber(), row.getContractCode(), "Trạng thái thiết bị",
                    "Giá trị enum không hợp lệ"));
        }

        if (row.getQuantity() == null || row.getQuantity() <= 0) {
            errors.add(error(SHEET_PURCHASED, row.getRowNumber(), row.getContractCode(), "Số lượng",
                    "Số lượng phải là số nguyên dương"));
        }
        if (row.getPrice() == null || row.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add(error(SHEET_PURCHASED, row.getRowNumber(), row.getContractCode(), "Đơn giá (VNĐ)",
                    "Đơn giá phải lớn hơn 0"));
        }
        if (row.getWarrantyMonths() == null || row.getWarrantyMonths() <= 0) {
            errors.add(error(SHEET_PURCHASED, row.getRowNumber(), row.getContractCode(), "Số tháng bảo hành",
                    "Số tháng bảo hành phải lớn hơn 0"));
        }
        if (row.getWarrantyStartDate() == null) {
            errors.add(error(SHEET_PURCHASED, row.getRowNumber(), row.getContractCode(), "Ngày bắt đầu bảo hành",
                    "Ngày bắt đầu bảo hành không hợp lệ (YYYY-MM-DD)"));
        }
        if (row.getWarrantyEndDate() == null) {
            errors.add(error(SHEET_PURCHASED, row.getRowNumber(), row.getContractCode(), "Ngày hết bảo hành",
                    "Ngày hết bảo hành không hợp lệ (YYYY-MM-DD)"));
        }
        if (row.getWarrantyStartDate() != null && row.getWarrantyEndDate() != null
                && !row.getWarrantyEndDate().isAfter(row.getWarrantyStartDate())) {
            errors.add(error(SHEET_PURCHASED, row.getRowNumber(), row.getContractCode(), "Ngày hết bảo hành",
                    "Ngày hết bảo hành phải sau ngày bắt đầu"));
        }

        validateEquipmentImportAction(errors, SHEET_PURCHASED, row);
        if (EquipmentImportAction.parse(row.getActionRaw()) == EquipmentImportAction.THAY_THE
                && inboundContractRepository.existsByContractCode(row.getContractCode())) {
            validateThayTheStock(row, errors);
        }
    }

    private void validateThayTheStock(PurchasedEquipmentImportRow row,
                                      List<BulkImportErrorResponse> errors) {
        EquipmentCatalog catalog = equipmentCatalogRepository
                .findFirstByNameIgnoreCaseAndActiveTrue(row.getCatalogName())
                .orElse(null);
        if (catalog == null || row.getQuantity() == null) {
            return;
        }
        Property property = inboundContractRepository.findByContractCode(row.getContractCode())
                .orElseThrow().getProperty();
        Long propertyId = property.getId();

        String roomNumber = normalizeOptional(row.getRoomNumber());
        Long roomId = null;
        HouseArea houseArea = null;
        if (!roomNumber.isBlank()) {
            roomId = roomRepository.findByPropertyIdAndRoomNumberAndDeletedIsFalse(propertyId, roomNumber)
                    .map(Room::getId)
                    .orElse(null);
            if (roomId == null) {
                return;
            }
        } else {
            try {
                houseArea = HouseArea.valueOf(normalizeOptional(row.getHouseAreaRaw()));
            } catch (IllegalArgumentException ex) {
                return;
            }
        }

        long available = equipmentRepository.findActivePurchasedAtPlacement(
                propertyId, catalog.getId(), roomId, houseArea).size();
        if (available < row.getQuantity()) {
            errors.add(error(SHEET_PURCHASED, row.getRowNumber(), row.getContractCode(), "Hành động",
                    "THAY_THE: không đủ thiết bị ACTIVE tại vị trí (cần "
                            + row.getQuantity() + ", hiện có " + available + ")"));
        }
    }

    private int countImportable(List<ExploitationConfigImportRow> configRows,
                                Map<String, String> skippedContracts) {
        return (int) configRows.stream()
                .map(ExploitationConfigImportRow::getContractCode)
                .filter(code -> !skippedContracts.containsKey(code))
                .count();
    }

    private int countRenovationForImport(RenovationImportWorkbook workbook, Map<String, String> skippedContracts) {
        return (int) workbook.getRenovationLines().stream()
                .filter(row -> !skippedContracts.containsKey(row.getContractCode()))
                .count();
    }

    private int countPurchasedForImport(RenovationImportWorkbook workbook, Map<String, String> skippedContracts) {
        return (int) workbook.getPurchasedRows().stream()
                .filter(row -> !skippedContracts.containsKey(row.getContractCode()))
                .count();
    }

    private List<BulkImportContractResultResponse> buildDryRunResults(List<ExploitationConfigImportRow> configRows,
                                                                      Map<String, String> skippedContracts) {
        List<BulkImportContractResultResponse> results = new ArrayList<>();
        for (ExploitationConfigImportRow row : configRows) {
            String code = row.getContractCode();
            if (skippedContracts.containsKey(code)) {
                results.add(buildSkippedResult(code, skippedContracts.get(code)));
            } else {
                results.add(BulkImportContractResultResponse.builder()
                        .importStatus(IMPORT_STATUS_IMPORTED)
                        .contractCode(code)
                        .message("Dry run — sẽ cập nhật đợt 2 và gửi Host")
                        .build());
            }
        }
        return results;
    }

    private BulkImportContractResultResponse buildSkippedResult(String contractCode, String message) {
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
}
