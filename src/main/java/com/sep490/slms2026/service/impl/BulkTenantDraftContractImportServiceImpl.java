package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.OnboardTenantRequest;
import com.sep490.slms2026.dto.response.BulkImportContractResultResponse;
import com.sep490.slms2026.dto.response.BulkImportErrorResponse;
import com.sep490.slms2026.dto.response.BulkImportResponse;
import com.sep490.slms2026.dto.response.TenantContractResponse;
import com.sep490.slms2026.entity.InboundContract;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Room;
import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.enums.PropertyStatus;
import com.sep490.slms2026.enums.Role;
import com.sep490.slms2026.enums.RoomStatus;
import com.sep490.slms2026.exception.BulkImportValidationException;
import com.sep490.slms2026.imports.ExcelTenantDraftContractWorkbookReader;
import com.sep490.slms2026.imports.TenantDraftContractImportRow;
import com.sep490.slms2026.imports.TenantDraftContractImportWorkbook;
import com.sep490.slms2026.repository.InboundContractRepository;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.RoomRepository;
import com.sep490.slms2026.repository.TenantContractRepository;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.service.BulkTenantDraftContractImportService;
import com.sep490.slms2026.service.TenantOnboardingService;
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
import java.util.UUID;

import static com.sep490.slms2026.imports.BulkImportSupport.IMPORT_STATUS_IMPORTED;
import static com.sep490.slms2026.imports.BulkImportSupport.error;
import static com.sep490.slms2026.imports.BulkImportSupport.normalizeOptional;
import static com.sep490.slms2026.imports.BulkImportSupport.requireText;
import static com.sep490.slms2026.imports.ExcelTenantDraftContractWorkbookReader.SHEET_DRAFT;

@Service
@RequiredArgsConstructor
public class BulkTenantDraftContractImportServiceImpl implements BulkTenantDraftContractImportService {

    private final ExcelTenantDraftContractWorkbookReader workbookReader;
    private final InboundContractRepository inboundContractRepository;
    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final TenantContractRepository tenantContractRepository;
    private final UserRepository userRepository;
    private final TenantOnboardingService tenantOnboardingService;

