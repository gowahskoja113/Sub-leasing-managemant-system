package com.sep490.slms2026.config;

import com.sep490.slms2026.entity.*;
import com.sep490.slms2026.enums.Role;
import com.sep490.slms2026.enums.UserStatus;
import com.sep490.slms2026.repository.EquipmentCatalogRepository;
import com.sep490.slms2026.repository.RenovationCategoryRepository;
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
import java.util.stream.Collectors;

/**
 * Master data khi start: catalog thiết bị, hạng mục cải tạo, zone, và vài tài khoản mỗi role.
 * <p>Mật khẩu mặc định: {@code 123456}. Tài khoản: admin01..02, owner01..02, manager01..02,
 * tenant01..02, user01..02.</p>
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class MasterDataSeeder implements ApplicationRunner {

    private static final String DEFAULT_PASSWORD = "123456";
    private static final int ACCOUNTS_PER_ROLE = 2;

    private static final Map<String, String[]> CITY_DISTRICTS = new LinkedHashMap<>();

    static {
        CITY_DISTRICTS.put("Hà Nội", new String[]{"Cầu Giấy"});
        CITY_DISTRICTS.put("TP. Hồ Chí Minh", new String[]{
                "Phú Nhuận", "Quận 3", "Bình Thạnh", "Gò Vấp", "Quận 1"
        });
    }

    private final EquipmentCatalogRepository equipmentCatalogRepository;
    private final RenovationCategoryRepository renovationCategoryRepository;
    private final ZoneRepository zoneRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedEquipmentCatalog();
        seedRenovationCategories();
        seedZones();
        List<Zone> districts = loadDistrictZones();
        seedBaseAccounts(districts);
        log.info("MasterDataSeeder: hoàn tất (catalog + zone + {} tài khoản/role). MK: {}",
                ACCOUNTS_PER_ROLE, DEFAULT_PASSWORD);
    }

    // ------------------------------------------------------------------
    // EQUIPMENT / RENOVATION
    // ------------------------------------------------------------------
    private void seedEquipmentCatalog() {
        seedCatalog("Điều hòa", "Máy lạnh / điều hòa không khí");
        seedCatalog("Tủ lạnh", "Tủ lạnh các loại");
        seedCatalog("Máy giặt", "Máy giặt cửa trước / cửa trên");
        seedCatalog("Bàn ăn", "Bàn ăn và ghế");
        seedCatalog("Giường", "Giường ngủ các loại");
        seedCatalog("Tủ quần áo", "Tủ đựng quần áo");
        seedCatalog("Bếp từ", "Bếp từ / bếp gas");
        seedCatalog("Nóng lạnh", "Máy nước nóng");
        seedCatalog("Quạt", "Quạt điện / quạt trần");
        seedCatalog("Khác", "Thiết bị khác");
    }

    private void seedCatalog(String name, String description) {
        if (equipmentCatalogRepository.findAll().stream().noneMatch(c -> c.getName().equals(name))) {
            equipmentCatalogRepository.save(EquipmentCatalog.builder()
                    .name(name)
                    .description(description)
                    .active(true)
                    .build());
        }
    }

    private void seedRenovationCategories() {
        seedCategory("PAINTING", "Sơn sửa", "Sơn tường, trần nhà");
        seedCategory("PLUMBING", "Điện nước", "Sửa chữa hệ thống điện nước");
        seedCategory("FLOORING", "Sàn nhà", "Lát sàn, sửa sàn");
        seedCategory("FURNITURE", "Nội thất", "Mua sắm nội thất mới");
        seedCategory("EQUIPMENT", "Thiết bị mua thêm", "Mua thêm thiết bị trong đợt cải tạo");
        seedCategory("STRUCTURAL", "Kết cấu", "Thay đổi kết cấu, vách ngăn");
        seedCategory("OTHER", "Khác", "Hạng mục cải tạo khác");
    }

    private void seedCategory(String code, String name, String description) {
        if (renovationCategoryRepository.findByCode(code).isEmpty()) {
            renovationCategoryRepository.save(RenovationCategory.builder()
                    .code(code)
                    .name(name)
                    .description(description)
                    .active(true)
                    .build());
        }
    }

    // ------------------------------------------------------------------
    // ZONE
    // ------------------------------------------------------------------
    private void seedZones() {
        for (Map.Entry<String, String[]> entry : CITY_DISTRICTS.entrySet()) {
            Zone city = ensureRootZone(entry.getKey());
            for (String districtName : entry.getValue()) {
                ensureChildZone(districtName, city);
            }
        }
        log.info("MasterDataSeeder: zone seeded ({} cities)", CITY_DISTRICTS.size());
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

    private List<Zone> loadDistrictZones() {
        return zoneRepository.findAll().stream()
                .filter(z -> z.getLevel() != null && z.getLevel() == 2)
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------------------
    // BASE ACCOUNTS (vài tài khoản mỗi role)
    // ------------------------------------------------------------------
    private void seedBaseAccounts(List<Zone> districts) {
        seedAdmins(ACCOUNTS_PER_ROLE);
        seedOwners(ACCOUNTS_PER_ROLE);
        seedManagers(ACCOUNTS_PER_ROLE, districts);
        seedTenants(ACCOUNTS_PER_ROLE);
        seedPlainUsers(ACCOUNTS_PER_ROLE);
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

    private void seedAdmins(int n) {
        for (int i = 1; i <= n; i++) {
            String username = String.format("admin%02d", i);
            String phone = String.format("0901%06d", i);
            if (userRepository.existsByUsername(username) || userRepository.existsByPhoneNumber(phone)) continue;
            User u = newUser(username, phone, "Quản Trị Viên " + String.format("%02d", i), Role.ROLE_ADMIN);
            Admin profile = new Admin();
            profile.setUser(u);
            profile.setStartAt(LocalDateTime.now());
            u.setAdminProfile(profile);
            saveQuietly(u, username);
        }
    }

    private void seedOwners(int n) {
        for (int i = 1; i <= n; i++) {
            String username = String.format("owner%02d", i);
            String phone = String.format("0902%06d", i);
            if (userRepository.existsByUsername(username) || userRepository.existsByPhoneNumber(phone)) continue;
            User u = newUser(username, phone, "Chủ Nhà " + String.format("%02d", i), Role.ROLE_OWNER);
            Owner profile = new Owner();
            profile.setUser(u);
            u.setOwnerProfile(profile);
            saveQuietly(u, username);
        }
    }

    private void seedManagers(int n, List<Zone> districts) {
        for (int i = 1; i <= n; i++) {
            String username = String.format("manager%02d", i);
            String phone = String.format("0903%06d", i);
            if (userRepository.existsByUsername(username) || userRepository.existsByPhoneNumber(phone)) continue;
            User u = newUser(username, phone, "Quản Lý Vận Hành " + String.format("%02d", i), Role.ROLE_MANAGER);
            OperationManagement profile = new OperationManagement();
            profile.setUser(u);
            profile.setStartAt(LocalDateTime.now());
            if (!districts.isEmpty()) {
                List<Zone> assigned = new ArrayList<>();
                for (int z = 0; z < districts.size(); z++) {
                    if (z % n == (i - 1)) assigned.add(districts.get(z));
                }
                if (assigned.isEmpty()) assigned.add(districts.get((i - 1) % districts.size()));
                profile.setZones(assigned);
            }
            u.setOperationManagementProfile(profile);
            saveQuietly(u, username);
        }
    }

    private void seedTenants(int n) {
        for (int i = 1; i <= n; i++) {
            String username = String.format("tenant%02d", i);
            String phone = String.format("0904%06d", i);
            if (userRepository.existsByUsername(username) || userRepository.existsByPhoneNumber(phone)) continue;
            User u = newUser(username, phone, "Khách Thuê " + String.format("%02d", i), Role.ROLE_TENANT);
            Tenant profile = new Tenant();
            profile.setUser(u);
            profile.setCccd(String.format("079%09d", i));
            u.setTenantProfile(profile);
            saveQuietly(u, username);
        }
    }

    private void seedPlainUsers(int n) {
        for (int i = 1; i <= n; i++) {
            String username = String.format("user%02d", i);
            String phone = String.format("0905%06d", i);
            if (userRepository.existsByUsername(username) || userRepository.existsByPhoneNumber(phone)) continue;
            User u = newUser(username, phone, "Người Dùng " + String.format("%02d", i), Role.ROLE_USER);
            saveQuietly(u, username);
        }
    }

    private void saveQuietly(User u, String username) {
        try {
            userRepository.save(u);
        } catch (Exception e) {
            log.warn("MasterDataSeeder: bỏ qua user {} ({}).", username, e.getMessage());
        }
    }
}
