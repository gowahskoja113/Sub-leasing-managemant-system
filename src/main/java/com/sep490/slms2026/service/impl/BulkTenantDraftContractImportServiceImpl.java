package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.OnboardTenantRequest;
import com.sep490.slms2026.dto.response.BulkImportContractResultResponse;
import com.sep490.slms2026.dto.response.BulkImportErrorResponse;
import com.sep490.slms2026.dto.response.BulkImportResponse;
import com.sep490.slms2026.dto.response.TenantContractResponse;
import com.sep490.slms2026.entity.InboundContract;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Room;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.enums.PropertyStatus;
import com.sep490.slms2026.enums.RoomStatus;
import com.sep490.slms2026.exception.BulkImportValidationException;
import com.sep490.slms2026.imports.ExcelTenantDraftContractWorkbookReader;
import com.sep490.slms2026.imports.TenantDraftContractImportRow;
import com.sep490.slms2026.imports.TenantDraftContractImportWorkbook;
import com.sep490.slms2026.repository.InboundContractRepository;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.RoomRepository;
import com.sep490.slms2026.repository.TenantContractRepository;
import com.sep490.slms2026.service.BulkTenantDraftContractImportService;
import com.sep490.slms2026.service.PricingConfigService;
import com.sep490.slms2026.service.TenantOnboardingService;
import com.sep490.slms2026.service.UnitPriceService;
import com.sep490.slms2026.util.InboundLeaseRules;
import com.sep490.slms2026.util.RentEscalationSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static com.sep490.slms2026.imports.BulkImportSupport.IMPORT_STATUS_IMPORTED;
import static com.sep490.slms2026.imports.BulkImportSupport.error;
import static com.sep490.slms2026.imports.BulkImportSupport.normalizeOptional;
import static com.sep490.slms2026.imports.BulkImportSupport.requireText;
import static com.sep490.slms2026.imports.ExcelTenantDraftContractWorkbookReader.SHEET_DRAFT;

@Service
@RequiredArgsConstructor
public class BulkTenantDraftContractImportServiceImpl implements BulkTenantDraftContractImportService {

    static final String CODE_MISSING_FIELD = "MISSING_FIELD";
    static final String CODE_INVALID_DATE = "INVALID_DATE";
    static final String CODE_INVALID_NUMBER = "INVALID_NUMBER";
    static final String CODE_INVALID_FIELD = "INVALID_FIELD";
    static final String CODE_PROPERTY_NOT_ACTIVE = "PROPERTY_NOT_ACTIVE";
    static final String CODE_PROPERTY_NO_INBOUND_LEASE = "PROPERTY_NO_INBOUND_LEASE";
    static final String CODE_OCCUPANCY_OUT_OF_LEASE = "OCCUPANCY_OUT_OF_LEASE";
    static final String CODE_PROPERTY_NOT_FOUND = "PROPERTY_NOT_FOUND";
    static final String CODE_PROPERTY_AMBIGUOUS = "PROPERTY_AMBIGUOUS";
    static final String CODE_ROOM_NOT_FOUND = "ROOM_NOT_FOUND";
    static final String CODE_ROOM_OCCUPIED = "ROOM_OCCUPIED";
    static final String CODE_DUPLICATE_IN_FILE = "DUPLICATE_IN_FILE";
    static final String CODE_FILE_EMPTY = "FILE_EMPTY";

    private final ExcelTenantDraftContractWorkbookReader workbookReader;
    private final InboundContractRepository inboundContractRepository;
    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final TenantContractRepository tenantContractRepository;
    private final TenantOnboardingService tenantOnboardingService;
    private final UnitPriceService unitPriceService;
    private final PricingConfigService pricingConfigService;

