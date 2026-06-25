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
import com.sep490.slms2026.exception.BulkImportValidationException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.imports.*;
import com.sep490.slms2026.repository.*;
import com.sep490.slms2026.service.BulkRenovationSupplementImportService;
import com.sep490.slms2026.service.PropertyOnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static com.sep490.slms2026.imports.BulkImportSupport.*;
import static com.sep490.slms2026.imports.ExcelRenovationSupplementWorkbookReader.*;

@Service
@RequiredArgsConstructor
public class BulkRenovationSupplementImportServiceImpl implements BulkRenovationSupplementImportService {

    private final ExcelRenovationSupplementWorkbookReader workbookReader;
    private final PropertyOnboardingService propertyOnboardingService;
    private final RenovationCategoryRepository renovationCategoryRepository;
    private final EquipmentCatalogRepository equipmentCatalogRepository;
    private final InboundContractRepository inboundContractRepository;
    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final RenovationPhaseSupport renovationPhaseSupport;
    private final EquipmentRepository equipmentRepository;

    @Override
    @Transactional
    public BulkImportResponse importSupplementWorkbook(MultipartFile file, boolean dryRun) {
        RenovationSupplementImportWorkbook workbook = workbookReader.read(file);
        Set<String> contractCodes = collectContractCodes(workbook);
        List<BulkImportErrorResponse> errors = validate(workbook, contractCodes);

        if (!errors.isEmpty()) {
            throw new BulkImportValidationException("File Excel có lỗi validation", errors);
        }

        if (contractCodes.isEmpty()) {
            throw new BulkImportValidationException("File Excel có lỗi validation",
                    List.of(error(SHEET_RENOVATION, 1, null, null,
                            "Không có dòng cải tạo hoặc thiết bị mua mới nào để import")));
        }

        if (dryRun) {
            return BulkImportResponse.builder()
                    .dryRun(true)
                    .contractsProcessed(contractCodes.size())
                    .contractsSkipped(0)
                    .renovationLinesImported(workbook.getRenovationLines().size())
                    .equipmentRowsImported(workbook.getPurchasedRows().size())
                    .results(buildDryRunResults(contractCodes))
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

        for (String contractCode : contractCodes) {
            List<RenovationImportRow> renovationRows = renovationsByContract.getOrDefault(contractCode, List.of());
            List<PurchasedEquipmentImportRow> purchasedRows = purchasedByContract.getOrDefault(contractCode, List.of());

            results.add(importContract(contractCode, renovationRows, purchasedRows));
            renovationCount += renovationRows.size();
            equipmentCount += purchasedRows.size();
        }

        return BulkImportResponse.builder()
                .dryRun(false)
                .contractsProcessed(contractCodes.size())
                .contractsSkipped(0)
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
        Property property = propertyRepository.findById(propertyId).orElseThrow();

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
            propertyOnboardingService.appendPurchasedEquipmentManifest(propertyId, manifestRequest);

            for (PurchasedEquipmentImportRow purchasedRow : purchasedRows) {
                AssignEquipmentRequest assignRequest = buildAssignRequest(purchasedRow, roomIdByNumber);
                propertyOnboardingService.assignEquipment(propertyId, assignRequest);
            }
        }

        propertyOnboardingService.completeRenovation(propertyId);

        property = propertyRepository.findById(propertyId).orElseThrow();
        return BulkImportContractResultResponse.builder()
                .importStatus(IMPORT_STATUS_IMPORTED)
                .contractCode(contractCode)
                .propertyId(propertyId)
                .propertyName(property.getPropertyName())
                .finalStatus(property.getStatus().name())
                .message("Cải tạo bổ sung — session mới đã đóng")
                .build();
    }

    private Set<String> collectContractCodes(RenovationSupplementImportWorkbook workbook) {
        Set<String> codes = new LinkedHashSet<>();
        workbook.getRenovationLines().forEach(row -> codes.add(row.getContractCode()));
        workbook.getPurchasedRows().forEach(row -> codes.add(row.getContractCode()));
        return codes;
    }

