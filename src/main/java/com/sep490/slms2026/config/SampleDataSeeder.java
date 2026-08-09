package com.sep490.slms2026.config;

import com.sep490.slms2026.entity.*;
import com.sep490.slms2026.enums.*;
import com.sep490.slms2026.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SEEDER DỮ LIỆU MẪU CHO DEMO / THUYẾT TRÌNH.
 *
 * <p>Mục tiêu: chỉ cần có mặt file này trong source, khi project khởi chạy sẽ TỰ ĐỘNG nạp
 * đầy đủ dữ liệu cho một hệ thống hoàn chỉnh. Khi không cần nữa, chỉ cần XÓA hoặc DI CHUYỂN
 * file này ra khỏi thư mục source rồi build lại — dữ liệu cũ vẫn còn trong DB nhưng sẽ không
 * sinh thêm. Muốn xóa sạch dữ liệu thì drop/tạo lại database.</p>
 *
 * <p>Nội dung seed:</p>
 * <ul>
 *   <li><b>40 người dùng</b> đủ 5 role: 2 ADMIN, 6 OWNER, 5 MANAGER, 22 TENANT, 5 USER.</li>
 *   <li><b>55 bất động sản</b> (25 nhà nguyên căn + 30 nhà chia phòng) kèm phòng,
 *       trang thiết bị nội thất và bảng kê (manifest) thiết bị.</li>
 *   <li>Zone TP.HCM + các quận, gán quyền quản lý cho các MANAGER.</li>
 * </ul>
 *
 * <p>Mật khẩu mặc định cho TẤT CẢ tài khoản: <b>123456</b>.</p>
 * <p>Tài khoản đăng nhập: admin01..admin02, owner01..owner06, manager01..manager05,
 * tenant01..tenant22, user01..user05.</p>
 *
 * <p>Chạy sau {@link MasterDataSeeder} (@Order 1) và {@link ZoneDataSeeder} (@Order 2)
 * để chắc chắn EquipmentCatalog và Zone đã có sẵn. Toàn bộ idempotent: chạy lại nhiều lần
 * không tạo trùng.</p>
 */
@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
public class SampleDataSeeder implements ApplicationRunner {

    private static final String DEFAULT_PASSWORD = "123456";

    // Lưu ý: Admin/Owner/Tenant/OperationManagement profile được lưu tự động qua cascade ALL
    // từ entity User, nên không cần inject repository riêng cho từng profile.
    private final UserRepository userRepository;
    private final ZoneRepository zoneRepository;
    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final EquipmentCatalogRepository equipmentCatalogRepository;
    private final EquipmentRepository equipmentRepository;
    private final EquipmentManifestRepository equipmentManifestRepository;
    private final TenantContractRepository tenantContractRepository;
    private final TenantInvoiceRepository tenantInvoiceRepository;
    private final TenantPaymentRepository tenantPaymentRepository;
    private final PasswordEncoder passwordEncoder;

    private Map<String, EquipmentCatalog> catalogByName;

    @Override
    public void run(ApplicationArguments args) {
        catalogByName = equipmentCatalogRepository.findAll().stream()
                .collect(Collectors.toMap(EquipmentCatalog::getName, c -> c, (a, b) -> a));

        // Idempotent full seed: chỉ chạy khi chưa có manager01
        if (!userRepository.existsByUsername("manager01")) {
            try {
                seedFreshDemo();
            } catch (Exception e) {
                log.warn("SampleDataSeeder seedFreshDemo thất bại (bỏ qua, không chặn start): {}", e.getMessage());
            }
            return;
        }

        // DB đã seed cũ (tenant01..22): bổ sung tenant23..50 + HĐ đa trạng thái nếu chưa có
        if (userRepository.existsByUsername("tenant22") && !userRepository.existsByUsername("tenant50")) {
            try {
                expandDemoContracts();
            } catch (Exception e) {
                // Production có HĐ thật (trùng contract_code) không được chặn API boot
                log.warn("SampleDataSeeder expand thất bại (bỏ qua, không chặn start): {}", e.getMessage());
            }
        } else {
            log.info("SampleDataSeeder: dữ liệu mẫu đã tồn tại, bỏ qua.");
        }
    }

