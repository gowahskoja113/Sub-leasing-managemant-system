-- =============================================================================
-- SLMS: CLEAR data trên Supabase + seed lại tối thiểu (khớp DemoBootstrapSeeder)
--
-- CÁCH LÀM:
--   1. Đảm bảo API trên Render đã từng start thành công (Hibernate tạo schema).
--      Nếu chưa có bảng zone → restart Render trước, rồi mới chạy script này.
--   2. Supabase → SQL Editor → paste toàn bộ → Run → "Run without RLS"
--
-- Password mọi account: 123456
--
-- Accounts: admin01/02, owner01/02, manager01/02, tenant01/02, user01/02
-- manager01 → Phú Nhuận, Quận 3, Bình Thạnh
-- manager02 → Gò Vấp, Quận 1, Cầu Giấy
--
-- Lưu ý tên bảng: Spring Boot → Postgres thường lower-case (zone, admin, ...).
-- Bảng User có thể là "User" (quoted) vì từ khóa SQL.
-- =============================================================================

BEGIN;

-- 0) Kiểm tra schema đã có chưa
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'zone'
  ) THEN
    RAISE EXCEPTION
      'Chưa có bảng zone. Hãy restart API trên Render để Hibernate tạo schema, rồi chạy lại script.';
  END IF;
END $$;

-- 1) Clear toàn bộ data (giữ schema)
DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN (
    SELECT tablename
    FROM pg_tables
    WHERE schemaname = 'public'
  ) LOOP
    EXECUTE format('TRUNCATE TABLE %I RESTART IDENTITY CASCADE;', r.tablename);
  END LOOP;
END $$;

-- 2) Master data
INSERT INTO equipment_catalog (name, description, active) VALUES
  ('Điều hòa', 'Máy lạnh / điều hòa không khí', true),
  ('Tủ lạnh', 'Tủ lạnh các loại', true),
  ('Máy giặt', 'Máy giặt cửa trước / cửa trên', true),
  ('Bàn ăn', 'Bàn ăn và ghế', true),
  ('Giường', 'Giường ngủ các loại', true),
  ('Tủ quần áo', 'Tủ đựng quần áo', true),
  ('Bếp từ', 'Bếp từ / bếp gas', true),
  ('Nóng lạnh', 'Máy nước nóng', true),
  ('Quạt', 'Quạt điện / quạt trần', true),
  ('Khác', 'Thiết bị khác', true);

INSERT INTO renovation_categories (code, name, description, active) VALUES
  ('PAINTING', 'Sơn sửa', 'Sơn tường, trần nhà', true),
  ('PLUMBING', 'Điện nước', 'Sửa chữa hệ thống điện nước', true),
  ('FLOORING', 'Sàn nhà', 'Lát sàn, sửa sàn', true),
  ('FURNITURE', 'Nội thất', 'Mua sắm nội thất mới', true),
  ('EQUIPMENT', 'Thiết bị mua thêm', 'Mua thêm thiết bị trong đợt cải tạo', true),
  ('STRUCTURAL', 'Kết cấu', 'Thay đổi kết cấu, vách ngăn', true),
  ('OTHER', 'Khác', 'Hạng mục cải tạo khác', true);

-- 3) Zones
INSERT INTO zone (id, name, description, level, parent_id) VALUES
  ('11111111-1111-1111-1111-111111111001', 'Hà Nội', 'Tỉnh/Thành phố', 1, NULL),
  ('11111111-1111-1111-1111-111111111002', 'TP. Hồ Chí Minh', 'Tỉnh/Thành phố', 1, NULL);

INSERT INTO zone (id, name, description, level, parent_id) VALUES
  ('22222222-2222-2222-2222-222222222001', 'Cầu Giấy', 'Quận/Huyện', 2, '11111111-1111-1111-1111-111111111001'),
  ('22222222-2222-2222-2222-222222222002', 'Phú Nhuận', 'Quận/Huyện', 2, '11111111-1111-1111-1111-111111111002'),
  ('22222222-2222-2222-2222-222222222003', 'Quận 3', 'Quận/Huyện', 2, '11111111-1111-1111-1111-111111111002'),
  ('22222222-2222-2222-2222-222222222004', 'Bình Thạnh', 'Quận/Huyện', 2, '11111111-1111-1111-1111-111111111002'),
  ('22222222-2222-2222-2222-222222222005', 'Gò Vấp', 'Quận/Huyện', 2, '11111111-1111-1111-1111-111111111002'),
  ('22222222-2222-2222-2222-222222222006', 'Quận 1', 'Quận/Huyện', 2, '11111111-1111-1111-1111-111111111002');

-- 4) Users — bảng có thể tên "User" (quoted). Dùng dynamic SQL cho an toàn.
DO $$
DECLARE
  user_tbl text;
  pw text := '$2a$10$pDrQSc03zsx2Fialkri.M.QLNgm1lqDszY56vmTIpjZlVXMY9oW7a';
