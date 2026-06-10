package com.sep490.slms2026.config;

import com.sep490.slms2026.entity.EquipmentCatalog;
import com.sep490.slms2026.entity.RenovationCategory;
import com.sep490.slms2026.repository.EquipmentCatalogRepository;
import com.sep490.slms2026.repository.RenovationCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class MasterDataSeeder implements ApplicationRunner {

    private final EquipmentCatalogRepository equipmentCatalogRepository;
    private final RenovationCategoryRepository renovationCategoryRepository;

    @Override
    public void run(ApplicationArguments args) {
        seedEquipmentCatalog();
        seedRenovationCategories();
    }

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
}
