-- =============================================================================
-- SLMS2026 — master data (catalog, zone, billing)
-- Tài khoản demo (admin/owner/manager/tenant) do SampleDataSeeder tạo lúc start app
--   (mật khẩu mặc định: 123456)
-- =============================================================================

INSERT INTO billing_config (id, reminder_lead_days, grace_days, meter_reminder_lead_days, updated_at)
VALUES (1, 3, 2, 1, NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO equipment_catalog (name, description, active) VALUES
    ('Điều hòa', 'Máy lạnh / điều hòa không khí', TRUE),
    ('Tủ lạnh', 'Tủ lạnh các loại', TRUE),
    ('Máy giặt', 'Máy giặt cửa trước / cửa trên', TRUE),
    ('Bàn ăn', 'Bàn ăn và ghế', TRUE),
    ('Giường', 'Giường ngủ các loại', TRUE),
    ('Tủ quần áo', 'Tủ đựng quần áo', TRUE),
    ('Bếp từ', 'Bếp từ / bếp gas', TRUE),
    ('Nóng lạnh', 'Máy nước nóng', TRUE),
    ('Quạt', 'Quạt điện / quạt trần', TRUE),
    ('Khác', 'Thiết bị khác', TRUE)
ON CONFLICT (name) DO NOTHING;

INSERT INTO renovation_categories (code, name, description, active) VALUES
    ('PAINTING', 'Sơn sửa', 'Sơn tường, trần nhà', TRUE),
    ('PLUMBING', 'Điện nước', 'Sửa chữa hệ thống điện nước', TRUE),
    ('FLOORING', 'Sàn nhà', 'Lát sàn, sửa sàn', TRUE),
    ('FURNITURE', 'Nội thất', 'Mua sắm nội thất mới', TRUE),
    ('EQUIPMENT', 'Thiết bị mua thêm', 'Mua thêm thiết bị trong đợt cải tạo', TRUE),
    ('STRUCTURAL', 'Kết cấu', 'Thay đổi kết cấu, vách ngăn', TRUE),
    ('OTHER', 'Khác', 'Hạng mục cải tạo khác', TRUE)
ON CONFLICT (code) DO NOTHING;

-- Zone cấp 1 (thành phố) + cấp 2 (quận) — khớp ZoneDataSeeder
INSERT INTO zone (id, name, description, level, parent_id)
SELECT gen_random_uuid(), 'Hà Nội', 'Tỉnh/Thành phố', 1, NULL
WHERE NOT EXISTS (SELECT 1 FROM zone z WHERE LOWER(z.name) = LOWER('Hà Nội') AND z.level = 1 AND z.parent_id IS NULL);

INSERT INTO zone (id, name, description, level, parent_id)
SELECT gen_random_uuid(), 'TP. Hồ Chí Minh', 'Tỉnh/Thành phố', 1, NULL
WHERE NOT EXISTS (SELECT 1 FROM zone z WHERE LOWER(z.name) = LOWER('TP. Hồ Chí Minh') AND z.level = 1 AND z.parent_id IS NULL);

INSERT INTO zone (id, name, description, level, parent_id)
SELECT gen_random_uuid(), d.district, 'Quận/Huyện', 2, c.id
FROM (VALUES
    ('Hà Nội', 'Cầu Giấy'),
    ('TP. Hồ Chí Minh', 'Phú Nhuận'),
    ('TP. Hồ Chí Minh', 'Quận 3'),
    ('TP. Hồ Chí Minh', 'Bình Thạnh'),
    ('TP. Hồ Chí Minh', 'Gò Vấp'),
    ('TP. Hồ Chí Minh', 'Quận 1')
) AS d(city, district)
JOIN zone c ON LOWER(c.name) = LOWER(d.city) AND c.level = 1 AND c.parent_id IS NULL
WHERE NOT EXISTS (
    SELECT 1 FROM zone z
    WHERE LOWER(z.name) = LOWER(d.district) AND z.level = 2 AND z.parent_id = c.id
);