    @Override
    @Transactional
    public BulkImportResponse importWorkbook(MultipartFile file, boolean dryRun) {
        TenantDraftContractImportWorkbook workbook = workbookReader.read(file);
        if (workbook.getRows().isEmpty()) {
            throw new BulkImportValidationException("File Excel không có dòng dữ liệu", List.of(
                    error(SHEET_DRAFT, 2, null, "file", "Không có dòng hợp đồng nháp nào để import")));
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

        if (!errors.isEmpty()) {
            throw new BulkImportValidationException("File Excel có lỗi validation", errors);
        }

        if (dryRun) {
            List<BulkImportContractResultResponse> dryResults = resolved.stream()
                    .map(r -> BulkImportContractResultResponse.builder()
                            .importStatus(IMPORT_STATUS_IMPORTED)
                            .contractCode("(dry-run)")
                            .propertyId(r.property().getId())
                            .propertyName(r.property().getPropertyName())
                            .finalStatus(ContractStatus.DRAFT.name())
                            .message(buildPreviewMessage(r))
                            .build())
                    .toList();
            return BulkImportResponse.builder()
                    .dryRun(true)
                    .contractsProcessed(resolved.size())
                    .contractsSkipped(0)
                    .renovationLinesImported(0)
                    .equipmentRowsImported(0)
                    .results(dryResults)
                    .errors(List.of())
                    .build();
        }

        List<BulkImportContractResultResponse> results = new ArrayList<>();
        for (ResolvedDraftRow r : resolved) {
            OnboardTenantRequest request = toOnboardRequest(r);
            Long roomId = r.room() != null ? r.room().getId() : null;
            TenantContractResponse created = tenantOnboardingService.onboardTenant(
                    r.property().getId(), roomId, request);
            results.add(BulkImportContractResultResponse.builder()
                    .importStatus(IMPORT_STATUS_IMPORTED)
                    .contractCode(created.getContractCode())
                    .propertyId(created.getPropertyId())
                    .propertyName(r.property().getPropertyName())
                    .finalStatus(created.getStatus() != null ? created.getStatus().name() : ContractStatus.DRAFT.name())
                    .message("Đã tạo HĐ nháp cho " + created.getTenantFullName()
                            + (created.getRoomNumber() != null ? " — phòng " + created.getRoomNumber() : " — nguyên căn"))
                    .build());
        }

        return BulkImportResponse.builder()
                .dryRun(false)
                .contractsProcessed(results.size())
                .contractsSkipped(0)
                .renovationLinesImported(0)
                .equipmentRowsImported(0)
                .results(results)
                .errors(List.of())
                .build();
    }

    private ResolvedDraftRow validateAndResolve(TenantDraftContractImportRow row,
                                                Set<String> occupancyKeysInFile,
                                                List<BulkImportErrorResponse> errors) {
        int before = errors.size();
        String rowKey = "row-" + row.getRowNumber();

        requireText(errors, SHEET_DRAFT, row.getRowNumber(), rowKey, "Họ tên khách thuê", row.getFullName());
        requireText(errors, SHEET_DRAFT, row.getRowNumber(), rowKey, "CCCD", row.getCccd());
        requireText(errors, SHEET_DRAFT, row.getRowNumber(), rowKey, "Số điện thoại", row.getPhoneNumber());

        if (row.getMoveInDate() == null) {
            errors.add(error(SHEET_DRAFT, row.getRowNumber(), rowKey, "Ngày vào ở",
                    "Ngày vào ở không hợp lệ hoặc để trống (YYYY-MM-DD hoặc DD/MM/YYYY)"));
        }
        if (row.getEndDate() == null) {
            errors.add(error(SHEET_DRAFT, row.getRowNumber(), rowKey, "Ngày kết thúc",
                    "Ngày kết thúc không hợp lệ hoặc để trống"));
        }
        if (row.getRentAmount() == null || row.getRentAmount().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add(error(SHEET_DRAFT, row.getRowNumber(), rowKey, "Giá thuê/tháng",
                    "Giá thuê phải lớn hơn 0"));
        }
        if (row.getDeposit() == null || row.getDeposit().compareTo(BigDecimal.ZERO) < 0) {
            errors.add(error(SHEET_DRAFT, row.getRowNumber(), rowKey, "Tiền cọc",
                    "Tiền cọc không hợp lệ (điền Tiền cọc hoặc Số tháng cọc)"));
        }

        LocalDate today = LocalDate.now();
        if (row.getMoveInDate() != null && row.getEndDate() != null) {
            if (!row.getEndDate().isAfter(row.getMoveInDate())) {
                errors.add(error(SHEET_DRAFT, row.getRowNumber(), rowKey, "Ngày kết thúc",
                        "Ngày kết thúc phải sau ngày vào ở"));
            }
            if (row.getEndDate().isAfter(today.plusYears(5))) {
                errors.add(error(SHEET_DRAFT, row.getRowNumber(), rowKey, "Ngày kết thúc",
                        "Thời hạn thuê tối đa 5 năm"));
            }
        }

        Property property = resolveProperty(row, errors, rowKey);
        if (property == null) {
            return null;
        }
        if (property.getStatus() != PropertyStatus.ACTIVE) {
            errors.add(error(SHEET_DRAFT, row.getRowNumber(), rowKey, "BĐS",
                    "BĐS '" + property.getPropertyName() + "' chưa ACTIVE (status="
                            + property.getStatus() + ") — chưa cho thuê được"));
        }

        boolean byRoom = isRoomRental(row, property);
        Room room = null;
        if (byRoom) {
            String roomNumber = normalizeOptional(row.getRoomNumber());
            if (roomNumber.isBlank()) {
                errors.add(error(SHEET_DRAFT, row.getRowNumber(), rowKey, "Số phòng",
                        "Thuê theo phòng bắt buộc có Số phòng"));
            } else {
                room = roomRepository.findByPropertyIdAndRoomNumberAndDeletedIsFalse(
                                property.getId(), roomNumber)
                        .orElse(null);
                if (room == null) {
                    errors.add(error(SHEET_DRAFT, row.getRowNumber(), rowKey, "Số phòng",
                            "Không tìm thấy phòng '" + roomNumber + "' thuộc BĐS '"
                                    + property.getPropertyName() + "'"));
                } else if (room.getStatus() == RoomStatus.RENTED) {
                    errors.add(error(SHEET_DRAFT, row.getRowNumber(), rowKey, "Số phòng",
                            "Phòng '" + roomNumber + "' đang được cho thuê"));
                } else if (row.getMoveInDate() != null && row.getEndDate() != null) {
                    if (tenantContractRepository.existsByRoomIdAndStatus(room.getId(), ContractStatus.ACTIVE)) {
                        errors.add(error(SHEET_DRAFT, row.getRowNumber(), rowKey, "Số phòng",
                                "Phòng đã có hợp đồng đang hiệu lực"));
                    } else if (tenantContractRepository.existsOverlappingContractByRoom(
                            room.getId(), row.getMoveInDate(), row.getEndDate())) {
                        errors.add(error(SHEET_DRAFT, row.getRowNumber(), rowKey, "Số phòng",
                                "Phòng đã có hợp đồng chồng lấn thời gian"));
                    }
                }
            }
        } else {
            if (!normalizeOptional(row.getRoomNumber()).isBlank()) {
                errors.add(error(SHEET_DRAFT, row.getRowNumber(), rowKey, "Số phòng",
                        "Thuê nguyên căn — để trống cột Số phòng"));
            }
            if (row.getMoveInDate() != null && row.getEndDate() != null) {
                if (tenantContractRepository.existsByPropertyIdAndRoomIsNullAndStatus(
                        property.getId(), ContractStatus.ACTIVE)) {
                    errors.add(error(SHEET_DRAFT, row.getRowNumber(), rowKey, "BĐS",
                            "Căn nhà đã có hợp đồng nguyên căn đang hiệu lực"));
                } else if (tenantContractRepository.existsOverlappingContractByProperty(
                        property.getId(), row.getMoveInDate(), row.getEndDate())) {
                    errors.add(error(SHEET_DRAFT, row.getRowNumber(), rowKey, "BĐS",
                            "Căn nhà đã có hợp đồng chồng lấn thời gian"));
                }
            }
        }

        String occupancyKey = property.getId() + "|"
                + (room != null ? "R:" + room.getId() : "WHOLE");
        if (!occupancyKeysInFile.add(occupancyKey)) {
            errors.add(error(SHEET_DRAFT, row.getRowNumber(), rowKey, "BĐS",
                    "Trùng BĐS/phòng với dòng khác trong file"));
        }

        String managerId = resolveManagerId(row, errors, rowKey);

        if (errors.size() > before) {
            return null;
        }

        return new ResolvedDraftRow(row, property, room, managerId);
    }

    private Property resolveProperty(TenantDraftContractImportRow row,
                                     List<BulkImportErrorResponse> errors,
                                     String rowKey) {
        String inboundCode = normalizeOptional(row.getInboundContractCode());
        if (!inboundCode.isBlank()) {
            Optional<InboundContract> inbound = inboundContractRepository
                    .findByContractCodeIgnoreCaseWithProperty(inboundCode);
            if (inbound.isEmpty()) {
                errors.add(error(SHEET_DRAFT, row.getRowNumber(), rowKey, "Mã HĐ inbound",
                        "Không tìm thấy HĐ inbound '" + inboundCode + "' trong hệ thống"));
                return null;
            }
            return inbound.get().getProperty();
        }

        if (row.getPropertyId() != null) {
            Optional<Property> byId = propertyRepository.findById(row.getPropertyId());
            if (byId.isEmpty()) {
                errors.add(error(SHEET_DRAFT, row.getRowNumber(), rowKey, "Mã BĐS",
                        "Không tìm thấy BĐS ID " + row.getPropertyId()));
                return null;
            }
            return byId.get();
        }

        String name = normalizeOptional(row.getPropertyName());
        if (!name.isBlank()) {
            List<Property> matches = propertyRepository.findByPropertyNameIgnoreCase(name);
            if (matches.isEmpty()) {
                errors.add(error(SHEET_DRAFT, row.getRowNumber(), rowKey, "Tên tòa nhà",
                        "Không tìm thấy BĐS tên '" + name + "'"));
                return null;
            }
            if (matches.size() > 1) {
                errors.add(error(SHEET_DRAFT, row.getRowNumber(), rowKey, "Tên tòa nhà",
                        "Có " + matches.size() + " BĐS trùng tên '" + name
                                + "' — dùng Mã HĐ inbound hoặc Mã BĐS"));
                return null;
            }
            return matches.get(0);
        }

        errors.add(error(SHEET_DRAFT, row.getRowNumber(), rowKey, "BĐS",
                "Cần ít nhất một trong: Mã HĐ inbound, Mã BĐS, hoặc Tên tòa nhà"));
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

    private String resolveManagerId(TenantDraftContractImportRow row,
                                    List<BulkImportErrorResponse> errors,
                                    String rowKey) {
        String raw = normalizeOptional(row.getAssignedManagerRaw());
        if (raw.isBlank()) {
            return null;
        }
        // UUID
        try {
            UUID id = UUID.fromString(raw);
            User user = userRepository.findById(id).orElse(null);
            if (user == null) {
                errors.add(error(SHEET_DRAFT, row.getRowNumber(), rowKey, "SĐT quản lý đón khách",
                        "Không tìm thấy user UUID " + raw));
                return null;
            }
            if (user.getRole() != Role.ROLE_MANAGER && user.getRole() != Role.ROLE_ADMIN) {
                errors.add(error(SHEET_DRAFT, row.getRowNumber(), rowKey, "SĐT quản lý đón khách",
                        "User không phải MANAGER/ADMIN"));
                return null;
            }
            return id.toString();
        } catch (IllegalArgumentException ignored) {
            // phone lookup
        }

        User byPhone = userRepository.findByPhoneNumber(raw).orElse(null);
        if (byPhone == null) {
            errors.add(error(SHEET_DRAFT, row.getRowNumber(), rowKey, "SĐT quản lý đón khách",
                    "Không tìm thấy quản lý với SĐT '" + raw + "'"));
            return null;
        }
        if (byPhone.getRole() != Role.ROLE_MANAGER && byPhone.getRole() != Role.ROLE_ADMIN) {
            errors.add(error(SHEET_DRAFT, row.getRowNumber(), rowKey, "SĐT quản lý đón khách",
                    "SĐT '" + raw + "' không thuộc MANAGER/ADMIN"));
            return null;
        }
        return byPhone.getId().toString();
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
        request.setMoveInDate(row.getMoveInDate());
        request.setEndDate(row.getEndDate());
        request.setRentAmount(row.getRentAmount());
        request.setDeposit(row.getDeposit());
        request.setDepositMonths(row.getDepositMonths());
        request.setExpectedReceptionDate(row.getExpectedReceptionDate());
        request.setAssignedManagerId(r.managerId());
        request.setRequireDepositPayment(true);
        return request;
    }

    private static String buildPreviewMessage(ResolvedDraftRow r) {
        String unit = r.room() != null ? "phòng " + r.room().getRoomNumber() : "nguyên căn";
        return "Sẽ tạo DRAFT cho " + r.row().getFullName() + " — " + unit;
    }

    private record ResolvedDraftRow(
            TenantDraftContractImportRow row,
            Property property,
            Room room,
            String managerId
    ) {
    }
}
