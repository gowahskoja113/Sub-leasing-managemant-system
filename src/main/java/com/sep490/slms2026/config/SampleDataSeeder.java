package com.sep490.slms2026.config;

import com.sep490.slms2026.entity.*;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SEEDER TÀI KHOẢN DEMO (không seed BĐS / HĐ thuê / hóa đơn).
 *
 * <p>BĐS và HĐ nháp lấy từ file Excel import:
 * {@code docs/SLMS2026_import_matrix_dot1.xlsx},
 * {@code docs/SLMS2026_import_matrix_dot2.xlsx},
 * {@code docs/SLMS2026_import_tenant_draft_contracts.xlsx}.</p>
 *
 * <p>Mật khẩu mặc định: <b>123456</b>.</p>
 * <p>Tài khoản: admin01–02, owner01–06, manager01–05;
 * tenant login bằng <b>SĐT</b> ({@code 0904000001}…{@code 0904000036}), không còn {@code tenant01}…</p>
 * <p>Tenant chỉ có tài khoản — chưa gán hợp đồng / chưa thuê nhà.</p>
 *
 * <p>Chạy sau {@link MasterDataSeeder} (@Order 1) và {@link ZoneDataSeeder} (@Order 2).
 * Idempotent: có {@code manager01} thì bỏ qua full seed; thiếu tenant cuối (SĐT) thì bổ sung tenant.</p>
 */
@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
public class SampleDataSeeder implements ApplicationRunner {

    private static final String DEFAULT_PASSWORD = "123456";
    /** 25–40 tenant sẵn tài khoản, chưa thuê. Cần ≥36 cho file draft (25 gốc + 11 MTX#40..#50). */
    private static final int TENANT_COUNT = 36;

    private final UserRepository userRepository;
    private final ZoneRepository zoneRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (!userRepository.existsByUsername("manager01")) {
            try {
                seedAccountsOnly();
            } catch (Exception e) {
                log.warn("SampleDataSeeder seedAccountsOnly thất bại (bỏ qua, không chặn start): {}", e.getMessage());
            }
            return;
        }

        // DB cũ: bảo đảm đủ tenant (username = SĐT 0904xxxxxx), không seed BĐS/HĐ
        if (!userRepository.existsByUsername(tenantPhone(TENANT_COUNT))) {
            try {
                List<Zone> districts = loadDistrictZones();
                seedManagers(5, districts);
                int created = seedTenants(TENANT_COUNT).size();
                log.info("SampleDataSeeder: bổ sung tenant (đến {}), tạo/đọc được {} user. Không seed BĐS.",
                        tenantPhone(TENANT_COUNT), created);
            } catch (Exception e) {
                log.warn("SampleDataSeeder expand tenants thất bại (bỏ qua): {}", e.getMessage());
            }
        } else {
            log.info("SampleDataSeeder: tài khoản mẫu đã tồn tại, bỏ qua.");
        }
    }

    private void seedAccountsOnly() {
        List<Zone> districts = loadDistrictZones();
        if (districts.isEmpty()) {
            log.warn("SampleDataSeeder: chưa có Zone cấp quận — ZoneDataSeeder cần chạy trước (manager sẽ không gán zone).");
        }

        seedAdmins(2);
        seedOwners(6);
        seedManagers(5, districts);
        seedTenants(TENANT_COUNT);

        log.info("SampleDataSeeder: HOÀN TẤT seed tài khoản (admin/owner/manager + {} tenant chưa thuê). "
                        + "Mật khẩu: {}. BĐS/HĐ lấy từ Excel import.",
                TENANT_COUNT, DEFAULT_PASSWORD);
    }

    private List<Zone> loadDistrictZones() {
        return zoneRepository.findAll().stream()
                .filter(z -> z.getLevel() != null && z.getLevel() == 2)
                .collect(Collectors.toList());
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
            User u = newUser(username, phone, "Chủ Nhà " + fullNameByIndex(i - 1), Role.ROLE_OWNER);
            Owner profile = new Owner();
            profile.setUser(u);
            u.setOwnerProfile(profile);
            saveQuietly(u, username);
        }
    }

    private List<User> seedManagers(int n, List<Zone> districts) {
        List<User> managers = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            String username = String.format("manager%02d", i);
            String phone = String.format("0903%06d", i);
            if (userRepository.existsByUsername(username) || userRepository.existsByPhoneNumber(phone)) {
                userRepository.findByUsername(username).ifPresent(managers::add);
                continue;
            }
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
            User saved = saveQuietly(u, username);
            if (saved != null) managers.add(saved);
        }
        return managers;
    }

    private List<User> seedTenants(int n) {
        List<User> tenants = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            // Login tenant = số điện thoại (0904000001 …)
            String phone = tenantPhone(i);
            String username = phone;
            if (userRepository.existsByUsername(username) || userRepository.existsByPhoneNumber(phone)) {
                userRepository.findByUsername(username)
                        .or(() -> userRepository.findByPhoneNumber(phone))
                        .ifPresent(tenants::add);
                continue;
            }
            User u = newUser(username, phone, fullNameByIndex(i + 9), Role.ROLE_TENANT);
            Tenant profile = new Tenant();
            profile.setUser(u);
            profile.setCccd(String.format("079%09d", i));
            u.setTenantProfile(profile);
            User saved = saveQuietly(u, username);
            if (saved != null) tenants.add(saved);
        }
        return tenants;
    }

    /** SĐT demo tenant i (1-based): 0904000001, 0904000002, … — đồng thời là username. */
    private static String tenantPhone(int index) {
        return String.format("0904%06d", index);
    }

    private User saveQuietly(User u, String username) {
        try {
            return userRepository.save(u);
        } catch (Exception e) {
            log.warn("SampleDataSeeder: bỏ qua user {} ({}).", username, e.getMessage());
            return null;
        }
    }

    private static final String[] HO = {"Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Vũ", "Đặng", "Bùi", "Đỗ", "Hồ"};
    private static final String[] DEM = {"Văn", "Thị", "Hữu", "Đức", "Minh", "Ngọc", "Gia", "Quang", "Thanh", "Khánh"};
    private static final String[] TEN = {"An", "Bình", "Cường", "Dũng", "Phúc", "Giang", "Hà", "Hiếu", "Khoa", "Long",
            "Mai", "Nam", "Oanh", "Phương", "Quân", "Quỳnh", "Sơn", "Tâm", "Uyên", "Vy",
            "Xuân", "Yến", "Bảo", "Châu", "Duy", "Hải", "Khang", "Linh", "Trang", "Tú"};

    private String fullNameByIndex(int idx) {
        String ho = HO[idx % HO.length];
        String dem = DEM[(idx / TEN.length) % DEM.length];
        String ten = TEN[idx % TEN.length];
        int round = idx / (HO.length * TEN.length);
        return round == 0 ? ho + " " + dem + " " + ten : ho + " " + dem + " " + ten + " " + round;
    }
}
