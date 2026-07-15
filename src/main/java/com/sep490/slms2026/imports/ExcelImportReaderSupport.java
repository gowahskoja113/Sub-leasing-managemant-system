package com.sep490.slms2026.imports;

import com.sep490.slms2026.exception.BusinessException;
import org.apache.poi.ss.usermodel.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ExcelImportReaderSupport {

    static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private ExcelImportReaderSupport() {
    }

    public static void validateExcelFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("File Excel không được để trống");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
            throw new BusinessException("Chỉ hỗ trợ file .xlsx hoặc .xls");
        }
    }

    public static Workbook openWorkbook(MultipartFile file) {
        validateExcelFile(file);
        try (InputStream inputStream = file.getInputStream()) {
            return new org.apache.poi.xssf.usermodel.XSSFWorkbook(inputStream);
        } catch (IOException ex) {
            throw new BusinessException("Không đọc được file Excel: " + ex.getMessage());
        }
    }

    public static Sheet requireSheet(Workbook workbook, String sheetName) {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            throw new BusinessException("Thiếu sheet bắt buộc: " + sheetName);
        }
        return sheet;
    }

    public static Sheet optionalSheet(Workbook workbook, String sheetName) {
        return workbook.getSheet(sheetName);
    }

    public static Map<String, Integer> readHeaders(Sheet sheet,
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

    public static void requireHeaders(Map<String, Integer> headers, String sheetName, String... required) {
        for (String header : required) {
            if (!headers.containsKey(header)) {
                throw new BusinessException("Sheet " + sheetName + " thiếu cột bắt buộc: " + header);
            }
        }
    }

    public static boolean isRowEmpty(Row row,
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

    public static String readOptionalString(Row row, Integer columnIndex,
                                            DataFormatter formatter, FormulaEvaluator evaluator) {
        if (columnIndex == null) {
            return "";
        }
        return readString(row, columnIndex, formatter, evaluator);
    }

    public static String readString(Row row, Integer columnIndex,
                                    DataFormatter formatter, FormulaEvaluator evaluator) {
        if (columnIndex == null || row == null) {
            return "";
        }
        Cell cell = row.getCell(columnIndex);
        return readString(cell, formatter, evaluator).trim();
    }

    public static String readString(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.BOOLEAN) {
            return cell.getBooleanCellValue() ? "TRUE" : "FALSE";
        }
        return formatter.formatCellValue(cell, evaluator).trim();
    }

    public static Integer readInteger(Row row, Integer columnIndex,
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

    public static Double readDouble(Row row, Integer columnIndex,
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

    public static BigDecimal readDecimal(Row row, Integer columnIndex,
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

    public static LocalDate readDate(Row row, Integer columnIndex,
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
        return parseFlexibleDate(raw);
    }

    /** Hỗ trợ YYYY-MM-DD, DD/MM/YYYY, D/M/YYYY. */
    public static LocalDate parseFlexibleDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        for (DateTimeFormatter formatter : FLEXIBLE_DATE_FORMATS) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        return null;
    }

    private static final java.util.List<DateTimeFormatter> FLEXIBLE_DATE_FORMATS = java.util.List.of(
            DATE_FORMAT,
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy")
    );

    public static String readOptionalAction(Row row,
                                            Map<String, Integer> headers,
                                            DataFormatter formatter,
                                            FormulaEvaluator evaluator) {
        Integer columnIndex = headers.get("Hành động");
        if (columnIndex == null) {
            return null;
        }
        return readOptionalString(row, columnIndex, formatter, evaluator);
    }
}
