package com.sep490.slms2026.imports;

import com.sep490.slms2026.entity.Zone;
import com.sep490.slms2026.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Tra cứu Zone khi import Excel.
 * Hệ thống chỉ có 2 cấp:
 * - level 1: Tỉnh/Thành phố
 * - level 2: Quận/Huyện (gán vào Property.zone)
 * Xã/Phường không phải Zone — chỉ ghép vào địa chỉ chi tiết.
 */
@Component
@RequiredArgsConstructor
public class ZoneImportResolver {

    private final ZoneRepository zoneRepository;

    /**
     * @return Zone cấp Quận/Huyện (level 2) để gán vào Property.
     */
    public Zone resolveDistrictZone(String cityName, String districtName) {
        Zone cityZone = zoneRepository.findCityLevelZoneByNameIgnoreCase(cityName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy Tỉnh/Thành phố (Zone level 1): " + cityName));

        return zoneRepository.findDistrictLevelZoneByNameIgnoreCaseAndParentId(districtName, cityZone.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy Quận/Huyện (Zone level 2) '"
                                + districtName + "' thuộc " + cityName));
    }

    public static String buildShortAddress(String addressDetail, String ward) {
        String address = addressDetail == null ? "" : addressDetail.trim();
        String wardName = ward == null ? "" : ward.trim();
        if (wardName.isBlank() || address.toLowerCase().contains(wardName.toLowerCase())) {
            return address;
        }
        return address + ", " + wardName;
    }
}