    @Override
    @Transactional
    public BulkImportResponse importWorkbook(MultipartFile file, boolean dryRun, boolean skipInvalidRows) {
        TenantDraftContractImportWorkbook workbook = workbookReader.read(file);
        if (workbook.getRows().isEmpty()) {
            throw new BulkImportValidationException("File Excel không có dòng dữ liệu", List.of(
                    error(SHEET_DRAFT, 2, null, "file", "Không có dòng hợp đồng nháp nào để import",
                            CODE_FILE_EMPTY)));
        }

        List<BulkImportErrorResponse> errors = new ArrayList<>();
        List<ResolvedDraftRow> resolved = new ArrayList<>();
        Set<String> occupancyKeysInFile = new HashSet<>();

        for (TenantDraftContractImportRow row : workbook.getRows()) {
            ResolvedDraftRow item = validateAndResolve(row, occupancyKeysInFile, errors);
            if (item != null) {
                resolved.add(item);
            }
        }

        if (!errors.isEmpty() && !skipInvalidRows) {
            throw new BulkImportValidationException("File Excel có lỗi validation", errors);
        }
        if (resolved.isEmpty()) {
            throw new BulkImportValidationException("Không có dòng nào hợp lệ để import", errors);
        }

        int skipped = countSkippedRows(errors);

        if (dryRun) {
            List<BulkImportContractResultResponse> dryResults = resolved.stream()
                    .map(r -> toImportResult(r, IMPORT_STATUS_IMPORTED, "(dry-run)",
                            ContractStatus.DRAFT.name(), buildPreviewMessage(r)))
                    .toList();
            return BulkImportResponse.builder()
                    .dryRun(true)
                    .contractsProcessed(resolved.size())
                    .contractsSkipped(skipped)
                    .renovationLinesImported(0)
                    .equipmentRowsImported(0)
                    .results(dryResults)
                    .errors(errors)
                    .build();
        }

        List<BulkImportContractResultResponse> results = new ArrayList<>();
        for (ResolvedDraftRow r : resolved) {
            OnboardTenantRequest request = toOnboardRequest(r);
            Long roomId = r.room() != null ? r.room().getId() : null;
            TenantContractResponse created = tenantOnboardingService.onboardTenant(
                    r.property().getId(), roomId, request);
            results.add(toImportResult(r, IMPORT_STATUS_IMPORTED, created.getContractCode(),
                    created.getStatus() != null ? created.getStatus().name() : ContractStatus.DRAFT.name(),
                    "Đã tạo HĐ nháp cho " + created.getTenantFullName()
                            + (created.getRoomNumber() != null ? " — phòng " + created.getRoomNumber() : " — nguyên căn")));
        }

        return BulkImportResponse.builder()
                .dryRun(false)
                .contractsProcessed(results.size())
                .contractsSkipped(skipped)
                .renovationLinesImported(0)
                .equipmentRowsImported(0)
                .results(results)
                .errors(errors)
                .build();
    }

