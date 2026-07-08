package com.sep490.slms2026.imports;

import com.sep490.slms2026.dto.response.BulkImportErrorResponse;
import com.sep490.slms2026.entity.Zone;
import com.sep490.slms2026.repository.InboundContractRepository;
import com.sep490.slms2026.repository.PropertyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class BulkImportSupport {

    public static final String IMPORT_STATUS_IMPORTED = "IMPORTED";
    public static final String IMPORT_STATUS_SKIPPED = "SKIPPED";
    public static final String SKIP_REASON_DUPLICATE_CONTRACT = "Mã hợp đồng đã tồn tại — bỏ qua";
    public static final String SKIP_REASON_DUPLICATE_ADDRESS = "Địa chỉ đã được dùng cho tòa nhà khác — bỏ qua";
    public static final String SKIP_REASON_DUPLICATE_ADDRESS_IN_FILE = "Địa chỉ bị trùng trong file — bỏ qua";

    private final ZoneImportResolver zoneImportResolver;
    private final InboundContractRepository inboundContractRepository;
    private final PropertyRepository propertyRepository;

    public Map<String, String> resolveSkippedLeaseContracts(List<LeaseContractImportRow> leaseRows) {
        Map<String, String> skipped = new LinkedHashMap<>();
        Set<String> seenAddressesInFile = new HashSet<>();

        for (LeaseContractImportRow row : leaseRows) {
            String code = normalizeOptional(row.getContractCode());
            if (code.isBlank() || skipped.containsKey(code)) {
                continue;
            }
            if (inboundContractRepository.existsByContractCode(code)) {
                skipped.put(code, SKIP_REASON_DUPLICATE_CONTRACT);
                continue;
            }
            String fullAddress = tryBuildFullAddress(row);
            if (fullAddress == null) {
                continue;
            }
            if (propertyRepository.existsByAddressIgnoreCase(fullAddress)) {
                skipped.put(code, SKIP_REASON_DUPLICATE_ADDRESS);
                continue;
            }
            if (!seenAddressesInFile.add(fullAddress.toLowerCase(Locale.ROOT))) {
                skipped.put(code, SKIP_REASON_DUPLICATE_ADDRESS_IN_FILE);
            }
        }
        return skipped;
    }

    public String tryBuildFullAddress(LeaseContractImportRow row) {
        try {
            Zone districtZone = zoneImportResolver.resolveDistrictZone(row.getProvince(), row.getDistrict());
            String shortAddress = row.getAddress() == null ? "" : row.getAddress().trim();
            return ZoneImportResolver.buildFullAddress(shortAddress, districtZone);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static String normalizeOptional(String value) {
        return value == null ? "" : value.trim();
    }

    public static void requireText(List<BulkImportErrorResponse> errors,
                                   String sheet,
                                   int rowNumber,
                                   String contractCode,
                                   String field,
                                   String value) {
        if (normalizeOptional(value).isBlank()) {
            errors.add(error(sheet, rowNumber, contractCode, field, field + " không được để trống"));
        }
    }

    public static BulkImportErrorResponse error(String sheet,
                                                int rowNumber,
                                                String contractCode,
                                                String field,
                                                String message) {
        return BulkImportErrorResponse.builder()
                .sheet(sheet)
                .rowNumber(rowNumber)
                .contractCode(contractCode)
                .field(field)
                .message(message)
                .build();
    }

    public static void validateEquipmentImportAction(List<BulkImportErrorResponse> errors,
                                                     String sheet,
                                                     PurchasedEquipmentImportRow row) {
        String raw = normalizeOptional(row.getActionRaw());
        if (raw.isBlank()) {
            return;
        }
        String upper = raw.toUpperCase(Locale.ROOT);
        if (!Set.of("THEM_MOI", "THAY_THE", "REPLACE", "THAY THE").contains(upper)) {
            errors.add(error(sheet, row.getRowNumber(), row.getContractCode(), "Hành động",
                    "Giá trị không hợp lệ. Chọn THEM_MOI hoặc THAY_THE"));
        }
    }
}
