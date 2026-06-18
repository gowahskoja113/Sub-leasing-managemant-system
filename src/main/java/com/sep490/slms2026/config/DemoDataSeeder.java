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
 * Seed dữ liệu demo: 1 tài khoản manager "long2", các zone của TP.HCM và 5 bất động sản
 * (mix nhà nguyên căn + nhà chia phòng) kèm trang thiết bị nội thất.
 * Chạy sau {@link MasterDataSeeder} (@Order(1)) để chắc chắn EquipmentCatalog đã có sẵn.
 * Toàn bộ idempotent: chạy lại mỗi lần khởi động sẽ không tạo trùng.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {

    private static final String MANAGER_USERNAME = "long2";

    private final UserRepository userRepository;
    private final OperationManagementRepository operationManagementRepository;
    private final ZoneRepository zoneRepository;
    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final EquipmentCatalogRepository equipmentCatalogRepository;
    private final EquipmentRepository equipmentRepository;
    private final EquipmentManifestRepository equipmentManifestRepository;
    private final PasswordEncoder passwordEncoder;

    private Map<String, EquipmentCatalog> catalogByName;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        User manager = ensureManager();
        Map<String, Zone> districts = seedZones(manager);

        catalogByName = equipmentCatalogRepository.findAll().stream()
                .collect(Collectors.toMap(EquipmentCatalog::getName, c -> c, (a, b) -> a));

        seedProperties(manager.getId(), districts);
        seedTenantUsers();
        log.info("DemoDataSeeder: hoàn tất seed dữ liệu demo (manager={}).", MANAGER_USERNAME);
    }

    // ---------------------------------------------------------------------
    // Bước 1: tài khoản manager long2
    // ---------------------------------------------------------------------
    private User ensureManager() {
        Optional<User> existing = userRepository.findByUsername(MANAGER_USERNAME);
        if (existing.isPresent()) {
            return existing.get();
        }
        User u = new User();
        u.setUsername(MANAGER_USERNAME);
        u.setPassword(passwordEncoder.encode("123456"));
        u.setRole(Role.ROLE_MANAGER);
        u.setStatus(UserStatus.ACTIVE);
        u.setFullName("Long Quản Lý");
        u.setPhoneNumber("0900000002");

        // Tạo profile OperationManagement giống UserServiceImpl.createUser
        OperationManagement profile = new OperationManagement();
        profile.setUser(u);
        profile.setStartAt(LocalDateTime.now());
        u.setOperationManagementProfile(profile);

        User saved = userRepository.save(u);
        log.info("DemoDataSeeder: đã tạo tài khoản manager '{}' (id={}).", MANAGER_USERNAME, saved.getId());
        return saved;
    }

    // ---------------------------------------------------------------------
    // Bước 2: zone TP.HCM + các quận, gán cho manager
    // ---------------------------------------------------------------------
    private Map<String, Zone> seedZones(User manager) {
        Zone hcm = ensureRootZone("TP. Hồ Chí Minh");
        String[] districtNames = {"Quận 1", "Quận 3", "Bình Thạnh", "Gò Vấp", "Phú Nhuận"};

        Map<String, Zone> districts = new LinkedHashMap<>();
        for (String name : districtNames) {
            districts.put(name, ensureChildZone(name, hcm));
        }

        operationManagementRepository.findById(manager.getId()).ifPresent(profile -> {
            boolean changed = false;
            for (Zone zone : districts.values()) {
                boolean already = profile.getZones().stream()
                        .anyMatch(z -> z.getId().equals(zone.getId()));
                if (!already) {
                    profile.getZones().add(zone);
                    changed = true;
                }
            }
            if (changed) {
                operationManagementRepository.save(profile);
            }
        });

        return districts;
    }

    private Zone ensureRootZone(String name) {
        return zoneRepository.findAll().stream()
                .filter(z -> z.getParent() == null && z.getName().equals(name))
                .findFirst()
                .orElseGet(() -> {
                    Zone z = new Zone();
                    z.setName(name);
                    z.setLevel(1);
                    z.setDescription("Tỉnh/Thành phố");
                    return zoneRepository.save(z);
                });
    }

    private Zone ensureChildZone(String name, Zone parent) {
        return zoneRepository.findAllByParentId(parent.getId()).stream()
                .filter(z -> z.getName().equals(name))
                .findFirst()
                .orElseGet(() -> {
                    Zone z = new Zone();
                    z.setName(name);
                    z.setLevel(2);
                    z.setParent(parent);
                    z.setDescription("Quận/Huyện");
                    return zoneRepository.save(z);
                });
    }

    // ---------------------------------------------------------------------
    // Bước 3 + 4: property + phòng + trang thiết bị
    // ---------------------------------------------------------------------
    private void seedProperties(UUID managerId, Map<String, Zone> districts) {
        Set<String> existingNames = propertyRepository.findAll().stream()
                .map(Property::getPropertyName)
                .collect(Collectors.toSet());

        // 1) Nhà nguyên căn - Quận 3
        createWholeHouse(existingNames, managerId, districts.get("Quận 3"),
                "Nhà phố 3 tầng Võ Văn Tần",
                "123 Võ Văn Tần",
                "Nhà nguyên căn 3 tầng, thiết kế hiện đại, 4 phòng ngủ, đầy đủ nội thất, "
                        + "phù hợp gia đình hoặc cho thuê dài hạn. Khu trung tâm, an ninh tốt.",
                120.0, 3, 4,
                new BigDecimal("35000000"),
                List.of(
                        "https://images.unsplash.com/photo-1568605114967-8130f3a36994",
                        "https://images.unsplash.com/photo-1570129477492-45c003edd2be",
                        "https://images.unsplash.com/photo-1512917774080-9991f1c4c750"),
                "4 phòng ngủ, 1 phòng khách, 1 bếp, 3 WC, sân thượng");

        // 2) Nhà nguyên căn - Bình Thạnh
        createWholeHouse(existingNames, managerId, districts.get("Bình Thạnh"),
                "Villa mini Điện Biên Phủ",
                "45 Điện Biên Phủ",
                "Villa mini 2 tầng có sân vườn, 3 phòng ngủ rộng rãi, nội thất cao cấp, "
                        + "gara để xe ô tô. Yên tĩnh, thoáng mát, gần sông Sài Gòn.",
                95.0, 2, 3,
                new BigDecimal("28000000"),
                List.of(
                        "https://images.unsplash.com/photo-1600585154340-be6161a56a0c",
                        "https://images.unsplash.com/photo-1600566753086-00f18fb6b3ea"),
                "3 phòng ngủ, 1 phòng khách, 1 bếp, 2 WC, sân vườn, gara");

        // 3) Nhà chia phòng - Quận 1
        createRoomBased(existingNames, managerId, districts.get("Quận 1"),
                "Căn hộ dịch vụ Nguyễn Thị Minh Khai",
                "200 Nguyễn Thị Minh Khai",
                "Căn hộ dịch vụ cao cấp ngay trung tâm Quận 1, full nội thất, "
                        + "có thang máy, bảo vệ 24/7. Phòng sạch sẽ, ban công thoáng.",
                250.0, 5, 8,
                new BigDecimal("6500000"),
                List.of(
                        "https://images.unsplash.com/photo-1522708323590-d24dbb6b0267",
                        "https://images.unsplash.com/photo-1502672260266-1c1ef2d93688"));

        // 4) Nhà chia phòng - Phú Nhuận
        createRoomBased(existingNames, managerId, districts.get("Phú Nhuận"),
                "Nhà trọ cao cấp Phan Xích Long",
                "78 Phan Xích Long",
                "Dãy phòng trọ mới xây, mỗi phòng có gác lửng, máy lạnh, nóng lạnh. "
                        + "Khu dân cư an ninh, gần chợ và nhiều quán ăn.",
                180.0, 4, 6,
                new BigDecimal("4200000"),
                List.of(
                        "https://images.unsplash.com/photo-1493809842364-78817add7ffb",
                        "https://images.unsplash.com/photo-1505691938895-1758d7feb511"));

        // 5) Nhà chia phòng - Gò Vấp
        createRoomBased(existingNames, managerId, districts.get("Gò Vấp"),
                "Phòng trọ sinh viên Quang Trung",
                "350 Quang Trung",
                "Phòng trọ giá tốt cho sinh viên và người đi làm, có chỗ để xe rộng, "
                        + "giờ giấc tự do. Gần các trường đại học và khu công nghệ phần mềm.",
                150.0, 3, 5,
                new BigDecimal("3000000"),
                List.of(
                        "https://images.unsplash.com/photo-1484154218962-a197022b5858",
                        "https://images.unsplash.com/photo-1556911220-bff31c812dba"));

        // ============================================================
        // Bổ sung cho đủ 10 nhà nguyên căn + 10 nhà chia phòng (demo onboarding)
        // ============================================================
        String[] dCycle = {"Quận 1", "Quận 3", "Bình Thạnh", "Gò Vấp", "Phú Nhuận"};
        List<String> imgsWH = List.of(
                "https://images.unsplash.com/photo-1568605114967-8130f3a36994",
                "https://images.unsplash.com/photo-1570129477492-45c003edd2be");
        List<String> imgsRB = List.of(
                "https://images.unsplash.com/photo-1493809842364-78817add7ffb",
                "https://images.unsplash.com/photo-1505691938895-1758d7feb511");

        // 8 nhà nguyên căn (đã có 2 -> tổng 10)
        String[] whName = {"Nhà phố Lê Lợi", "Villa Nguyễn Huệ", "Nhà nguyên căn Hai Bà Trưng",
                "Biệt thự Trần Hưng Đạo", "Nhà phố Cách Mạng Tháng 8", "Villa Nguyễn Đình Chiểu",
                "Nhà nguyên căn Pasteur", "Nhà phố Lý Tự Trọng"};
        String[] whAddr = {"12 Lê Lợi", "34 Nguyễn Huệ", "56 Hai Bà Trưng", "78 Trần Hưng Đạo",
                "90 Cách Mạng Tháng 8", "21 Nguyễn Đình Chiểu", "43 Pasteur", "65 Lý Tự Trọng"};
        int[] whBeds = {2, 3, 4, 5, 3, 2, 4, 3};
        long[] whPrice = {18000000, 22000000, 30000000, 40000000, 25000000, 16000000, 32000000, 24000000};
        for (int i = 0; i < whName.length; i++) {
            int beds = whBeds[i];
            createWholeHouse(existingNames, managerId, districts.get(dCycle[i % dCycle.length]),
                    whName[i], whAddr[i],
                    "Nhà nguyên căn cho thuê, nội thất đầy đủ, vị trí thuận tiện.",
                    70.0 + i * 8, 2 + (i % 3), beds, BigDecimal.valueOf(whPrice[i]), imgsWH,
                    beds + " phòng ngủ, 1 phòng khách, 1 bếp, " + Math.max(1, beds - 1) + " WC");
        }

        // 7 nhà chia phòng (đã có 3 -> tổng 10)
        String[] rbName = {"Nhà trọ Cộng Hòa", "Căn hộ mini Trường Chinh", "Nhà trọ Lạc Long Quân",
                "KTX mini Nguyễn Oanh", "Nhà trọ Lê Văn Sỹ", "Căn hộ DV Hoàng Văn Thụ", "Nhà trọ Phạm Văn Đồng"};
        String[] rbAddr = {"15 Cộng Hòa", "27 Trường Chinh", "39 Lạc Long Quân", "51 Nguyễn Oanh",
                "63 Lê Văn Sỹ", "75 Hoàng Văn Thụ", "87 Phạm Văn Đồng"};
        int[] rbRooms = {6, 8, 10, 5, 7, 9, 4};
        long[] rbPrice = {3500000, 5000000, 2800000, 4000000, 3200000, 6000000, 2500000};
        for (int i = 0; i < rbName.length; i++) {
            createRoomBased(existingNames, managerId, districts.get(dCycle[i % dCycle.length]),
                    rbName[i], rbAddr[i],
                    "Nhà chia phòng cho thuê, phòng khép kín đầy đủ tiện nghi.",
                    120.0 + i * 15, 3 + (i % 3), rbRooms[i], BigDecimal.valueOf(rbPrice[i]), imgsRB);
        }
    }

    private void createWholeHouse(Set<String> existing, UUID managerId, Zone zone, String name,
                                  String shortAddress, String descriptions, double area, int floors,
                                  int bedrooms, BigDecimal price, List<String> images, String structure) {
        if (zone == null || existing.contains(name)) {
            return;
        }
        Property property = baseProperty(managerId, zone, name, shortAddress, descriptions,
                area, floors, bedrooms, price, images)
                .wholeHouse(true)
                .build();
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

        // Trang thiết bị nội thất cho cả căn nhà
        addEquipment(property, room, "Điều hòa", HouseArea.LIVING_ROOM, EquipmentSource.INITIAL_HANDOVER, EquipmentStatus.NEW, "Daikin Inverter 2.0HP - phòng khách");
        addEquipment(property, room, "Điều hòa", HouseArea.BEDROOM, EquipmentSource.INITIAL_HANDOVER, EquipmentStatus.GOOD, "Panasonic 1.5HP - phòng ngủ chính");
        addEquipment(property, room, "Tủ lạnh", HouseArea.KITCHEN, EquipmentSource.INITIAL_HANDOVER, EquipmentStatus.GOOD, "Toshiba 250L");
        addEquipment(property, room, "Bếp từ", HouseArea.KITCHEN, EquipmentSource.INITIAL_HANDOVER, EquipmentStatus.NEW, "Bếp từ đôi");
        addEquipment(property, room, "Bàn ăn", HouseArea.KITCHEN, EquipmentSource.INITIAL_HANDOVER, EquipmentStatus.GOOD, "Bàn ăn gỗ 6 ghế");
        addEquipment(property, room, "Giường", HouseArea.BEDROOM, EquipmentSource.INITIAL_HANDOVER, EquipmentStatus.GOOD, "Giường gỗ 1m8");
        addEquipment(property, room, "Tủ quần áo", HouseArea.BEDROOM, EquipmentSource.INITIAL_HANDOVER, EquipmentStatus.GOOD, "Tủ áo 4 cánh");
        addEquipment(property, room, "Máy giặt", HouseArea.BALCONY, EquipmentSource.PURCHASED, EquipmentStatus.NEW, "LG cửa trước 9kg");
        addEquipment(property, room, "Nóng lạnh", HouseArea.BATHROOM, EquipmentSource.INITIAL_HANDOVER, EquipmentStatus.NEW, "Ariston 30L");

        // Bảng kê tổng hợp (manifest)
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
        if (zone == null || existing.contains(name)) {
            return;
        }
        Property property = baseProperty(managerId, zone, name, shortAddress, descriptions,
                area, floors, roomCount, roomPrice, images)
                .wholeHouse(false)
                .build();
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

        // Thiết bị dùng chung của toà
        addEquipment(property, null, "Máy giặt", HouseArea.OTHER, EquipmentSource.PURCHASED, EquipmentStatus.GOOD, "Máy giặt chung 10kg");

        // Bảng kê tổng hợp
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
                .operationManagerId(managerId)
                .managedBy(managerId)
                .descriptions(descriptions)
                .price(price)
                .renovationStartDate(LocalDate.now().minusMonths(2))
                .renovationEndDate(LocalDate.now().minusMonths(1))
                .renovationCompleted(true);
    }

    private void addEquipment(Property property, Room room, String catalogName, HouseArea area,
                              EquipmentSource source, EquipmentStatus status, String note) {
        EquipmentCatalog catalog = catalogByName.get(catalogName);
        if (catalog == null) {
            return;
        }
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

    // ---------------------------------------------------------------------
    // 30 tài khoản khách thuê chưa có nhà (để demo luồng onboarding)
    // Đăng nhập: khach01..khach30 / 123456. Tra cứu theo SĐT 0912000001..0912000030.
    // ---------------------------------------------------------------------
    private void seedTenantUsers() {
        String[] ho = {"Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Vũ", "Đặng", "Bùi", "Đỗ", "Hồ"};
        String[] dem = {"Văn", "Thị", "Hữu", "Đức", "Minh", "Ngọc", "Gia", "Quang", "Thanh", "Khánh"};
        String[] ten = {"An", "Bình", "Cường", "Dũng", "Em", "Phúc", "Giang", "Hà", "Hiếu", "Khoa",
                "Long", "Mai", "Nam", "Oanh", "Phương", "Quân", "Quỳnh", "Sơn", "Tâm", "Uyên",
                "Vy", "Xuân", "Yến", "Bảo", "Châu", "Duy", "Hải", "Khang", "Linh", "Trang"};

        int created = 0;
        for (int i = 0; i < 30; i++) {
            String username = "khach" + String.format("%02d", i + 1);
            String phone = "0912" + String.format("%06d", i + 1);
            if (userRepository.existsByUsername(username) || userRepository.existsByPhoneNumber(phone)) {
                continue;
            }
            User u = new User();
            u.setUsername(username);
            u.setPassword(passwordEncoder.encode("123456"));
            u.setRole(Role.ROLE_TENANT);
            u.setStatus(UserStatus.ACTIVE);
            u.setFullName(ho[i % ho.length] + " " + dem[i % dem.length] + " " + ten[i]);
            u.setPhoneNumber(phone);

            Tenant profile = new Tenant();
            profile.setUser(u);
            profile.setCccd("079" + String.format("%09d", i + 1));
            u.setTenantProfile(profile);

            try {
                userRepository.save(u);
                created++;
            } catch (Exception e) {
                log.warn("DemoDataSeeder: bỏ qua khách thuê {}: {}", username, e.getMessage());
            }
        }
        log.info("DemoDataSeeder: seed {} khách thuê demo (khach01..khach30 / 123456).", created);
    }

    private void addManifest(Property property, String catalogName, int quantity, EquipmentStatus status) {
        EquipmentCatalog catalog = catalogByName.get(catalogName);
        if (catalog == null) {
            return;
        }
        equipmentManifestRepository.save(EquipmentManifest.builder()
                .property(property)
                .catalog(catalog)
                .quantity(quantity)
                .status(status)
                .source(EquipmentSource.INITIAL_HANDOVER)  // @Builder bỏ qua initializer -> phải set tường minh
                .price(BigDecimal.ZERO)
                .build());
    }
}