    private ResolvedDraftRow validateAndResolve(TenantDraftContractImportRow row,
                                                Set<String> occupancyKeysInFile,
                                                List<BulkImportErrorResponse> errors) {
        int before = errors.size();
        String rowKey = "row-" + row.getRowNumber();

        requireText(errors, SHEET_DRAFT, row.getRowNumber(), rowKey, "Họ tên khách thuê", row.getFullName(),
                CODE_MISSING_FIELD);
        requireText(errors, SHEET_DRAFT, row.getRowNumber(), rowKey, "CCCD", row.getCccd(), CODE_MISSING_FIELD);
        requireText(errors, SHEET_DRAFT, row.getRowNumber(), rowKey, "Số điện thoại", row.getPhoneNumber(),
                CODE_MISSING_FIELD);

        if (row.getMoveInDate() == null) {
            addError(errors, row, rowKey, "Ngày vào ở", CODE_INVALID_DATE,
                    "Ngày vào ở không hợp lệ hoặc để trống (YYYY-MM-DD hoặc DD/MM/YYYY)");
        }
        if (row.getEndDate() == null) {
            addError(errors, row, rowKey, "Ngày kết thúc", CODE_INVALID_DATE,
                    "Ngày kết thúc không hợp lệ hoặc để trống");
        }
        if (row.getRentAmount() == null || row.getRentAmount().compareTo(BigDecimal.ZERO) <= 0) {
            addError(errors, row, rowKey, "Giá thuê/tháng", CODE_INVALID_NUMBER, "Giá thuê phải lớn hơn 0");
        }
        if (row.getDeposit() == null || row.getDeposit().compareTo(BigDecimal.ZERO) < 0) {
            addError(errors, row, rowKey, "Tiền cọc", CODE_INVALID_NUMBER,
                    "Tiền cọc không hợp lệ (điền Tiền cọc hoặc Số tháng cọc)");
        }

        LocalDate today = LocalDate.now();
        if (row.getMoveInDate() != null && row.getEndDate() != null) {
            if (!row.getEndDate().isAfter(row.getMoveInDate())) {
                addError(errors, row, rowKey, "Ngày kết thúc", CODE_INVALID_DATE,
                        "Ngày kết thúc phải sau ngày vào ở");
            }
            if (row.getEndDate().isAfter(today.plusYears(5))) {
                addError(errors, row, rowKey, "Ngày kết thúc", CODE_INVALID_DATE, "Thời hạn thuê tối đa 5 năm");
            }
        }

        Property property = resolveProperty(row, errors, rowKey);
        if (property == null) {
            return null;
        }
        if (property.getStatus() != PropertyStatus.ACTIVE) {
            addError(errors, row, rowKey, "BĐS", CODE_PROPERTY_NOT_ACTIVE,
                    "BĐS '" + property.getPropertyName() + "' chưa ACTIVE (status="
                            + property.getStatus() + ") — chưa cho thuê được");
        }

        InboundContract lease = inboundContractRepository.findFirstByPropertyIdOrderByIdDesc(property.getId())
                .orElse(null);
        if (lease == null) {
            addError(errors, row, rowKey, "BĐS", CODE_PROPERTY_NO_INBOUND_LEASE,
                    "Nhà chưa có hợp đồng với chủ nhà — không thể cho thuê");
        } else {
            String occupancyError = InboundLeaseRules.occupancyErrorOrNull(
                    row.getMoveInDate(), row.getEndDate(), lease);
            if (occupancyError != null) {
                addError(errors, row, rowKey, "Ngày vào ở", CODE_OCCUPANCY_OUT_OF_LEASE, occupancyError);
            }
        }

        boolean byRoom = isRoomRental(row, property);
        Room room = null;
        if (byRoom) {
            String roomNumber = normalizeOptional(row.getRoomNumber());
            if (roomNumber.isBlank()) {
                addError(errors, row, rowKey, "Số phòng", CODE_MISSING_FIELD, "Thuê theo phòng bắt buộc có Số phòng");
            } else {
                room = roomRepository.findByPropertyIdAndRoomNumberAndDeletedIsFalse(
                                property.getId(), roomNumber)
                        .orElse(null);
                if (room == null) {
                    addError(errors, row, rowKey, "Số phòng", CODE_ROOM_NOT_FOUND,
                            "Không tìm thấy phòng '" + roomNumber + "' thuộc BĐS '"
                                    + property.getPropertyName() + "'");
                } else if (room.getStatus() == RoomStatus.RENTED) {
                    addError(errors, row, rowKey, "Số phòng", CODE_ROOM_OCCUPIED,
                            "Phòng '" + roomNumber + "' đang được cho thuê");
                } else if (row.getMoveInDate() != null && row.getEndDate() != null) {
                    if (tenantContractRepository.existsByRoomIdAndStatus(room.getId(), ContractStatus.ACTIVE)) {
                        addError(errors, row, rowKey, "Số phòng", CODE_ROOM_OCCUPIED,
                                "Phòng đã có hợp đồng đang hiệu lực");
                    } else if (tenantContractRepository.existsOverlappingContractByRoom(
                            room.getId(), row.getMoveInDate(), row.getEndDate())) {
                        addError(errors, row, rowKey, "Số phòng", CODE_ROOM_OCCUPIED,
                                "Phòng đã có hợp đồng chồng lấn thời gian");
                    }
                }
            }
        } else {
            if (!normalizeOptional(row.getRoomNumber()).isBlank()) {
                addError(errors, row, rowKey, "Số phòng", CODE_INVALID_FIELD,
                        "Thuê nguyên căn — để trống cột Số phòng");
            }
            if (row.getMoveInDate() != null && row.getEndDate() != null) {
                if (tenantContractRepository.existsByPropertyIdAndRoomIsNullAndStatus(
                        property.getId(), ContractStatus.ACTIVE)) {
                    addError(errors, row, rowKey, "BĐS", CODE_ROOM_OCCUPIED,
                            "Căn nhà đã có hợp đồng nguyên căn đang hiệu lực");
                } else if (tenantContractRepository.existsOverlappingContractByProperty(
                        property.getId(), row.getMoveInDate(), row.getEndDate())) {
                    addError(errors, row, rowKey, "BĐS", CODE_ROOM_OCCUPIED,
                            "Căn nhà đã có hợp đồng chồng lấn thời gian");
                }
            }
        }

        String occupancyKey = property.getId() + "|"
                + (room != null ? "R:" + room.getId() : "WHOLE");
        if (!occupancyKeysInFile.add(occupancyKey)) {
            addError(errors, row, rowKey, "BĐS", CODE_DUPLICATE_IN_FILE,
                    "Trùng BĐS/phòng với dòng khác trong file");
        }

        if (errors.size() > before) {
            return null;
        }

        return new ResolvedDraftRow(row, property, room);
    }