BEGIN
  SELECT tablename INTO user_tbl
  FROM pg_tables
  WHERE schemaname = 'public'
    AND lower(tablename) = 'user'
  LIMIT 1;

  IF user_tbl IS NULL THEN
    RAISE EXCEPTION 'Không tìm thấy bảng User/user. Restart API Render rồi chạy lại.';
  END IF;

  EXECUTE format(
    'INSERT INTO %I (
       id, username, password, phone_number, email, full_name,
       role, status, create_at, is_first_login
     ) VALUES
       (%L::uuid, %L, %L, %L, %L, %L, %L, %L, now(), false),
       (%L::uuid, %L, %L, %L, %L, %L, %L, %L, now(), false),
       (%L::uuid, %L, %L, %L, %L, %L, %L, %L, now(), false),
       (%L::uuid, %L, %L, %L, %L, %L, %L, %L, now(), false),
       (%L::uuid, %L, %L, %L, %L, %L, %L, %L, now(), false),
       (%L::uuid, %L, %L, %L, %L, %L, %L, %L, now(), false),
       (%L::uuid, %L, %L, %L, %L, %L, %L, %L, now(), false),
       (%L::uuid, %L, %L, %L, %L, %L, %L, %L, now(), false),
       (%L::uuid, %L, %L, %L, %L, %L, %L, %L, now(), false),
       (%L::uuid, %L, %L, %L, %L, %L, %L, %L, now(), false)',
    user_tbl,
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001', 'admin01',   pw, '0901000001', 'admin01@slms.local',   'Quản Trị Viên 1',    'ROLE_ADMIN',   'ACTIVE',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0002', 'admin02',   pw, '0901000002', 'admin02@slms.local',   'Quản Trị Viên 2',    'ROLE_ADMIN',   'ACTIVE',
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001', 'owner01',   pw, '0902000001', 'owner01@slms.local',   'Chủ Nhà 1',          'ROLE_OWNER',   'ACTIVE',
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0002', 'owner02',   pw, '0902000002', 'owner02@slms.local',   'Chủ Nhà 2',          'ROLE_OWNER',   'ACTIVE',
    'cccccccc-cccc-cccc-cccc-cccccccc0001', 'manager01', pw, '0903000001', 'manager01@slms.local', 'Quản Lý Vận Hành 1', 'ROLE_MANAGER', 'ACTIVE',
    'cccccccc-cccc-cccc-cccc-cccccccc0002', 'manager02', pw, '0903000002', 'manager02@slms.local', 'Quản Lý Vận Hành 2', 'ROLE_MANAGER', 'ACTIVE',
    'dddddddd-dddd-dddd-dddd-dddddddd0001', 'tenant01',  pw, '0904000001', 'tenant01@slms.local',  'Khách Thuê 1',       'ROLE_TENANT',  'ACTIVE',
    'dddddddd-dddd-dddd-dddd-dddddddd0002', 'tenant02',  pw, '0904000002', 'tenant02@slms.local',  'Khách Thuê 2',       'ROLE_TENANT',  'ACTIVE',
    'eeeeeeee-eeee-eeee-eeee-eeeeeeee0001', 'user01',    pw, '0905000001', 'user01@slms.local',    'Người Dùng 1',       'ROLE_USER',    'ACTIVE',
    'eeeeeeee-eeee-eeee-eeee-eeeeeeee0002', 'user02',    pw, '0905000002', 'user02@slms.local',    'Người Dùng 2',       'ROLE_USER',    'ACTIVE'
  );
END $$;

-- Profiles (tên bảng lower-case theo Spring naming)
INSERT INTO admin (user_id, start_at) VALUES
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001', now()),
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0002', now());

INSERT INTO owner (user_id) VALUES
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001'),
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0002');

INSERT INTO operation_management (user_id, start_at) VALUES
  ('cccccccc-cccc-cccc-cccc-cccccccc0001', now()),
  ('cccccccc-cccc-cccc-cccc-cccccccc0002', now());

INSERT INTO tenant (user_id, cccd) VALUES
  ('dddddddd-dddd-dddd-dddd-dddddddd0001', '079000000001'),
  ('dddddddd-dddd-dddd-dddd-dddddddd0002', '079000000002');

-- 5) manager ↔ zone
INSERT INTO manager_zones (manager_id, zone_id) VALUES
  ('cccccccc-cccc-cccc-cccc-cccccccc0001', '22222222-2222-2222-2222-222222222002'), -- Phú Nhuận
  ('cccccccc-cccc-cccc-cccc-cccccccc0001', '22222222-2222-2222-2222-222222222003'), -- Quận 3
  ('cccccccc-cccc-cccc-cccc-cccccccc0001', '22222222-2222-2222-2222-222222222004'), -- Bình Thạnh
  ('cccccccc-cccc-cccc-cccc-cccccccc0002', '22222222-2222-2222-2222-222222222005'), -- Gò Vấp
  ('cccccccc-cccc-cccc-cccc-cccccccc0002', '22222222-2222-2222-2222-222222222006'), -- Quận 1
  ('cccccccc-cccc-cccc-cccc-cccccccc0002', '22222222-2222-2222-2222-222222222001'); -- Cầu Giấy

COMMIT;
