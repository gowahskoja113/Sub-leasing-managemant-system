package com.sep490.slms2026.imports;

import com.sep490.slms2026.exception.BusinessException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Component
public class ExcelOnboardingWorkbookReader {

    private static final String SHEET_LEASE = "1. Hop_Dong_Thue";
    private static final String SHEET_RENOVATION = "2. Hop_Dong_Cai_Tao";
    private static final String SHEET_EQUIPMENT = "3. Phan_Bo_Thiet_Bi";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    public OnboardingImportWorkbook read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("File Excel không được để trống");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
            throw new BusinessException("Chỉ hỗ trợ file .xlsx hoặc .xls");
        }

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            Sheet leaseSheet = requireSheet(workbook, SHEET_LEASE);
            Sheet renovationSheet = requireSheet(workbook, SHEET_RENOVATION);
            Sheet equipmentSheet = requireSheet(workbook, SHEET_EQUIPMENT);

            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            return OnboardingImportWorkbook.builder()
                    .leaseContracts(readLeaseContracts(leaseSheet, formatter, evaluator))
                    .renovationLines(readRenovationLines(renovationSheet, formatter, evaluator))
                    .equipmentRows(readEquipmentRows(equipmentSheet, formatter, evaluator))
                    .build();
        } catch (IOException ex) {
            throw new BusinessException("Không đọc được file Excel: " + ex.getMessage());
        }
    }

    private Sheet requireSheet(Workbook workbook, String sheetName) {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            throw new BusinessException("Thiếu sheet bắt buộc: " + sheetName);
        }
        return sheet;
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
        int lastRow = sheet.getLastRowNum();
        for (int rowIndex = 1; rowIndex <= lastRow; rowIndex++) {
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

    private List<RenovationImportRow> readRenovationLines(Sheet sheet,
                                                            DataFormatter formatter,
                                                            FormulaEvaluator evaluator) {
        Map<String, Integer> headers = readHeaders(sheet, formatter, evaluator);
        requireHeaders(headers, SHEET_RENOVATION,
                "Mã hợp đồng thuê", "Mã danh mục cải tạo", "Chi phí cải tạo (VNĐ)");

        List<RenovationImportRow> rows = new ArrayList<>();
        int lastRow = sheet.getLastRowNum();
        for (int rowIndex = 1; rowIndex <= lastRow; rowIndex++) {
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

    private List<EquipmentImportRow> readEquipmentRows(Sheet sheet,
                                                        DataFormatter formatter,
                                                        FormulaEvaluator evaluator) {
        Map<String, Integer> headers = readHeaders(sheet, formatter, evaluator);
        requireHeaders(headers, SHEET_EQUIPMENT,
                "Mã hợp đồng thuê", "Tên Catalog thiết bị", "Nguồn gốc thiết bị",
                "Trạng thái thiết bị", "Số lượng", "Đơn giá (VNĐ)");

        List<EquipmentImportRow> rows = new ArrayList<>();
        int lastRow = sheet.getLastRowNum();
        for (int rowIndex = 1; rowIndex <= lastRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || isRowEmpty(row, headers, formatter, evaluator)) {
                continue;
            }

            String contractCode = readString(row, headers.get("Mã hợp đồng thuê"), formatter, evaluator);
            if (contractCode.isBlank()) {
                continue;
            }

            rows.add(EquipmentImportRow.builder()
                    .rowNumber(rowIndex + 1)
                    .contractCode(contractCode)
                    .roomNumber(readOptionalString(row, headers.get("Số phòng"), formatter, evaluator))
                    .houseAreaRaw(readOptionalString(row, headers.get("Khu vực chung"), formatter, evaluator))
                    .catalogName(readString(row, headers.get("Tên Catalog thiết bị"), formatter, evaluator))
                    .sourceRaw(readString(row, headers.get("Nguồn gốc thiết bị"), formatter, evaluator))
                    .statusRaw(readString(row, headers.get("Trạng thái thiết bị"), formatter, evaluator))
                    .quantity(readInteger(row, headers.get("Số lượng"), formatter, evaluator))
                    .price(readDecimal(row, headers.get("Đơn giá (VNĐ)"), formatter, evaluator))
                    .note(readOptionalString(row, headers.get("Ghi chú lắp đặt"), formatter, evaluator))
                    .build());
        }
        return rows;
    }

    private Map<String, Integer> readHeaders(Sheet sheet,
                                               DataFormatter formatter,
                                               FormulaEvaluator evaluator) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            throw new BusinessException("Sheet " + sheet.getSheetName() + " thiếu dòng header");
        }

        Map<String, Integer> headers = new LinkedHashMap<>();
        for (Cell cell : headerRow) {
            String header = readString(cell, formatter, evaluator).trim();
            if (!header.isBlank()) {
                headers.put(header, cell.getColumnIndex());
            }
        }
        return headers;
    }

    private void requireHeaders(Map<String, Integer> headers, String sheetName, String... required) {
        for (String header : required) {
            if (!headers.containsKey(header)) {
                throw new BusinessException("Sheet " + sheetName + " thiếu cột bắt buộc: " + header);
            }
        }
    }

    private boolean isRowEmpty(Row row,
                               Map<String, Integer> headers,
                               DataFormatter formatter,
                               FormulaEvaluator evaluator) {
        for (Integer columnIndex : headers.values()) {
            String value = readString(row, columnIndex, formatter, evaluator);
            if (!value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String readOptionalString(Row row, Integer columnIndex,
                                      DataFormatter formatter, FormulaEvaluator evaluator) {
        if (columnIndex == null) {
            return "";
        }
        return readString(row, columnIndex, formatter, evaluator);
    }

    private String readString(Row row, Integer columnIndex,
                              DataFormatter formatter, FormulaEvaluator evaluator) {
        if (columnIndex == null || row == null) {
            return "";
        }
        Cell cell = row.getCell(columnIndex);
        return readString(cell, formatter, evaluator).trim();
    }

    private String readString(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.BOOLEAN) {
            return cell.getBooleanCellValue() ? "TRUE" : "FALSE";
        }
        return formatter.formatCellValue(cell, evaluator).trim();
    }

    private Integer readInteger(Row row, Integer columnIndex,
                                DataFormatter formatter, FormulaEvaluator evaluator) {
        String raw = readString(row, columnIndex, formatter, evaluator);
        if (raw.isBlank()) {
            return null;
        }
        try {
            return (int) Math.round(Double.parseDouble(raw.replace(",", "")));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Double readDouble(Row row, Integer columnIndex,
                              DataFormatter formatter, FormulaEvaluator evaluator) {
        String raw = readString(row, columnIndex, formatter, evaluator);
        if (raw.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(raw.replace(",", ""));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal readDecimal(Row row, Integer columnIndex,
                                   DataFormatter formatter, FormulaEvaluator evaluator) {
        String raw = readString(row, columnIndex, formatter, evaluator);
        if (raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.replace(",", ""));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal readDecimalOptional(Row row, Integer columnIndex,
                                           DataFormatter formatter, FormulaEvaluator evaluator) {
        if (columnIndex == null) {
            return null;
        }
        return readDecimal(row, columnIndex, formatter, evaluator);
    }

    private LocalDate readDate(Row row, Integer columnIndex,
                               DataFormatter formatter, FormulaEvaluator evaluator) {
        if (columnIndex == null || row == null) {
            return null;
        }
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        String raw = formatter.formatCellValue(cell, evaluator).trim();
        if (raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw, DATE_FORMAT);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