    private Property resolveProperty(TenantDraftContractImportRow row,
                                     List<BulkImportErrorResponse> errors,
                                     String rowKey) {
        String inboundCode = normalizeOptional(row.getInboundContractCode());
        if (!inboundCode.isBlank()) {
            Optional<InboundContract> inbound = inboundContractRepository
                    .findByContractCodeIgnoreCaseWithProperty(inboundCode);
            if (inbound.isEmpty()) {
                addError(errors, row, rowKey, "Mã HĐ inbound", CODE_PROPERTY_NOT_FOUND,
                        "Không tìm thấy HĐ inbound '" + inboundCode + "' trong hệ thống");
                return null;
            }
            return inbound.get().getProperty();
        }

        if (row.getPropertyId() != null) {
            Optional<Property> byId = propertyRepository.findById(row.getPropertyId());
            if (byId.isEmpty()) {
                addError(errors, row, rowKey, "Mã BĐS", CODE_PROPERTY_NOT_FOUND,
                        "Không tìm thấy BĐS ID " + row.getPropertyId());
                return null;
            }
            return byId.get();
        }

        String name = normalizeOptional(row.getPropertyName());
        if (!name.isBlank()) {
            List<Property> matches = propertyRepository.findByPropertyNameIgnoreCase(name);
            if (matches.isEmpty()) {
                addError(errors, row, rowKey, "Tên tòa nhà", CODE_PROPERTY_NOT_FOUND,
                        "Không tìm thấy BĐS tên '" + name + "'");
                return null;
            }
            if (matches.size() > 1) {
                addError(errors, row, rowKey, "Tên tòa nhà", CODE_PROPERTY_AMBIGUOUS,
                        "Có " + matches.size() + " BĐS trùng tên '" + name
                                + "' — dùng Mã HĐ inbound hoặc Mã BĐS");
                return null;
            }
            return matches.get(0);
        }

        addError(errors, row, rowKey, "BĐS", CODE_MISSING_FIELD,
                "Cần ít nhất một trong: Mã HĐ inbound, Mã BĐS, hoặc Tên tòa nhà");
        return null;
    }

    private boolean isRoomRental(TenantDraftContractImportRow row, Property property) {
        String type = normalizeOptional(row.getRentTypeRaw()).toUpperCase(Locale.ROOT)
                .replace(' ', '_');
        if (type.equals("THEO_PHONG") || type.equals("PHONG") || type.equals("ROOM")) {
            return true;
        }
        if (type.equals("NGUYEN_CAN") || type.equals("WHOLE_HOUSE") || type.equals("NGUYENCAN")) {
            return false;
        }
        // Không ghi Loại thuê → suy luận theo Số phòng hoặc chế độ BĐS
        if (!normalizeOptional(row.getRoomNumber()).isBlank()) {
            return true;
        }
        return Boolean.FALSE.equals(property.getWholeHouse());
    }