    private void seedFreshDemo() {
        List<Zone> districts = loadDistrictZones();
        if (districts.isEmpty()) {
            log.warn("SampleDataSeeder: chưa có Zone cấp quận — ZoneDataSeeder cần chạy trước. Bỏ qua seed BĐS.");
        }

        seedAdmins(2);
        seedOwners(6);
        List<User> managers = seedManagers(5, districts);
        List<User> tenants = seedTenants(50);
        seedPlainUsers(5);

        if (!managers.isEmpty() && !districts.isEmpty()) {
            seedProperties(managers, districts);
            seedContractsAndInvoicesDiverse(tenants, managers);
        }

        log.info("SampleDataSeeder: HOÀN TẤT seed dữ liệu mẫu (50 tenant, HĐ đa trạng thái + hóa đơn). "
                + "Mật khẩu mọi tài khoản: {}", DEFAULT_PASSWORD);
    }

    /**
     * Mở rộng demo trên DB đã seed: thêm tenant23..50 và ~28 HĐ nháp/pending (không đụng HĐ ACTIVE cũ).
     */
    private void expandDemoContracts() {
        List<Zone> districts = loadDistrictZones();
        List<User> managers = seedManagers(5, districts);
        List<User> newTenants = seedTenants(50); // tenant01..22 đã có, tạo 23..50
        List<User> extra = newTenants.stream()
                .filter(u -> u.getUsername() != null && u.getUsername().compareTo("tenant22") > 0)
                .collect(Collectors.toList());
        if (extra.isEmpty() || managers.isEmpty()) {
            log.info("SampleDataSeeder expand: không có tenant mới / manager — bỏ qua.");
            return;
        }
        seedExtraNonActiveContracts(extra, managers);
        log.info("SampleDataSeeder: đã mở rộng demo (tenant03..50 + HĐ DRAFT/PENDING).");
    }

