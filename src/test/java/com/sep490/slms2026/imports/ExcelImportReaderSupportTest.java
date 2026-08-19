package com.sep490.slms2026.imports;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ExcelImportReaderSupportTest {

    private Locale previousLocale;

    @BeforeEach
    void pinVietnameseLocale() {
        previousLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("vi-VN"));
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(previousLocale);
    }

    @Test
    void numericCellKeepsDecimalOnViVnJvm() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();
            Row row = sheet.createRow(0);
            row.createCell(0).setCellValue(29.6);
            DataFormatter formatter = ExcelImportReaderSupport.usFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            assertEquals(29.6, ExcelImportReaderSupport.readDouble(row, 0, formatter, evaluator));
            assertEquals(0, BigDecimal.valueOf(29.6).compareTo(
                    ExcelImportReaderSupport.readDecimal(row, 0, formatter, evaluator)));
        }
    }

    @Test
    void textCellParsesVietnameseAndEnglishDecimals() {
        assertEquals(29.6, ExcelImportReaderSupport.parseFlexibleNumber("29,6"));
        assertEquals(29.6, ExcelImportReaderSupport.parseFlexibleNumber("29.6"));
        assertEquals(1234.5, ExcelImportReaderSupport.parseFlexibleNumber("1.234,5"));
        assertEquals(1234.5, ExcelImportReaderSupport.parseFlexibleNumber("1,234.5"));
        assertEquals(12_000_000d, ExcelImportReaderSupport.parseFlexibleNumber("12.000.000"));
        assertEquals(12_000_000d, ExcelImportReaderSupport.parseFlexibleNumber("12,000,000"));
        assertNull(ExcelImportReaderSupport.parseFlexibleNumber(""));
        assertNull(ExcelImportReaderSupport.parseFlexibleNumber("abc"));
    }
}
