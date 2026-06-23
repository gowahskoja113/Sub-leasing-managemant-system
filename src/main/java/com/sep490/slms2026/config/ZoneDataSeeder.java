package com.sep490.slms2026.config;

import com.sep490.slms2026.entity.Zone;
import com.sep490.slms2026.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class ZoneDataSeeder implements ApplicationRunner {

    private final ZoneRepository zoneRepository;

    private static final Map<String, String[]> CITY_DISTRICTS = new LinkedHashMap<>();

    static {
        CITY_DISTRICTS.put("Hà Nội", new String[]{"Cầu Giấy"});
        CITY_DISTRICTS.put("TP. Hồ Chí Minh", new String[]{
                "Phú Nhuận", "Quận 3", "Bình Thạnh", "Gò Vấp", "Quận 1"
        });
    }

    @Override
    public void run(ApplicationArguments args) {
        seedZones();
    }

    private void seedZones() {
        for (Map.Entry<String, String[]> entry : CITY_DISTRICTS.entrySet()) {
            Zone city = ensureRootZone(entry.getKey());
            for (String districtName : entry.getValue()) {
                ensureChildZone(districtName, city);
            }
        }
        log.info("Zone master data seeded ({} cities)", CITY_DISTRICTS.size());
    }

    private Zone ensureRootZone(String name) {
        return zoneRepository.findCityLevelZoneByNameIgnoreCase(name)
                .orElseGet(() -> {
                    Zone zone = new Zone();
                    zone.setName(name);
                    zone.setLevel(1);
                    zone.setDescription("Tỉnh/Thành phố");
                    log.info("Created zone level 1: {}", name);
                    return zoneRepository.save(zone);
                });
    }

    private Zone ensureChildZone(String name, Zone parent) {
        return zoneRepository.findDistrictLevelZoneByNameIgnoreCaseAndParentId(name, parent.getId())
                .orElseGet(() -> {
                    Zone zone = new Zone();
                    zone.setName(name);
                    zone.setLevel(2);
                    zone.setParent(parent);
                    zone.setDescription("Quận/Huyện");
                    log.info("Created zone level 2: {} ({})", name, parent.getName());
                    return zoneRepository.save(zone);
                });
    }
}
