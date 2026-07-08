package com.sep490.slms2026.imports;

import com.sep490.slms2026.exception.BusinessException;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.sep490.slms2026.imports.ExcelImportReaderSupport.*;

@Component
public class ExcelLeaseImportWorkbookReader {

    public static final String SHEET_LEASE = "1. Hop_Dong_Thue";
    public static final String SHEET_HANDOVER = "2. Thiet_Bi_Ban_Giao";

    public LeaseImportWorkbook read(MultipartFile file) {
        validateExcelFile(file);
        try (Workbook workbook = openWorkbook(file)) {
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            Sheet leaseSheet = requireSheet(workbook, SHEET_LEASE);
            Sheet handoverSheet = optionalSheet(workbook, SHEET_HANDOVER);

            return LeaseImportWorkbook.builder()
                    .leaseContracts(readLeaseContracts(leaseSheet, formatter, evaluator))
                    .handoverRows(handoverSheet != null
                            ? readHandoverRows(handoverSheet, formatter, evaluator) : List.of())
                    .build();
        } catch (IOException ex) {
            throw new BusinessException("Không đọc được file Excel: " + ex.getMessage());
        }
    }

    private List<LeaseContractImportRow> readLeaseContracts(Sheet sheet,
                                                          DataFormatter formatter,
                                                          FormulaEvaluator evaluator) {
        Map<String, Integer> headers = readHeaders(sheet, formatter, evaluator);
        requireHeaders(headers, SHEET_LEASE,
                "Mã hợp đồng", "Tên tòa nhà", "Địa chỉ chi tiết", "Quận/Huyện",
                "Tỉnh/Thành phố", "Diện tích (m²)", "Chiều dài (m)", "Chiều rộng (m)",
                "Tổng số tầng", "Tổng số phòng", "Tên chủ nhà",
                "Tổng tiền thuê", "Ngày bắt đầu", "Ngày kết thúc", "Mô tả chi tiết");

        List<LeaseContractImportRow> rows = new ArrayList<>();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || isRowEmpty(row, headers, formatter, evaluator)) {
                continue;
            }
            String contractCode = readString(row, headers.get("Mã hợp đồng"), formatter, evaluator);
            if (contractCode.isBlank()) {
                continue;
            }
            rows.add(LeaseContractImportRow.builder()
                    .rowNumber(rowIndex + 1)
                    .contractCode(contractCode)
                    .propertyName(readString(row, headers.get("Tên tòa nhà"), formatter, evaluator))
                    .address(readString(row, headers.get("Địa chỉ chi tiết"), formatter, evaluator))
                    .district(readString(row, headers.get("Quận/Huyện"), formatter, evaluator))
                    .province(readString(row, headers.get("Tỉnh/Thành phố"), formatter, evaluator))
                    .areaSize(readDouble(row, headers.get("Diện tích (m²)"), formatter, evaluator))
                    .length(readDouble(row, headers.get("Chiều dài (m)"), formatter, evaluator))
                    .width(readDouble(row, headers.get("Chiều rộng (m)"), formatter, evaluator))
                    .totalFloor(readInteger(row, headers.get("Tổng số tầng"), formatter, evaluator))
                    .totalRooms(readInteger(row, headers.get("Tổng số phòng"), formatter, evaluator))
                    .ownerName(readString(row, headers.get("Tên chủ nhà"), formatter, evaluator))
                    .totalRentAmount(readDecimal(row, headers.get("Tổng tiền thuê"), formatter, evaluator))
                    .startDate(readDate(row, headers.get("Ngày bắt đầu"), formatter, evaluator))
                    .endDate(readDate(row, headers.get("Ngày kết thúc"), formatter, evaluator))
                    .descriptions(readString(row, headers.get("Mô tả chi tiết"), formatter, evaluator))
                    .build());
        }
        return rows;
    }

    private List<HandoverEquipmentImportRow> readHandoverRows(Sheet sheet,
                                                              DataFormatter formatter,
                                                              FormulaEvaluator evaluator) {
        Map<String, Integer> headers = readHeaders(sheet, formatter, evaluator);
        requireHeaders(headers, SHEET_HANDOVER,
                "Mã hợp đồng thuê", "Tên thiết bị", "Trạng thái thiết bị", "Số lượng");

        List<HandoverEquipmentImportRow> rows = new ArrayList<>();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || isRowEmpty(row, headers, formatter, evaluator)) {
                continue;
            }
            String contractCode = readString(row, headers.get("Mã hợp đồng thuê"), formatter, evaluator);
            if (contractCode.isBlank()) {
                continue;
            }
            rows.add(HandoverEquipmentImportRow.builder()
                    .rowNumber(rowIndex + 1)
                    .contractCode(contractCode)
                    .equipmentName(readString(row, headers.get("Tên thiết bị"), formatter, evaluator))
                    .description(readOptionalString(row, headers.get("Mô tả chi tiết"), formatter, evaluator))
                    .locationNote(readOptionalString(row, headers.get("Mô tả vị trí"), formatter, evaluator))
                    .statusRaw(readString(row, headers.get("Trạng thái thiết bị"), formatter, evaluator))
                    .quantity(readInteger(row, headers.get("Số lượng"), formatter, evaluator))
                    .note(readOptionalString(row, headers.get("Ghi chú"), formatter, evaluator))
                    .build());
        }
        return rows;
    }
}