    // ------------------------------------------------------------------
    // ZONE
    // ------------------------------------------------------------------
    private List<Zone> loadDistrictZones() {
        return zoneRepository.findAll().stream()
                .filter(z -> z.getLevel() != null && z.getLevel() == 2)
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------------------
    // USERS
    // ------------------------------------------------------------------
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
            // Gán cho mỗi manager một nhóm quận (round-robin) để demo phân vùng quản lý.
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
            String username = String.format("tenant%02d", i);
            String phone = String.format("0904%06d", i);
            if (userRepository.existsByUsername(username) || userRepository.existsByPhoneNumber(phone)) {
                userRepository.findByUsername(username).ifPresent(tenants::add);
                continue;
            }
            // dịch chỉ số tên để không trùng họ tên với owner
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

    private void seedPlainUsers(int n) {
        for (int i = 1; i <= n; i++) {
            String username = String.format("user%02d", i);
            String phone = String.format("0905%06d", i);
            if (userRepository.existsByUsername(username) || userRepository.existsByPhoneNumber(phone)) continue;
            User u = newUser(username, phone, "Người Dùng " + fullNameByIndex(i + 31), Role.ROLE_USER);
            saveQuietly(u, username);
        }
    }

    private User saveQuietly(User u, String username) {
        try {
            return userRepository.save(u);
        } catch (Exception e) {
            log.warn("SampleDataSeeder: bỏ qua user {} ({}).", username, e.getMessage());
            return null;
        }
    }

    /** Sinh họ tên tiếng Việt theo chỉ số, đảm bảo phân tán đủ để không trùng trong phạm vi seed. */
    private static final String[] HO = {"Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Vũ", "Đặng", "Bùi", "Đỗ", "Hồ"};
    private static final String[] DEM = {"Văn", "Thị", "Hữu", "Đức", "Minh", "Ngọc", "Gia", "Quang", "Thanh", "Khánh"};
    private static final String[] TEN = {"An", "Bình", "Cường", "Dũng", "Phúc", "Giang", "Hà", "Hiếu", "Khoa", "Long",
            "Mai", "Nam", "Oanh", "Phương", "Quân", "Quỳnh", "Sơn", "Tâm", "Uyên", "Vy",
            "Xuân", "Yến", "Bảo", "Châu", "Duy", "Hải", "Khang", "Linh", "Trang", "Tú"};

    private String fullNameByIndex(int idx) {
        String ho = HO[idx % HO.length];
        String dem = DEM[(idx / TEN.length) % DEM.length];
        String ten = TEN[idx % TEN.length];
        // thêm hậu tố số nếu vẫn có nguy cơ trùng ở các vòng lớn
        int round = idx / (HO.length * TEN.length);
        return round == 0 ? ho + " " + dem + " " + ten : ho + " " + dem + " " + ten + " " + round;
    }

    // ------------------------------------------------------------------
    // PROPERTIES (25 nhà nguyên căn + 30 nhà chia phòng = 55)
    // ------------------------------------------------------------------
    private static final List<String> IMGS_WH = List.of(
            "https://images.unsplash.com/photo-1568605114967-8130f3a36994",
            "https://images.unsplash.com/photo-1570129477492-45c003edd2be",
            "https://images.unsplash.com/photo-1512917774080-9991f1c4c750");
    private static final List<String> IMGS_RB = List.of(
            "https://images.unsplash.com/photo-1493809842364-78817add7ffb",
            "https://images.unsplash.com/photo-1505691938895-1758d7feb511");

    private static final String[] STREETS = {
            "Lê Lợi", "Nguyễn Huệ", "Hai Bà Trưng", "Trần Hưng Đạo", "Cách Mạng Tháng 8",
            "Nguyễn Đình Chiểu", "Pasteur", "Lý Tự Trọng", "Võ Văn Tần", "Điện Biên Phủ",
            "Nguyễn Thị Minh Khai", "Phan Xích Long", "Quang Trung", "Cộng Hòa", "Trường Chinh",
            "Lạc Long Quân", "Nguyễn Oanh", "Lê Văn Sỹ", "Hoàng Văn Thụ", "Phạm Văn Đồng",
            "Nguyễn Văn Trỗi", "Xô Viết Nghệ Tĩnh", "Phan Đăng Lưu", "Nơ Trang Long", "Bạch Đằng",
            "Đinh Tiên Hoàng", "Nguyễn Trãi", "Trần Quang Khải", "Hoàng Sa", "Trường Sa"};

    private void seedProperties(List<User> managers, List<Zone> districts) {
        Set<String> existingNames = propertyRepository.findAll().stream()
                .map(Property::getPropertyName).collect(Collectors.toSet());

        int idx = 0;
        // 25 nhà nguyên căn
        for (int i = 0; i < 25; i++, idx++) {
            UUID managerId = managers.get(idx % managers.size()).getId();
            Zone zone = districts.get(idx % districts.size());
            String street = STREETS[i % STREETS.length];
            String name = String.format("Nhà nguyên căn %s %02d", street, i + 1);
            int beds = 2 + (i % 4);
            long price = 16_000_000L + (i % 6) * 4_000_000L;
            createWholeHouse(existingNames, managerId, zone, name,
                    (10 + i) + " " + street,
                    "Nhà nguyên căn cho thuê, nội thất đầy đủ, vị trí thuận tiện, an ninh tốt.",
                    70.0 + i * 5, 2 + (i % 3), beds, BigDecimal.valueOf(price), IMGS_WH,
                    beds + " phòng ngủ, 1 phòng khách, 1 bếp, " + Math.max(1, beds - 1) + " WC");
        }
        // 30 nhà chia phòng
        for (int i = 0; i < 30; i++, idx++) {
            UUID managerId = managers.get(idx % managers.size()).getId();
            Zone zone = districts.get(idx % districts.size());
            String street = STREETS[i % STREETS.length];
            String name = String.format("Nhà chia phòng %s %02d", street, i + 1);
            int rooms = 4 + (i % 8);
            long price = 2_500_000L + (i % 6) * 700_000L;
            createRoomBased(existingNames, managerId, zone, name,
                    (100 + i) + " " + street,
                    "Nhà chia phòng cho thuê, phòng khép kín đầy đủ tiện nghi, có chỗ để xe.",
                    120.0 + i * 10, 3 + (i % 3), rooms, BigDecimal.valueOf(price), IMGS_RB);
        }
        log.info("SampleDataSeeder: đã tạo {} bất động sản.", idx);
    }

    private void createWholeHouse(Set<String> existing, UUID managerId, Zone zone, String name,
                                  String shortAddress, String descriptions, double area, int floors,
                                  int bedrooms, BigDecimal price, List<String> images, String structure) {
        if (zone == null || existing.contains(name)) return;
        Property property = baseProperty(managerId, zone, name, shortAddress, descriptions,
                area, floors, bedrooms, price, images).wholeHouse(true).build();
        property = propertyRepository.save(property);

        Room room = Room.builder()
                .property(property)
                .roomNumber("Toàn nhà")
                .price(price)
                .deposit(price)
                .area(area)
                .maxOccupants(bedrooms * 2)
                .structureDescription(structure)
                .imageUrls(String.join(",", images))
                .propertyType(PropertyType.WHOLE_HOUSE)
                .status(RoomStatus.AVAILABLE)
                .build();
        room = roomRepository.save(room);

        addEquipment(property, room, "Điều hòa", HouseArea.LIVING_ROOM, EquipmentSource.INITIAL_HANDOVER, EquipmentStatus.NEW, "Daikin Inverter 2.0HP - phòng khách");
        addEquipment(property, room, "Điều hòa", HouseArea.BEDROOM, EquipmentSource.INITIAL_HANDOVER, EquipmentStatus.GOOD, "Panasonic 1.5HP - phòng ngủ chính");
        addEquipment(property, room, "Tủ lạnh", HouseArea.KITCHEN, EquipmentSource.INITIAL_HANDOVER, EquipmentStatus.GOOD, "Toshiba 250L");
        addEquipment(property, room, "Bếp từ", HouseArea.KITCHEN, EquipmentSource.INITIAL_HANDOVER, EquipmentStatus.NEW, "Bếp từ đôi");
        addEquipment(property, room, "Bàn ăn", HouseArea.KITCHEN, EquipmentSource.INITIAL_HANDOVER, EquipmentStatus.GOOD, "Bàn ăn gỗ 6 ghế");
        addEquipment(property, room, "Giường", HouseArea.BEDROOM, EquipmentSource.INITIAL_HANDOVER, EquipmentStatus.GOOD, "Giường gỗ 1m8");
        addEquipment(property, room, "Tủ quần áo", HouseArea.BEDROOM, EquipmentSource.INITIAL_HANDOVER, EquipmentStatus.GOOD, "Tủ áo 4 cánh");
        addEquipment(property, room, "Máy giặt", HouseArea.BALCONY, EquipmentSource.PURCHASED, EquipmentStatus.NEW, "LG cửa trước 9kg");
        addEquipment(property, room, "Nóng lạnh", HouseArea.BATHROOM, EquipmentSource.INITIAL_HANDOVER, EquipmentStatus.NEW, "Ariston 30L");

        addManifest(property, "Điều hòa", bedrooms + 1, EquipmentStatus.GOOD);
        addManifest(property, "Tủ lạnh", 1, EquipmentStatus.GOOD);
        addManifest(property, "Bếp từ", 1, EquipmentStatus.NEW);
        addManifest(property, "Giường", bedrooms, EquipmentStatus.GOOD);
        addManifest(property, "Tủ quần áo", bedrooms, EquipmentStatus.GOOD);
        addManifest(property, "Máy giặt", 1, EquipmentStatus.NEW);
        addManifest(property, "Nóng lạnh", 2, EquipmentStatus.NEW);

        existing.add(name);
    }

    private void createRoomBased(Set<String> existing, UUID managerId, Zone zone, String name,
                                 String shortAddress, String descriptions, double area, int floors,
                                 int roomCount, BigDecimal roomPrice, List<String> images) {
        if (zone == null || existing.contains(name)) return;
        Property property = baseProperty(managerId, zone, name, shortAddress, descriptions,
                area, floors, roomCount, roomPrice, images).wholeHouse(false).build();
        property = propertyRepository.save(property);

        BigDecimal deposit = roomPrice; // cọc 1 tháng
        for (int i = 1; i <= roomCount; i++) {
            Room room = Room.builder()
                    .property(property)
                    .roomNumber(String.format("P%02d", i))
                    .price(roomPrice)
                    .deposit(deposit)
                    .area(area / roomCount)
                    .maxOccupants(2)
                    .structureDescription("Phòng khép kín, có gác lửng, ban công")
                    .imageUrls(String.join(",", images))
                    .propertyType(PropertyType.INDIVIDUAL_ROOM)
                    .status(RoomStatus.AVAILABLE)
                    .electricMeterCode("DH-" + property.getId() + "-" + i)
                    .waterMeterCode("NUOC-" + property.getId() + "-" + i)
                    .build();
            room = roomRepository.save(room);

            addEquipment(property, room, "Điều hòa", HouseArea.BEDROOM, EquipmentSource.INITIAL_HANDOVER, EquipmentStatus.NEW, "Casper 1.5HP");
            addEquipment(property, room, "Giường", HouseArea.BEDROOM, EquipmentSource.INITIAL_HANDOVER, EquipmentStatus.GOOD, "Giường gỗ 1m6");
            addEquipment(property, room, "Tủ quần áo", HouseArea.BEDROOM, EquipmentSource.INITIAL_HANDOVER, EquipmentStatus.GOOD, "Tủ áo 2 cánh");
            addEquipment(property, room, "Nóng lạnh", HouseArea.BATHROOM, EquipmentSource.INITIAL_HANDOVER, EquipmentStatus.GOOD, "Ariston 15L");
            addEquipment(property, room, "Quạt", HouseArea.BEDROOM, EquipmentSource.INITIAL_HANDOVER, EquipmentStatus.GOOD, "Quạt treo tường");
        }

        addEquipment(property, null, "Máy giặt", HouseArea.OTHER, EquipmentSource.PURCHASED, EquipmentStatus.GOOD, "Máy giặt chung 10kg");

        addManifest(property, "Điều hòa", roomCount, EquipmentStatus.NEW);
        addManifest(property, "Giường", roomCount, EquipmentStatus.GOOD);
        addManifest(property, "Tủ quần áo", roomCount, EquipmentStatus.GOOD);
        addManifest(property, "Nóng lạnh", roomCount, EquipmentStatus.GOOD);
        addManifest(property, "Quạt", roomCount, EquipmentStatus.GOOD);
        addManifest(property, "Máy giặt", 1, EquipmentStatus.GOOD);

        existing.add(name);
    }

    private Property.PropertyBuilder baseProperty(UUID managerId, Zone zone, String name,
                                                  String shortAddress, String descriptions, double area,
                                                  int floors, int totalRooms, BigDecimal price,
                                                  List<String> images) {
        String fullAddress = shortAddress + ", " + zone.getName()
                + (zone.getParent() != null ? ", " + zone.getParent().getName() : "");
        return Property.builder()
                .propertyName(name)
                .address(fullAddress)
                .zone(zone)
                .areaSize(area)
                .totalFloor(floors)
                .hasRenovation(true)
                .totalRooms(totalRooms)
                .imageUrls(new ArrayList<>(images))
                .status(PropertyStatus.ACTIVE)
                .managerAcceptedAt(LocalDateTime.now().minusMonths(1))
                .operationManagerId(managerId)
                .managedBy(managerId)
                .descriptions(descriptions)
                .price(price)
                .electricityUnitPrice(new BigDecimal("3500"))
                .waterUnitPrice(new BigDecimal("15000"))
                .depositMonths(1)
                .serviceFee(new BigDecimal("100000"))
                .renovationStartDate(LocalDate.now().minusMonths(2))
                .renovationEndDate(LocalDate.now().minusMonths(1))
                .renovationCompleted(true);
    }

    private void addEquipment(Property property, Room room, String catalogName, HouseArea area,
                              EquipmentSource source, EquipmentStatus status, String note) {
        EquipmentCatalog catalog = catalogByName.get(catalogName);
        if (catalog == null) return;
        equipmentRepository.save(Equipment.builder()
                .property(property)
                .room(room)
                .catalog(catalog)
                .houseArea(area)
                .source(source)
                .status(status)
                .note(note)
                .build());
    }

    private void addManifest(Property property, String catalogName, int quantity, EquipmentStatus status) {
        EquipmentCatalog catalog = catalogByName.get(catalogName);
        if (catalog == null) return;
        equipmentManifestRepository.save(EquipmentManifest.builder()
                .property(property)
                .catalog(catalog)
                .quantity(quantity)
                .status(status)
                .source(EquipmentSource.INITIAL_HANDOVER)
                .price(BigDecimal.ZERO)
                .build());
    }

    // ------------------------------------------------------------------
    // HỢP ĐỒNG THUÊ + HÓA ĐƠN
    // Phân bổ trạng thái demo mentor 07/08:
    //   DRAFT 15 | PENDING (chưa cọc) 8 | PENDING_PRICE_APPROVAL 5 | ACTIVE 20 | TERMINATED 2
    // ------------------------------------------------------------------
    private int contractSeq = 0;

    /** Tiếp số HD-MT-yyyy-xxxxx từ max trong DB — tránh trùng unique contract_code trên server. */
    private void syncContractSeqFromDb() {
        int year = LocalDate.now().getYear();
        String prefix = "HD-MT-" + year + "-";
        int max = 0;
        for (TenantContract c : tenantContractRepository.findAll()) {
            String code = c.getContractCode();
            if (code == null || !code.startsWith(prefix)) continue;
            try {
                max = Math.max(max, Integer.parseInt(code.substring(prefix.length())));
            } catch (NumberFormatException ignored) {
                // skip non-numeric suffix
            }
        }
        contractSeq = max;
    }

    private void seedContractsAndInvoicesDiverse(List<User> tenants, List<User> managers) {
        if (tenants.isEmpty()) return;
        syncContractSeqFromDb();

        List<Room> availableUnits = collectAvailableUnits();
        int totalNeeded = Math.min(50, Math.min(tenants.size(), availableUnits.size()));
        int created = 0;

        // target counts
        int nDraft = 15, nPending = 8, nPrice = 5, nActive = 20, nTerm = 2;
        int i = 0;

        for (int k = 0; k < nDraft && i < totalNeeded; k++, i++) {
            createContractVariant(tenants.get(i), availableUnits.get(i), managers,
                    ContractStatus.DRAFT, null, PaymentStatus.PENDING, false);
            created++;
        }
        for (int k = 0; k < nPending && i < totalNeeded; k++, i++) {
            createContractVariant(tenants.get(i), availableUnits.get(i), managers,
                    ContractStatus.PENDING, null, PaymentStatus.PENDING, false);
            created++;
        }
        for (int k = 0; k < nPrice && i < totalNeeded; k++, i++) {
            createContractVariant(tenants.get(i), availableUnits.get(i), managers,
                    ContractStatus.PENDING, PriceApprovalStatus.PENDING_PRICE_APPROVAL,
                    PaymentStatus.PENDING, false);
            created++;
        }
        for (int k = 0; k < nActive && i < totalNeeded; k++, i++) {
            createContractVariant(tenants.get(i), availableUnits.get(i), managers,
                    ContractStatus.ACTIVE, null, PaymentStatus.PAID, true);
            created++;
        }
        for (int k = 0; k < nTerm && i < totalNeeded; k++, i++) {
            createContractVariant(tenants.get(i), availableUnits.get(i), managers,
                    ContractStatus.TERMINATED, null, PaymentStatus.PAID, false);
            created++;
        }
        log.info("SampleDataSeeder: đã tạo {} hợp đồng thuê (đa trạng thái) + hóa đơn ACTIVE.", created);
    }

    private void seedExtraNonActiveContracts(List<User> tenants, List<User> managers) {
        syncContractSeqFromDb();
        List<Room> availableUnits = collectAvailableUnits();
        int n = Math.min(tenants.size(), Math.min(availableUnits.size(), 28));
        // 15 draft, 8 pending, 5 price among extra
        int i = 0;
        for (int k = 0; k < 15 && i < n; k++, i++) {
            createContractVariant(tenants.get(i), availableUnits.get(i), managers,
                    ContractStatus.DRAFT, null, PaymentStatus.PENDING, false);
        }
        for (int k = 0; k < 8 && i < n; k++, i++) {
            createContractVariant(tenants.get(i), availableUnits.get(i), managers,
                    ContractStatus.PENDING, null, PaymentStatus.PENDING, false);
        }
        for (int k = 0; k < 5 && i < n; k++, i++) {
            createContractVariant(tenants.get(i), availableUnits.get(i), managers,
                    ContractStatus.PENDING, PriceApprovalStatus.PENDING_PRICE_APPROVAL,
                    PaymentStatus.PENDING, false);
        }
        log.info("SampleDataSeeder expand: đã tạo thêm HĐ DRAFT/PENDING/PRICE.");
    }

    private List<Room> collectAvailableUnits() {
        List<Room> availableUnits = new ArrayList<>();
        for (Property p : propertyRepository.findAll()) {
            List<Room> rooms = roomRepository.findByPropertyId(p.getId()).stream()
                    .filter(r -> r.getStatus() == RoomStatus.AVAILABLE)
                    .collect(Collectors.toList());
            availableUnits.addAll(rooms);
        }
        return availableUnits;
    }

    private void createContractVariant(User tenantUser, Room room, List<User> managers,
                                       ContractStatus status, PriceApprovalStatus priceStatus,
                                       PaymentStatus paymentStatus, boolean withInvoices) {
        Tenant tenant = tenantUser.getTenantProfile();
        if (tenant == null) return;
        Property property = room.getProperty();
        boolean wholeHouse = Boolean.TRUE.equals(property.getWholeHouse());
        BigDecimal rent = room.getPrice() != null ? room.getPrice() : property.getPrice();
        BigDecimal deposit = room.getDeposit() != null ? room.getDeposit() : rent;
        LocalDate moveIn = status == ContractStatus.ACTIVE || status == ContractStatus.TERMINATED
                ? LocalDate.now().minusMonths(2).withDayOfMonth(1)
                : LocalDate.now().plusDays(3);

        User assigned = null;
        if (property.getOperationManagerId() != null) {
            assigned = managers.stream()
                    .filter(m -> m.getId().equals(property.getOperationManagerId()))
                    .findFirst().orElse(managers.isEmpty() ? null : managers.get(0));
        } else if (!managers.isEmpty()) {
            assigned = managers.get(contractSeq % managers.size());
        }

        String draftName = tenantUser.getFullName();
        String draftPhone = tenantUser.getPhoneNumber();
        String draftCccd = tenant.getCccd();

        TenantContract.TenantContractBuilder b = TenantContract.builder()
                .tenant(status == ContractStatus.DRAFT ? null : tenant)
                .property(property)
                .room(wholeHouse ? null : room)
                .contractCode(String.format("HD-MT-%d-%05d", LocalDate.now().getYear(), ++contractSeq))
                .rentAmount(rent)
                .deposit(deposit)
                .depositMonths(1)
                .moveInDate(moveIn)
                .startDate(moveIn)
                .endDate(moveIn.plusYears(1))
                .initialElectricReading(new BigDecimal("1000"))
                .initialWaterReading(new BigDecimal("50"))
                .status(status)
                .paymentStatus(paymentStatus)
                .priceApprovalStatus(priceStatus)
                .assignedManager(assigned)
                .draftTenantName(status == ContractStatus.DRAFT || status == ContractStatus.PENDING ? draftName : null)
                .draftTenantPhone(status == ContractStatus.DRAFT || status == ContractStatus.PENDING ? draftPhone : null)
                .draftTenantCccd(status == ContractStatus.DRAFT || status == ContractStatus.PENDING ? draftCccd : null);

        if (paymentStatus == PaymentStatus.PAID) {
            b.paidAt(moveIn.atStartOfDay())
                    .depositPaidAt(moveIn.atStartOfDay())
                    .depositMethod("SEED");
        }
        if (status == ContractStatus.ACTIVE) {
            b.activatedAt(moveIn.atStartOfDay());
        }
        if (status == ContractStatus.TERMINATED) {
            b.activatedAt(moveIn.atStartOfDay())
                    .terminatedAt(LocalDateTime.now().minusDays(10))
                    .terminationType(ContractTerminationType.MUTUAL_AGREEMENT)
                    .terminationReason("Seed demo - đã kết thúc");
        }

        TenantContract contract = tenantContractRepository.save(b.build());

        if (status == ContractStatus.ACTIVE) {
            room.setStatus(RoomStatus.RENTED);
            roomRepository.save(room);
            if (wholeHouse) {
                property.setStatus(PropertyStatus.RENTED);
                propertyRepository.save(property);
            }
            if (withInvoices) {
                seedInvoicesForActive(contract, tenantUser.getId(), property, room);
            }
        }
    }

    private void seedInvoicesForActive(TenantContract contract, java.util.UUID tenantUserId,
                                       Property property, Room room) {
        BigDecimal rent = contract.getRentAmount();
        BigDecimal eRate = property.getElectricityUnitPrice() != null
                ? property.getElectricityUnitPrice() : new BigDecimal("3500");
        BigDecimal wRate = property.getWaterUnitPrice() != null
                ? property.getWaterUnitPrice() : new BigDecimal("15000");
        BigDecimal serviceFee = property.getServiceFee() != null
                ? property.getServiceFee() : new BigDecimal("100000");
        String roomNumber = room.getRoomNumber();

        // Onboard PAID (tiền nhà tháng đầu + cọc)
        BigDecimal deposit = contract.getDeposit() != null ? contract.getDeposit() : rent;
        String onboardNote = "ONBOARD|rentAmount=" + rent.toPlainString()
                + "|depositAmount=" + deposit.toPlainString()
                + "|depositMonths=1";
        TenantInvoice onboard = TenantInvoice.builder()
                .code("HD-ONBOARD-" + contract.getId())
                .tenantUserId(tenantUserId)
                .tenantContract(contract)
                .invoiceType(TenantInvoiceType.RENT)
                .propertyName(property.getPropertyName())
                .roomNumber(roomNumber)
                .billingPeriod("Thu lúc nhận phòng " + contract.getMoveInDate())
                .note(onboardNote)
                .totalAmount(rent.add(deposit))
                .lateFee(BigDecimal.ZERO)
                .grandTotal(rent.add(deposit))
                .status(TenantInvoiceStatus.PAID)
                .createdAt(contract.getMoveInDate().atStartOfDay())
                .paidAt(contract.getMoveInDate().atStartOfDay())
                .paymentMethod("SEED")
                .build();
        try {
            TenantInvoice saved = tenantInvoiceRepository.save(onboard);
            tenantPaymentRepository.save(TenantPayment.builder()
                    .tenantInvoice(saved)
                    .tenantUserId(tenantUserId)
                    .invoiceCode(saved.getCode())
                    .invoiceType(saved.getInvoiceType())
                    .amount(saved.getGrandTotal())
                    .method("SEED")
                    .paidAt(saved.getPaidAt())
                    .propertyName(property.getPropertyName())
                    .roomNumber(roomNumber)
                    .build());
        } catch (Exception e) {
            log.warn("SampleDataSeeder: bỏ qua hoá đơn onboard ({}).", e.getMessage());
        }

        LocalDate prev = LocalDate.now().minusMonths(1);
        createInvoice(contract, tenantUserId, property.getPropertyName(), roomNumber, prev,
                TenantInvoiceType.RENT, rent, null, null, TenantInvoiceStatus.PAID);
        createInvoice(contract, tenantUserId, property.getPropertyName(), roomNumber, prev,
                TenantInvoiceType.ELECTRICITY, eRate.multiply(BigDecimal.valueOf(120)),
                new BigDecimal("120"), eRate, TenantInvoiceStatus.PAID);
        createInvoice(contract, tenantUserId, property.getPropertyName(), roomNumber, prev,
                TenantInvoiceType.WATER, wRate.multiply(BigDecimal.valueOf(8)),
                new BigDecimal("8"), wRate, TenantInvoiceStatus.PAID);
        createInvoice(contract, tenantUserId, property.getPropertyName(), roomNumber, prev,
                TenantInvoiceType.SERVICE, serviceFee, null, null, TenantInvoiceStatus.PAID);

        LocalDate cur = LocalDate.now();
        long id = contract.getId() != null ? contract.getId() : 0;
        TenantInvoiceStatus rentStatus = (id % 4 == 0) ? TenantInvoiceStatus.OVERDUE : TenantInvoiceStatus.PENDING;
        createInvoice(contract, tenantUserId, property.getPropertyName(), roomNumber, cur,
                TenantInvoiceType.RENT, rent, null, null, rentStatus);
        createInvoice(contract, tenantUserId, property.getPropertyName(), roomNumber, cur,
                TenantInvoiceType.ELECTRICITY, eRate.multiply(BigDecimal.valueOf(135)),
                new BigDecimal("135"), eRate, TenantInvoiceStatus.PENDING);
        createInvoice(contract, tenantUserId, property.getPropertyName(), roomNumber, cur,
                TenantInvoiceType.WATER, wRate.multiply(BigDecimal.valueOf(9)),
                new BigDecimal("9"), wRate, TenantInvoiceStatus.PENDING);
    }

    private void createInvoice(TenantContract contract, java.util.UUID tenantUserId, String propertyName,
                               String roomNumber, LocalDate month, TenantInvoiceType type,
                               BigDecimal total, BigDecimal usage, BigDecimal rate,
                               TenantInvoiceStatus status) {
        BigDecimal lateFee = status == TenantInvoiceStatus.OVERDUE
                ? total.multiply(new BigDecimal("0.05")) : BigDecimal.ZERO;
        BigDecimal grand = total.add(lateFee);
        LocalDateTime createdAt = month.withDayOfMonth(1).atStartOfDay();
        LocalDate dueDate = month.withDayOfMonth(Math.min(5, month.lengthOfMonth()));
        String code = String.format("INV%05d-%d%02d-%s", contract.getId(),
                month.getYear(), month.getMonthValue(), type.name().substring(0, 1));

        TenantInvoice.TenantInvoiceBuilder b = TenantInvoice.builder()
                .code(code)
                .tenantUserId(tenantUserId)
                .tenantContract(contract)
                .invoiceType(type)
                .propertyName(propertyName)
                .roomNumber(roomNumber)
                .billingMonth(month.getMonthValue())
                .billingYear(month.getYear())
                .billingPeriod(String.format("%02d/%d", month.getMonthValue(), month.getYear()))
                .totalAmount(total)
                .lateFee(lateFee)
                .grandTotal(grand)
                .status(status)
                .dueDate(dueDate)
                .createdAt(createdAt);

        if (type == TenantInvoiceType.ELECTRICITY) {
            b.kwhUsed(usage).electricityRate(rate);
        } else if (type == TenantInvoiceType.WATER) {
            b.m3Used(usage).waterRate(rate);
        }
        if (status == TenantInvoiceStatus.PAID) {
            b.paidAt(createdAt.plusDays(2)).paymentMethod("BANK_TRANSFER")
                    .transactionId("TXN" + contract.getId() + month.getMonthValue() + type.name().charAt(0));
        }
        try {
            tenantInvoiceRepository.save(b.build());
        } catch (Exception e) {
            log.warn("SampleDataSeeder: bỏ qua hóa đơn {} ({}).", type, e.getMessage());
        }
    }
}
