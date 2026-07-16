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
public class ExcelRenovationImportWorkbookReader {

    public static final String SHEET_CONFIG = "1. Cau_Hinh_Khai_Thac";
    public static final String SHEET_ROOMS = "2. Danh_Sach_Phong";
    public static final String SHEET_RENOVATION = "3. Hop_Dong_Cai_Tao";
    public static final String SHEET_PURCHASED = "4. Thiet_Bi_Mua_Moi";

    public RenovationImportWorkbook read(MultipartFile file) {
        validateExcelFile(file);
        try (Workbook workbook = openWorkbook(file)) {
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            Sheet configSheet = requireSheet(workbook, SHEET_CONFIG);
            Sheet roomSheet = optionalSheet(workbook, SHEET_ROOMS);
            Sheet renovationSheet = optionalSheet(workbook, SHEET_RENOVATION);
            Sheet purchasedSheet = optionalSheet(workbook, SHEET_PURCHASED);

            return RenovationImportWorkbook.builder()
                    .configRows(readConfigRows(configSheet, formatter, evaluator))
                    .roomRows(roomSheet != null
                            ? readRoomRows(roomSheet, formatter, evaluator) : List.of())
                    .renovationLines(renovationSheet != null
                            ? readRenovationLines(renovationSheet, formatter, evaluator) : List.of())
                    .purchasedRows(purchasedSheet != null
                            ? readPurchasedRows(purchasedSheet, formatter, evaluator) : List.of())
                    .build();
        } catch (IOException ex) {
            throw new BusinessException("Không đọc được file Excel: " + ex.getMessage());
        }
    }

