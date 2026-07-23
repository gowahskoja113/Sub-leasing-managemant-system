package com.sep490.slms2026.config;

import com.sep490.slms2026.entity.Admin;
import com.sep490.slms2026.entity.OperationManagement;
import com.sep490.slms2026.entity.Owner;
import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.entity.Zone;
import com.sep490.slms2026.enums.Role;
import com.sep490.slms2026.enums.UserStatus;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeder tối giản khi start: khu vực (zone) + vài tài khoản admin / owner / manager / user.
 * <p>Mật khẩu mặc định: {@code 123456}.</p>
 * <p>Tài khoản: {@code admin01..02}, {@code owner}, {@code manager01..02}, {@code user}.</p>
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private static final String DEFAULT_PASSWORD = "123456";
    private static final int ADMIN_COUNT = 2;
    private static final int MANAGER_COUNT = 2;

    private static final Map<String, String[]> CITY_DISTRICTS = new LinkedHashMap<>();

    static {
        CITY_DISTRICTS.put("Hà Nội", new String[]{"Cầu Giấy"});
        CITY_DISTRICTS.put("TP. Hồ Chí Minh", new String[]{
                "Phú Nhuận", "Quận 3", "Bình Thạnh", "Gò Vấp", "Quận 1"
        });
    }

    private final ZoneRepository zoneRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedZones();
        List<Zone> districts = loadDistrictZones();
        seedAccounts(districts);
        log.info("DataSeeder: xong. Zone + admin01..02 / owner / manager01..02 / user. MK: {}", DEFAULT_PASSWORD);
    }

    private void seedZones() {
        for (Map.Entry<String, String[]> entry : CITY_DISTRICTS.entrySet()) {
            Zone city = ensureRootZone(entry.getKey());
            for (String districtName : entry.getValue()) {
                ensureChildZone(districtName, city);
            }
        }
    }

    private Zone ensureRootZone(String name) {
        return zoneRepository.findCityLevelZoneByNameIgnoreCase(name)
                .orElseGet(() -> {
                    Zone zone = new Zone();
                    zone.setName(name);
                    zone.setLevel(1);
                    zone.setDescription("Tỉnh/Thành phố");
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
                    return zoneRepository.save(zone);
                });
    }

    private List<Zone> loadDistrictZones() {
        return zoneRepository.findAll().stream()
                .filter(z -> z.getLevel() != null && z.getLevel() == 2)
                .toList();
    }

    private void seedAccounts(List<Zone> districts) {
        seedAdmins();
        seedOwner();
        seedManagers(districts);
        seedUser();
    }

    private User newUser(String username, String phone, String fullName, Role role) {
        User u = new User();
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        u.setPhoneNumber(phone);
        u.setFullName(fullName);
        u.setEmail(username + "@slms.local");
        u.setRole(role);
        u.setStatus(UserStatus.ACTIVE);
        return u;
    }

    private void seedAdmins() {
        for (int i = 1; i <= ADMIN_COUNT; i++) {
            String username = String.format("admin%02d", i);
            String phone = String.format("0901%06d", i);
            if (userRepository.existsByUsername(username) || userRepository.existsByPhoneNumber(phone)) {
                continue;
            }
            User u = newUser(username, phone, "Quản Trị Viên " + String.format("%02d", i), Role.ROLE_ADMIN);
            Admin profile = new Admin();
            profile.setUser(u);
            profile.setStartAt(LocalDateTime.now());
            u.setAdminProfile(profile);
            userRepository.save(u);
        }
    }

    private void seedOwner() {
        if (userRepository.existsByUsername("owner") || userRepository.existsByPhoneNumber("0902000001")) {
            return;
        }
        User u = newUser("owner", "0902000001", "Chủ Nhà", Role.ROLE_OWNER);
        Owner profile = new Owner();
        profile.setUser(u);
        u.setOwnerProfile(profile);
        userRepository.save(u);
    }

    private void seedManagers(List<Zone> districts) {
        for (int i = 1; i <= MANAGER_COUNT; i++) {
            String username = String.format("manager%02d", i);
            String phone = String.format("0903%06d", i);
            if (userRepository.existsByUsername(username) || userRepository.existsByPhoneNumber(phone)) {
                continue;
            }
            User u = newUser(username, phone, "Quản Lý Vận Hành " + String.format("%02d", i), Role.ROLE_MANAGER);
            OperationManagement profile = new OperationManagement();
            profile.setUser(u);
            profile.setStartAt(LocalDateTime.now());
            if (!districts.isEmpty()) {
                List<Zone> assigned = new ArrayList<>();
                for (int z = 0; z < districts.size(); z++) {
                    if (z % MANAGER_COUNT == (i - 1)) {
                        assigned.add(districts.get(z));
                    }
                }
                if (assigned.isEmpty()) {
                    assigned.add(districts.get((i - 1) % districts.size()));
                }
                profile.setZones(assigned);
            }
            u.setOperationManagementProfile(profile);
            userRepository.save(u);
        }
    }

    private void seedUser() {
        if (userRepository.existsByUsername("user") || userRepository.existsByPhoneNumber("0905000001")) {
            return;
        }
        User u = newUser("user", "0905000001", "Người Dùng", Role.ROLE_USER);
        userRepository.save(u);
    }
}
