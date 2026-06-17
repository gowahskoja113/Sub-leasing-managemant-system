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
        List<BulkImportErrorResponse> errors = validate(workbook);

        if (!errors.isEmpty()) {
            throw new BulkImportValidationException("File Excel có lỗi validation", errors);
        }

        if (dryRun) {
            return BulkImportResponse.builder()
                    .dryRun(true)
                    .contractsProcessed(workbook.getLeaseContracts().size())
                    .renovationLinesImported(workbook.getRenovationLines().size())
                    .equipmentRowsImported(workbook.getEquipmentRows().size())
                    .results(List.of())
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

        for (LeaseContractImportRow leaseRow : workbook.getLeaseContracts()) {
            BulkImportContractResultResponse result = importContract(
                    leaseRow,
                    renovationsByContract.getOrDefault(leaseRow.getContractCode(), List.of()),
                    equipmentByContract.getOrDefault(leaseRow.getContractCode(), List.of()));
            results.add(result);
            renovationCount += renovationsByContract.getOrDefault(leaseRow.getContractCode(), List.of()).size();
            equipmentCount += equipmentByContract.getOrDefault(leaseRow.getContractCode(), List.of()).size();
        }

        return BulkImportResponse.builder()
                .dryRun(false)
                .contractsProcessed(results.size())
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
        String shortAddress = ZoneImportResolver.buildShortAddress(
                leaseRow.getAddress(), leaseRow.getWard());

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

        if (leaseRow.getHostContingencyPercent() != null) {
            Property property = propertyRepository.findById(propertyId).orElseThrow();
            property.setHostContingencyPercent(leaseRow.getHostContingencyPercent());
            propertyRepository.save(property);
        }

        var contractRequest = CreateInboundContractRequest.builder()
                .contractCode(leaseRow.getContractCode())
                .ownerName(leaseRow.getOwnerName())
                .totalRentAmount(leaseRow.getTotalRentAmount())
                .startDate(leaseRow.getStartDate())
                .endDate(leaseRow.getEndDate())
                .build();
        inboundContractService.signContract(propertyId, contractRequest);

        boolean wholeHouse = parseWholeHouse(leaseRow.getLeaseType());
        boolean hasRenovation = parseBoolean(leaseRow.getHasRenovationRaw());

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
                .contractCode(leaseRow.getContractCode())
                .propertyId(propertyId)
                .propertyName(property.getPropertyName())
                .finalStatus(property.getStatus().name())
                .build();
    }

    private List<BulkImportErrorResponse> validate(OnboardingImportWorkbook workbook) {
        List<BulkImportErrorResponse> errors = new ArrayList<>();

        if (workbook.getLeaseContracts().isEmpty()) {
            errors.add(error(SHEET_LEASE, 1, null, null, "Không có dòng hợp đồng thuê nào để import"));
            return errors;
        }

        Set<String> contractCodes = new HashSet<>();
        Map<String, LeaseContractImportRow> leaseByCode = new LinkedHashMap<>();

        for (LeaseContractImportRow row : workbook.getLeaseContracts()) {
            validateLeaseRow(row, contractCodes, errors);
            leaseByCode.put(row.getContractCode(), row);
        }

        for (RenovationImportRow row : workbook.getRenovationLines()) {
            validateRenovationRow(row, leaseByCode, errors);
        }

        for (EquipmentImportRow row : workbook.getEquipmentRows()) {
            validateEquipmentRow(row, leaseByCode, errors);
        }

        for (LeaseContractImportRow leaseRow : workbook.getLeaseContracts()) {
            boolean hasRenovation = parseBoolean(leaseRow.getHasRenovationRaw());
            long renovationCount = workbook.getRenovationLines().stream()
                    .filter(line -> line.getContractCode().equals(leaseRow.getContractCode()))
                    .count();
            if (!hasRenovation && renovationCount > 0) {
                errors.add(error(SHEET_LEASE, leaseRow.getRowNumber(), leaseRow.getContractCode(),
                        "Có cải tạo không",
                        "Hợp đồng khai báo FALSE nhưng sheet 2 vẫn có chi phí cải tạo"));
            }
            if (hasRenovation && renovationCount == 0) {
                errors.add(error(SHEET_LEASE, leaseRow.getRowNumber(), leaseRow.getContractCode(),
                        "Có cải tạo không",
                        "Hợp đồng khai báo TRUE nhưng sheet 2 không có dòng cải tạo"));
            }
        }

        return errors;
    }

    private void validateLeaseRow(LeaseContractImportRow row,
                                  Set<String> contractCodes,
                                  List<BulkImportErrorResponse> errors) {
        requireText(errors, SHEET_LEASE, row.getRowNumber(), row.getContractCode(), "Mã hợp đồng", row.getContractCode());
        requireText(errors, SHEET_LEASE, row.getRowNumber(), row.getContractCode(), "Tên tòa nhà", row.getPropertyName());
        requireText(errors, SHEET_LEASE, row.getRowNumber(), row.getContractCode(), "Địa chỉ chi tiết", row.getAddress());
        requireText(errors, SHEET_LEASE, row.getRowNumber(), row.getContractCode(), "Quận/Huyện", row.getDistrict());
        requireText(errors, SHEET_LEASE, row.getRowNumber(), row.getContractCode(), "Tỉnh/Thành phố", row.getProvince());
        requireText(errors, SHEET_LEASE, row.getRowNumber(), row.getContractCode(), "Tên chủ nhà", row.getOwnerName());
        requireText(errors, SHEET_LEASE, row.getRowNumber(), row.getContractCode(), "Mô tả chi tiết", row.getDescriptions());
        requireText(errors, SHEET_LEASE, row.getRowNumber(), row.getContractCode(), "Hình thức thuê", row.getLeaseType());
        requireText(errors, SHEET_LEASE, row.getRowNumber(), row.getContractCode(), "Có cải tạo không", row.getHasRenovationRaw());

        if (!contractCodes.add(row.getContractCode())) {
            errors.add(error(SHEET_LEASE, row.getRowNumber(), row.getContractCode(), "Mã hợp đồng",
                    "Mã hợp đồng bị trùng trong file"));
        }
        if (inboundContractRepository.existsByContractCode(row.getContractCode())) {
            errors.add(error(SHEET_LEASE, row.getRowNumber(), row.getContractCode(), "Mã hợp đồng",
                    "Mã hợp đồng đã tồn tại trong hệ thống"));
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

        if (!isValidLeaseType(row.getLeaseType())) {
            errors.add(error(SHEET_LEASE, row.getRowNumber(), row.getContractCode(), "Hình thức thuê",
                    "Chỉ chấp nhận WHOLE_HOUSE hoặc INDIVIDUAL_ROOM"));
        }
        if (!isValidBoolean(row.getHasRenovationRaw())) {
            errors.add(error(SHEET_LEASE, row.getRowNumber(), row.getContractCode(), "Có cải tạo không",
                    "Chỉ chấp nhận TRUE hoặc FALSE"));
        }
        if (row.getHostContingencyPercent() != null) {
            double percent = row.getHostContingencyPercent().doubleValue();
            if (percent < 0 || percent > 100) {
                errors.add(error(SHEET_LEASE, row.getRowNumber(), row.getContractCode(),
                        "Tỷ lệ chi phí dự phòng (%)", "Giá trị phải từ 0 đến 100"));
            }
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

        Map<String, Long> roomIdByNumber = new HashMap<>();
        PropertyType propertyType = wholeHouse ? PropertyType.WHOLE_HOUSE : PropertyType.INDIVIDUAL_ROOM;
        double defaultArea = Math.max(1.0,
                leaseRow.getAreaSize() / Math.max(leaseRow.getTotalRooms(), 1));

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
                    .maxOccupants(2)
                    .propertyType(propertyType)
                    .build();
            var roomResponse = roomService.addRoom(propertyId, addRoomRequest);
            roomIdByNumber.put(roomNumber, roomResponse.getId());
        }
        return roomIdByNumber;
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

    private boolean parseWholeHouse(String leaseType) {
        return "WHOLE_HOUSE".equalsIgnoreCase(normalizeOptional(leaseType));
    }

    private boolean parseBoolean(String raw) {
        return "TRUE".equalsIgnoreCase(normalizeOptional(raw));
    }

    private boolean isValidLeaseType(String leaseType) {
        String value = normalizeOptional(leaseType);
        return "WHOLE_HOUSE".equalsIgnoreCase(value) || "INDIVIDUAL_ROOM".equalsIgnoreCase(value);
    }

    private boolean isValidBoolean(String raw) {
        String value = normalizeOptional(raw);
        return "TRUE".equalsIgnoreCase(value) || "FALSE".equalsIgnoreCase(value);
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