    private List<ExploitationConfigImportRow> readConfigRows(Sheet sheet,
                                                             DataFormatter formatter,
                                                             FormulaEvaluator evaluator) {
        Map<String, Integer> headers = readHeaders(sheet, formatter, evaluator);
        requireHeaders(headers, SHEET_CONFIG,
                "Mã hợp đồng thuê", "Hình thức khai thác");

        List<ExploitationConfigImportRow> rows = new ArrayList<>();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || isRowEmpty(row, headers, formatter, evaluator)) {
                continue;
            }
            String contractCode = readString(row, headers.get("Mã hợp đồng thuê"), formatter, evaluator);
            if (contractCode.isBlank()) {
                continue;
            }
            rows.add(ExploitationConfigImportRow.builder()
                    .rowNumber(rowIndex + 1)
                    .contractCode(contractCode)
                    .exploitationTypeRaw(readString(row, headers.get("Hình thức khai thác"), formatter, evaluator))
                    .exploitationRoomCount(readInteger(row, headers.get("Số phòng khai thác"), formatter, evaluator))
                    .build());
        }
        return rows;
    }

    private List<RoomImportRow> readRoomRows(Sheet sheet,
                                             DataFormatter formatter,
                                             FormulaEvaluator evaluator) {
        Map<String, Integer> headers = readHeaders(sheet, formatter, evaluator);
        requireHeaders(headers, SHEET_ROOMS,
                "Mã hợp đồng thuê", "Số phòng", "Tầng", "Diện tích phòng (m²)",
                "Chiều dài (m)", "Chiều rộng (m)");

        List<RoomImportRow> rows = new ArrayList<>();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || isRowEmpty(row, headers, formatter, evaluator)) {
                continue;
            }
            String contractCode = readString(row, headers.get("Mã hợp đồng thuê"), formatter, evaluator);
            if (contractCode.isBlank()) {
                continue;
            }
            rows.add(RoomImportRow.builder()
                    .rowNumber(rowIndex + 1)
                    .contractCode(contractCode)
                    .roomNumber(readString(row, headers.get("Số phòng"), formatter, evaluator))
                    .floor(readInteger(row, headers.get("Tầng"), formatter, evaluator))
                    .area(readDouble(row, headers.get("Diện tích phòng (m²)"), formatter, evaluator))
                    .length(readDouble(row, headers.get("Chiều dài (m)"), formatter, evaluator))
                    .width(readDouble(row, headers.get("Chiều rộng (m)"), formatter, evaluator))
                    .note(readOptionalString(row, headers.get("Ghi chú"), formatter, evaluator))
                    .build());
        }
        return rows;
    }

    private List<RenovationImportRow> readRenovationLines(Sheet sheet,
                                                          DataFormatter formatter,
                                                          FormulaEvaluator evaluator) {
        Map<String, Integer> headers = readHeaders(sheet, formatter, evaluator);
        requireHeaders(headers, SHEET_RENOVATION,
                "Mã hợp đồng thuê", "Mã danh mục cải tạo", "Chi phí cải tạo (VNĐ)");

        List<RenovationImportRow> rows = new ArrayList<>();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || isRowEmpty(row, headers, formatter, evaluator)) {
                continue;
            }
            String contractCode = readString(row, headers.get("Mã hợp đồng thuê"), formatter, evaluator);
            if (contractCode.isBlank()) {
                continue;
            }
            rows.add(RenovationImportRow.builder()
                    .rowNumber(rowIndex + 1)
                    .contractCode(contractCode)
                    .categoryCode(readString(row, headers.get("Mã danh mục cải tạo"), formatter, evaluator))
                    .categoryName(readOptionalString(row, headers.get("Tên danh mục (Gợi ý)"), formatter, evaluator))
                    .cost(readDecimal(row, headers.get("Chi phí cải tạo (VNĐ)"), formatter, evaluator))
                    .note(readOptionalString(row, headers.get("Ghi chú chi tiết"), formatter, evaluator))
                    .build());
        }
        return rows;
    }

    private List<PurchasedEquipmentImportRow> readPurchasedRows(Sheet sheet,
                                                                DataFormatter formatter,
                                                                FormulaEvaluator evaluator) {
        Map<String, Integer> headers = readHeaders(sheet, formatter, evaluator);
        requireHeaders(headers, SHEET_PURCHASED,
                "Mã hợp đồng thuê", "Tên Catalog thiết bị", "Trạng thái thiết bị",
                "Số lượng", "Đơn giá (VNĐ)", "Số tháng bảo hành",
                "Ngày bắt đầu bảo hành", "Ngày hết bảo hành",
                "Giá phạt hết bảo hành (VNĐ)");

        List<PurchasedEquipmentImportRow> rows = new ArrayList<>();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || isRowEmpty(row, headers, formatter, evaluator)) {
                continue;
            }
            String contractCode = readString(row, headers.get("Mã hợp đồng thuê"), formatter, evaluator);
            if (contractCode.isBlank()) {
                continue;
            }
            rows.add(PurchasedEquipmentImportRow.builder()
                    .rowNumber(rowIndex + 1)
                    .contractCode(contractCode)
                    .roomNumber(readOptionalString(row, headers.get("Số phòng"), formatter, evaluator))
                    .houseAreaRaw(readOptionalString(row, headers.get("Khu vực chung"), formatter, evaluator))
                    .catalogName(readString(row, headers.get("Tên Catalog thiết bị"), formatter, evaluator))
                    .statusRaw(readString(row, headers.get("Trạng thái thiết bị"), formatter, evaluator))
                    .quantity(readInteger(row, headers.get("Số lượng"), formatter, evaluator))
                    .price(readDecimal(row, headers.get("Đơn giá (VNĐ)"), formatter, evaluator))
                    .warrantyMonths(readInteger(row, headers.get("Số tháng bảo hành"), formatter, evaluator))
                    .warrantyStartDate(readDate(row, headers.get("Ngày bắt đầu bảo hành"), formatter, evaluator))
                    .warrantyEndDate(readDate(row, headers.get("Ngày hết bảo hành"), formatter, evaluator))
                    .penaltyFee(readDecimal(row, headers.get("Giá phạt hết bảo hành (VNĐ)"), formatter, evaluator))
                    .note(readOptionalString(row, headers.get("Ghi chú lắp đặt"), formatter, evaluator))
                    .actionRaw(readOptionalAction(row, headers, formatter, evaluator))
                    .build());
        }
        return rows;
    }
}
