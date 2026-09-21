package com.sep490.slms2026.imports;

import com.sep490.slms2026.exception.BusinessException;
import lombok.Builder;
import lombok.Getter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Đọc Excel khu vực — sheet đầu tiên hoặc tên {@code Zones}.
 * Cột: {@code Tỉnh/Thành phố}, {@code Quận/Huyện} (tuỳ chọn), {@code Mô tả} (tuỳ chọn).
 */
@Component
public class ExcelZoneWorkbookReader {

    public static final String COL_PROVINCE = "Tỉnh/Thành phố";
    public static final String COL_DISTRICT = "Quận/Huyện";
    public static final String COL_DESCRIPTION = "Mô tả";

    public List<ZoneImportRow> read(MultipartFile file) {
        ExcelImportReaderSupport.validateExcelFile(file);
        try (InputStream in = file.getInputStream(); Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheet("Zones");
            if (sheet == null) {
                sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            }
            if (sheet == null) {
                throw new BusinessException("File Excel không có sheet dữ liệu");
            }
            DataFormatter formatter = ExcelImportReaderSupport.usFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Map<String, Integer> headers = ExcelImportReaderSupport.readHeaders(sheet, formatter, evaluator);
            ExcelImportReaderSupport.requireHeaders(headers, sheet.getSheetName(), COL_PROVINCE);

            List<ZoneImportRow> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || ExcelImportReaderSupport.isRowEmpty(row, headers, formatter, evaluator)) {
                    continue;
                }
                String province = ExcelImportReaderSupport.readOptionalString(
                        row, headers.get(COL_PROVINCE), formatter, evaluator).trim();
                String district = headers.containsKey(COL_DISTRICT)
                        ? ExcelImportReaderSupport.readOptionalString(
                        row, headers.get(COL_DISTRICT), formatter, evaluator).trim()
                        : "";
                String description = headers.containsKey(COL_DESCRIPTION)
                        ? ExcelImportReaderSupport.readOptionalString(
                        row, headers.get(COL_DESCRIPTION), formatter, evaluator).trim()
                        : "";
                if (province.isBlank()) {
                    continue;
                }
                rows.add(ZoneImportRow.builder()
                        .rowNumber(i + 1)
                        .province(province)
                        .district(district.isBlank() ? null : district)
                        .description(description.isBlank() ? null : description)
                        .build());
            }
            return rows;
        } catch (IOException e) {
            throw new BusinessException("Không đọc được file Excel: " + e.getMessage());
        }
    }

    @Getter
    @Builder
    public static class ZoneImportRow {
        private final int rowNumber;
        private final String province;
        private final String district;
        private final String description;
    }
}
