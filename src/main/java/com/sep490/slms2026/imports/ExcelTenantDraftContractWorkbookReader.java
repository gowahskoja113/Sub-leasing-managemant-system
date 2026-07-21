package com.sep490.slms2026.imports;

import com.sep490.slms2026.exception.BusinessException;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.sep490.slms2026.imports.ExcelImportReaderSupport.*;

@Component
public class ExcelTenantDraftContractWorkbookReader {

    public static final String SHEET_DRAFT = "1. Hop_Dong_Nhap_Khach";

    public TenantDraftContractImportWorkbook read(MultipartFile file) {
        validateExcelFile(file);
        try (Workbook workbook = openWorkbook(file)) {
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Sheet sheet = requireSheet(workbook, SHEET_DRAFT);
            return TenantDraftContractImportWorkbook.builder()
                    .rows(readRows(sheet, formatter, evaluator))
                    .build();
        } catch (IOException ex) {
            throw new BusinessException("Không đọc được file Excel: " + ex.getMessage());
        }
    }

    private List<TenantDraftContractImportRow> readRows(Sheet sheet,
                                                        DataFormatter formatter,
                                                        FormulaEvaluator evaluator) {
        Map<String, Integer> headers = readHeaders(sheet, formatter, evaluator);
        requireHeaders(headers, SHEET_DRAFT,
                "Họ tên khách thuê", "CCCD", "Số điện thoại",
                "Ngày vào ở", "Ngày kết thúc", "Giá thuê/tháng");

        List<TenantDraftContractImportRow> rows = new ArrayList<>();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || isRowEmpty(row, headers, formatter, evaluator)) {
                continue;
            }

            String propertyIdRaw = readOptionalString(row, headers.get("Mã BĐS"), formatter, evaluator);
            Long propertyId = null;
            if (!propertyIdRaw.isBlank()) {
                try {
                    propertyId = Long.parseLong(propertyIdRaw.replace(",", "").trim());
                } catch (NumberFormatException ignored) {
                    // giữ null — validate sẽ báo lỗi
                }
            }

            BigDecimal rentAmount = readDecimal(row, headers.get("Giá thuê/tháng"), formatter, evaluator);
            Integer depositMonths = readInteger(row, headers.get("Số tháng cọc"), formatter, evaluator);
            BigDecimal deposit = readDecimal(row, headers.get("Tiền cọc"), formatter, evaluator);
            if (deposit == null && rentAmount != null && depositMonths != null && depositMonths > 0) {
                deposit = rentAmount.multiply(BigDecimal.valueOf(depositMonths));
            }

            rows.add(TenantDraftContractImportRow.builder()
                    .rowNumber(rowIndex + 1)
                    .inboundContractCode(readOptionalString(row, headers.get("Mã HĐ inbound"), formatter, evaluator))
                    .propertyId(propertyId)
                    .propertyName(readOptionalString(row, headers.get("Tên tòa nhà"), formatter, evaluator))
                    .rentTypeRaw(readOptionalString(row, headers.get("Loại thuê"), formatter, evaluator))
                    .roomNumber(readOptionalString(row, headers.get("Số phòng"), formatter, evaluator))
                    .fullName(readString(row, headers.get("Họ tên khách thuê"), formatter, evaluator))
                    .cccd(readString(row, headers.get("CCCD"), formatter, evaluator))
                    .phoneNumber(readString(row, headers.get("Số điện thoại"), formatter, evaluator))
                    .dateOfBirth(readDate(row, headers.get("Ngày sinh"), formatter, evaluator))
                    .cccdIssueDate(readDate(row, headers.get("Ngày cấp CCCD"), formatter, evaluator))
                    .cccdIssuePlace(readOptionalString(row, headers.get("Nơi cấp CCCD"), formatter, evaluator))
                    .permanentAddress(readOptionalString(row, headers.get("Hộ khẩu thường trú"), formatter, evaluator))
                    .moveInDate(readDate(row, headers.get("Ngày vào ở"), formatter, evaluator))
                    .endDate(readDate(row, headers.get("Ngày kết thúc"), formatter, evaluator))
                    .rentAmount(rentAmount)
                    .depositMonths(depositMonths)
                    .deposit(deposit)
                    .expectedReceptionDate(readDate(row, headers.get("Ngày đón khách dự kiến"), formatter, evaluator))
                    .build());
        }
        return rows;
    }
}
