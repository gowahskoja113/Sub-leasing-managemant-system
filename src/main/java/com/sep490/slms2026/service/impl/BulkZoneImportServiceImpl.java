package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.response.BulkImportContractResultResponse;
import com.sep490.slms2026.dto.response.BulkImportErrorResponse;
import com.sep490.slms2026.dto.response.BulkImportResponse;
import com.sep490.slms2026.entity.Zone;
import com.sep490.slms2026.exception.BulkImportValidationException;
import com.sep490.slms2026.imports.ExcelZoneWorkbookReader;
import com.sep490.slms2026.imports.ExcelZoneWorkbookReader.ZoneImportRow;
import com.sep490.slms2026.repository.ZoneRepository;
import com.sep490.slms2026.service.BulkZoneImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

import static com.sep490.slms2026.imports.BulkImportSupport.IMPORT_STATUS_IMPORTED;
import static com.sep490.slms2026.imports.BulkImportSupport.IMPORT_STATUS_SKIPPED;

@Service
@RequiredArgsConstructor
public class BulkZoneImportServiceImpl implements BulkZoneImportService {

    private final ExcelZoneWorkbookReader excelZoneWorkbookReader;
    private final ZoneRepository zoneRepository;

    @Override
    @Transactional
    public BulkImportResponse importZonesWorkbook(MultipartFile file, boolean dryRun) {
        List<ZoneImportRow> rows = excelZoneWorkbookReader.read(file);
        if (rows.isEmpty()) {
            throw new BulkImportValidationException("File Excel không có dòng dữ liệu", List.of(
                    BulkImportErrorResponse.builder()
                            .sheet("Zones")
                            .rowNumber(0)
                            .field(null)
                            .message("Không có dòng hợp lệ (cần cột Tỉnh/Thành phố)")
                            .build()));
        }

        List<BulkImportErrorResponse> errors = new ArrayList<>();
        List<BulkImportContractResultResponse> results = new ArrayList<>();
        int imported = 0;
        int skipped = 0;

        for (ZoneImportRow row : rows) {
            try {
                String label = row.getProvince()
                        + (row.getDistrict() != null ? " / " + row.getDistrict() : "");

                Zone city = zoneRepository.findCityLevelZoneByNameIgnoreCase(row.getProvince()).orElse(null);
                boolean cityCreated = false;
                if (city == null) {
                    if (!dryRun) {
                        city = new Zone();
                        city.setName(row.getProvince().trim());
                        city.setLevel(1);
                        city.setDescription(row.getDistrict() == null ? row.getDescription() : null);
                        city = zoneRepository.save(city);
                    }
                    cityCreated = true;
                }

                boolean districtCreated = false;
                boolean districtAlreadyExists = false;
                if (row.getDistrict() != null && !row.getDistrict().isBlank()) {
                    if (city != null) {
                        var existingDistrict = zoneRepository
                                .findDistrictLevelZoneByNameIgnoreCaseAndParentId(row.getDistrict(), city.getId());
                        if (existingDistrict.isPresent()) {
                            districtAlreadyExists = true;
                        } else if (!dryRun) {
                            Zone district = new Zone();
                            district.setName(row.getDistrict().trim());
                            district.setLevel(2);
                            district.setParent(city);
                            district.setDescription(row.getDescription());
                            zoneRepository.save(district);
                            districtCreated = true;
                        } else {
                            districtCreated = true;
                        }
                    } else if (dryRun) {
                        districtCreated = true;
                    }
                } else {
                    districtAlreadyExists = !cityCreated;
                }

                if (!cityCreated && (row.getDistrict() == null || districtAlreadyExists) && !districtCreated) {
                    skipped++;
                    results.add(BulkImportContractResultResponse.builder()
                            .contractCode(label)
                            .importStatus(IMPORT_STATUS_SKIPPED)
                            .message("Đã tồn tại — bỏ qua")
                            .build());
                    continue;
                }

                imported++;
                String msg;
                if (cityCreated && districtCreated) {
                    msg = "Tạo tỉnh + quận";
                } else if (cityCreated) {
                    msg = "Tạo tỉnh/thành";
                } else {
                    msg = "Tạo quận/huyện";
                }
                results.add(BulkImportContractResultResponse.builder()
                        .contractCode(label)
                        .importStatus(IMPORT_STATUS_IMPORTED)
                        .message(dryRun ? "[dryRun] " + msg : msg)
                        .build());
            } catch (Exception ex) {
                errors.add(BulkImportErrorResponse.builder()
                        .sheet("Zones")
                        .rowNumber(row.getRowNumber())
                        .field(ExcelZoneWorkbookReader.COL_PROVINCE)
                        .message(ex.getMessage())
                        .build());
            }
        }

        if (!errors.isEmpty() && imported == 0) {
            throw new BulkImportValidationException("File Excel có lỗi validation", errors);
        }

        return BulkImportResponse.builder()
                .dryRun(dryRun)
                .contractsProcessed(imported)
                .contractsSkipped(skipped)
                .results(results)
                .errors(errors.isEmpty() ? null : errors)
                .build();
    }
}