    private OnboardTenantRequest toOnboardRequest(ResolvedDraftRow r) {
        TenantDraftContractImportRow row = r.row();
        OnboardTenantRequest request = new OnboardTenantRequest();
        request.setDraft(true);
        request.setFullName(row.getFullName().trim());
        request.setCccd(row.getCccd().trim());
        request.setPhoneNumber(row.getPhoneNumber().trim());
        request.setDateOfBirth(row.getDateOfBirth());
        request.setCccdIssueDate(row.getCccdIssueDate());
        request.setCccdIssuePlace(normalizeOptional(row.getCccdIssuePlace()).isBlank()
                ? null : row.getCccdIssuePlace().trim());
        request.setPermanentAddress(normalizeOptional(row.getPermanentAddress()).isBlank()
                ? null : row.getPermanentAddress().trim());
        request.setMoveInDate(row.getMoveInDate());
        request.setEndDate(row.getEndDate());
        request.setRentAmount(row.getRentAmount());
        request.setDeposit(row.getDeposit());
        request.setDepositMonths(row.getDepositMonths());
        request.setExpectedReceptionDate(row.getExpectedReceptionDate());
        request.setRequireDepositPayment(true);
        // Để trống loại/% → onboard gán ANNUAL_CALENDAR từ pricing_config.
        // Cột "Tăng giá theo năm (%)" = 0 → không tăng.
        request.setRentEscalationType(row.getRentEscalationTypeRaw());
        request.setRentEscalationPercent(row.getRentEscalationPercent());
        if (row.getRentScheduleRaw() != null && !row.getRentScheduleRaw().isBlank()) {
            request.setRentSchedule(RentEscalationSupport.parseScheduleRaw(row.getRentScheduleRaw()));
        }
        return request;
    }

    private BulkImportContractResultResponse toImportResult(ResolvedDraftRow r,
                                                            String importStatus,
                                                            String contractCode,
                                                            String finalStatus,
                                                            String message) {
        BigDecimal listed = r.room() != null ? r.room().getPrice() : r.property().getPrice();
        BigDecimal rent = r.row().getRentAmount();
        BigDecimal escalationPct = resolvePreviewEscalationPercent(r.row());
        return BulkImportContractResultResponse.builder()
                .importStatus(importStatus)
                .contractCode(contractCode)
                .propertyId(r.property().getId())
                .propertyName(r.property().getPropertyName())
                .finalStatus(finalStatus)
                .message(message)
                .roomId(r.room() != null ? r.room().getId() : null)
                .roomNumber(r.room() != null ? r.room().getRoomNumber() : null)
                .tenantName(r.row().getFullName())
                .listedPrice(listed)
                .rentAmount(rent)
                .rentEscalationPercent(escalationPct)
                .deltaPercent(unitPriceService.deltaPercent(listed, rent))
                .build();
    }

    private BigDecimal resolvePreviewEscalationPercent(TenantDraftContractImportRow row) {
        if (row.getRentEscalationPercent() != null) {
            return row.getRentEscalationPercent();
        }
        if (row.getRentEscalationTypeRaw() != null && !row.getRentEscalationTypeRaw().isBlank()) {
            var type = RentEscalationSupport.parseType(row.getRentEscalationTypeRaw());
            if (type == com.sep490.slms2026.enums.RentEscalationType.NONE) {
                return BigDecimal.ZERO;
            }
            if (type == com.sep490.slms2026.enums.RentEscalationType.SCHEDULE) {
                return null;
            }
        }
        if (row.getRentScheduleRaw() != null && !row.getRentScheduleRaw().isBlank()) {
            return null;
        }
        return pricingConfigService.current().getAnnualIncreasePct();
    }

    private static String buildPreviewMessage(ResolvedDraftRow r) {
        String unit = r.room() != null ? "phòng " + r.room().getRoomNumber() : "nguyên căn";
        return "Sẽ tạo DRAFT cho " + r.row().getFullName() + " — " + unit;
    }

    private static void addError(List<BulkImportErrorResponse> errors,
                                 TenantDraftContractImportRow row,
                                 String rowKey,
                                 String field,
                                 String code,
                                 String message) {
        errors.add(error(SHEET_DRAFT, row.getRowNumber(), rowKey, field, message, code));
    }

    private static int countSkippedRows(List<BulkImportErrorResponse> errors) {
        return (int) errors.stream().map(BulkImportErrorResponse::getRowNumber).distinct().count();
    }

    private record ResolvedDraftRow(
            TenantDraftContractImportRow row,
            Property property,
            Room room
    ) {
    }
}
