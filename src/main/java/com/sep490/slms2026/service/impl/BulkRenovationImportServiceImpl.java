package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.*;
import com.sep490.slms2026.dto.response.BulkImportContractResultResponse;
import com.sep490.slms2026.dto.response.BulkImportErrorResponse;
import com.sep490.slms2026.dto.response.BulkImportResponse;
import com.sep490.slms2026.entity.*;
import com.sep490.slms2026.enums.EquipmentSource;
import com.sep490.slms2026.enums.EquipmentStatus;
import com.sep490.slms2026.enums.HouseArea;
import com.sep490.slms2026.enums.PropertyStatus;
import com.sep490.slms2026.exception.BulkImportValidationException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.imports.*;
import com.sep490.slms2026.repository.*;
import com.sep490.slms2026.service.BulkRenovationImportService;
import com.sep490.slms2026.service.PropertyOnboardingService;
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

    private final ExcelRenovationImportWorkbookReader workbookReader;
    private final PropertyOnboardingService propertyOnboardingService;
    private final RenovationCategoryRepository renovationCategoryRepository;
    private final EquipmentCatalogRepository equipmentCatalogRepository;
    private final InboundContractRepository inboundContractRepository;
    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;

    @Override
    @Transactional
    public BulkImportResponse importRenovationWorkbook(MultipartFile file, boolean dryRun) {
        RenovationImportWorkbook workbook = workbookReader.read(file);
        Set<String> contractCodes = collectContractCodes(workbook);
        Map<String, String> skippedContracts = resolveSkippedContracts(contractCodes);
        List<BulkImportErrorResponse> errors = validate(workbook, skippedContracts);

        if (!errors.isEmpty()) {
            throw new BulkImportValidationException("File Excel có lỗi validation", errors);
        }

        if (contractCodes.isEmpty()) {
            throw new BulkImportValidationException("File Excel có lỗi validation",
                    List.of(error(SHEET_RENOVATION, 1, null, null,
                            "Không có dòng cải tạo hoặc thiết bị mua mới nào để import")));
        }

        int importableCount = countImportable(contractCodes, skippedContracts);

        if (dryRun) {
            return BulkImportResponse.builder()
                    .dryRun(true)
                    .contractsProcessed(importableCount)
                    .contractsSkipped(skippedContracts.size())
                    .renovationLinesImported(countRenovationForImport(workbook, skippedContracts))
                    .equipmentRowsImported(countPurchasedForImport(workbook, skippedContracts))
                    .results(buildDryRunResults(contractCodes, skippedContracts))
                    .errors(List.of())
                    .build();
        }

        Map<String, List<RenovationImportRow>> renovationsByContract = workbook.getRenovationLines().stream()
                .collect(Collectors.groupingBy(RenovationImportRow::getContractCode));
        Map<String, List<PurchasedEquipmentImportRow>> purchasedByContract = workbook.getPurchasedRows().stream()
                .collect(Collectors.groupingBy(PurchasedEquipmentImportRow::getContractCode));

        List<BulkImportContractResultResponse> results = new ArrayList<>();
        int renovationCount = 0;
        int equipmentCount = 0;
        int importedCount = 0;

        for (String contractCode : contractCodes) {
            if (skippedContracts.containsKey(contractCode)) {
                results.add(buildSkippedResult(contractCode, skippedContracts.get(contractCode)));
                continue;
            }

            List<RenovationImportRow> renovationRows = renovationsByContract.getOrDefault(contractCode, List.of());
            List<PurchasedEquipmentImportRow> purchasedRows = purchasedByContract.getOrDefault(contractCode, List.of());

            results.add(importContract(contractCode, renovationRows, purchasedRows));
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

    private BulkImportContractResultResponse importContract(String contractCode,
                                                            List<RenovationImportRow> renovationRows,
                                                            List<PurchasedEquipmentImportRow> purchasedRows) {
        InboundContract contract = inboundContractRepository.findByContractCode(contractCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy hợp đồng inbound với mã: " + contractCode));
        Long propertyId = contract.getProperty().getId();

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
        } else {
            Property property = propertyRepository.findById(propertyId).orElseThrow();
            if (property.getStatus() == PropertyStatus.UNDER_RENOVATION) {
                property.setStatus(PropertyStatus.PENDING_EQUIPMENT_INSTALLATION);
                propertyRepository.save(property);
            }
        }

        propertyOnboardingService.completeRenovation(propertyId);
        propertyOnboardingService.submitToHost(propertyId);

        Property property = propertyRepository.findById(propertyId).orElseThrow();
        return BulkImportContractResultResponse.builder()
                .importStatus(IMPORT_STATUS_IMPORTED)
                .contractCode(contractCode)
                .propertyId(propertyId)
                .propertyName(property.getPropertyName())
                .finalStatus(property.getStatus().name())
                .build();
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

    private Set<String> collectContractCodes(RenovationImportWorkbook workbook) {
        Set<String> codes = new LinkedHashSet<>();
        workbook.getRenovationLines().forEach(row -> codes.add(row.getContractCode()));
        workbook.getPurchasedRows().forEach(row -> codes.add(row.getContractCode()));
        return codes;
    }

    private Map<String, String> resolveSkippedContracts(Set<String> contractCodes) {
        Map<String, String> skipped = new LinkedHashMap<>();
        for (String code : contractCodes) {
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

        for (RenovationImportRow row : workbook.getRenovationLines()) {
            if (skippedContracts.containsKey(row.getContractCode())) {
                continue;
            }
            validateRenovationRow(row, errors);
        }

        for (PurchasedEquipmentImportRow row : workbook.getPurchasedRows()) {
            if (skippedContracts.containsKey(row.getContractCode())) {
                continue;
            }
            validatePurchasedRow(row, errors);
        }

        Set<String> allCodes = collectContractCodes(workbook);
        for (String code : allCodes) {
            if (skippedContracts.containsKey(code)) {
                continue;
            }
            if (!inboundContractRepository.existsByContractCode(code)) {
                errors.add(error(SHEET_RENOVATION, 1, code, "Mã hợp đồng thuê",
                        "Chưa khởi tạo tòa nhà cho mã HĐ này"));
            } else {
                Property property = inboundContractRepository.findByContractCode(code)
                        .orElseThrow().getProperty();
                if (property.getStatus() != PropertyStatus.UNDER_RENOVATION) {
                    errors.add(error(SHEET_RENOVATION, 1, code, "Mã hợp đồng thuê",
                            "Tòa nhà phải ở trạng thái UNDER_RENOVATION (chờ đợt 2), hiện tại: "
                                    + property.getStatus()));
                }
                long renovationCount = workbook.getRenovationLines().stream()
                        .filter(r -> r.getContractCode().equals(code))
                        .count();
                if (renovationCount == 0) {
                    errors.add(error(SHEET_RENOVATION, 1, code, "Mã hợp đồng thuê",
                            "Đợt 2 phải có ít nhất một dòng cải tạo cho mã HĐ này"));
                }
            }
        }

        return errors;
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

    private void validatePurchasedRow(PurchasedEquipmentImportRow row, List<BulkImportErrorResponse> errors) {
        if (!inboundContractRepository.existsByContractCode(row.getContractCode())) {
            errors.add(error(SHEET_PURCHASED, row.getRowNumber(), row.getContractCode(), "Mã hợp đồng thuê",
                    "Chưa khởi tạo tòa nhà cho mã HĐ này"));
            return;
        }

        String roomNumber = normalizeOptional(row.getRoomNumber());
        String houseAreaRaw = normalizeOptional(row.getHouseAreaRaw());
        boolean hasRoom = !roomNumber.isBlank();
        boolean hasArea = !houseAreaRaw.isBlank();

        if (hasRoom == hasArea) {
            errors.add(error(SHEET_PURCHASED, row.getRowNumber(), row.getContractCode(), "Vị trí",
                    "Phải điền Số phòng hoặc Khu vực chung, không được điền cả hai hoặc bỏ trống cả hai"));
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
    }

    private int countImportable(Set<String> contractCodes, Map<String, String> skippedContracts) {
        return (int) contractCodes.stream().filter(code -> !skippedContracts.containsKey(code)).count();
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

    private List<BulkImportContractResultResponse> buildDryRunResults(Set<String> contractCodes,
                                                                      Map<String, String> skippedContracts) {
        List<BulkImportContractResultResponse> results = new ArrayList<>();
        for (String code : contractCodes) {
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