    private List<BulkImportErrorResponse> validate(RenovationSupplementImportWorkbook workbook,
                                                   Set<String> contractCodes) {
        List<BulkImportErrorResponse> errors = new ArrayList<>();

        for (RenovationImportRow row : workbook.getRenovationLines()) {
            validateRenovationRow(row, errors);
        }

        for (PurchasedEquipmentImportRow row : workbook.getPurchasedRows()) {
            validatePurchasedRow(row, errors);
        }

        for (String code : contractCodes) {
            if (!inboundContractRepository.existsByContractCode(code)) {
                errors.add(error(SHEET_RENOVATION, 1, code, "Mã hợp đồng thuê",
                        "Không tìm thấy mã hợp đồng trong hệ thống"));
                continue;
            }
            Property property = inboundContractRepository.findByContractCode(code)
                    .orElseThrow().getProperty();
            Long propertyId = property.getId();

            if (!renovationPhaseSupport.isSupplementRenovationPhase(propertyId)) {
                errors.add(error(SHEET_RENOVATION, 1, code, "Mã hợp đồng thuê",
                        "Phải gọi start-renovation trước (nhà ACTIVE → UNDER_RENOVATION, session ≥ 2). "
                                + "Dùng renovation-excel nếu đang onboarding đợt 2."));
            }

            long renovationCount = workbook.getRenovationLines().stream()
                    .filter(r -> r.getContractCode().equals(code))
                    .count();
            long purchasedCount = workbook.getPurchasedRows().stream()
                    .filter(r -> r.getContractCode().equals(code))
                    .count();
            if (renovationCount == 0 && purchasedCount == 0) {
                errors.add(error(SHEET_RENOVATION, 1, code, "Mã hợp đồng thuê",
                        "Phải có ít nhất một dòng cải tạo hoặc thiết bị mua mới"));
            }
        }

        return errors;
    }

    private void validateRenovationRow(RenovationImportRow row, List<BulkImportErrorResponse> errors) {
        requireText(errors, SHEET_RENOVATION, row.getRowNumber(), row.getContractCode(),
                "Mã hợp đồng thuê", row.getContractCode());
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
        requireText(errors, SHEET_PURCHASED, row.getRowNumber(), row.getContractCode(),
                "Mã hợp đồng thuê", row.getContractCode());

        String roomNumber = normalizeOptional(row.getRoomNumber());
        String houseAreaRaw = normalizeOptional(row.getHouseAreaRaw());
        boolean hasRoom = !roomNumber.isBlank();
        boolean hasArea = !houseAreaRaw.isBlank();

        if (hasRoom == hasArea) {
            errors.add(error(SHEET_PURCHASED, row.getRowNumber(), row.getContractCode(), "Vị trí",
                    "Phải điền Số phòng hoặc Khu vực chung, không được điền cả hai hoặc bỏ trống cả hai"));
        }

        if (hasRoom && inboundContractRepository.existsByContractCode(row.getContractCode())) {
            Property property = inboundContractRepository.findByContractCode(row.getContractCode())
                    .orElseThrow().getProperty();
            boolean roomExists = roomRepository.existsByPropertyIdAndRoomNumberAndDeletedIsFalse(
                    property.getId(), roomNumber);
            if (!roomExists) {
                errors.add(error(SHEET_PURCHASED, row.getRowNumber(), row.getContractCode(), "Số phòng",
                        "Không tìm thấy phòng \"" + roomNumber + "\" trong tòa nhà"));
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
                throw new IllegalArgumentException("Không tìm thấy phòng " + roomNumber);
            }
            request.setRoomId(roomId);
        } else {
            request.setHouseArea(HouseArea.valueOf(normalizeOptional(row.getHouseAreaRaw())));
        }
        return request;
    }

    private List<BulkImportContractResultResponse> buildDryRunResults(Set<String> contractCodes) {
        List<BulkImportContractResultResponse> results = new ArrayList<>();
        for (String code : contractCodes) {
            results.add(BulkImportContractResultResponse.builder()
                    .importStatus(IMPORT_STATUS_IMPORTED)
                    .contractCode(code)
                    .message("Dry run — sẽ ghi cải tạo bổ sung và đóng session")
                    .build());
        }
        return results;
    }
}
