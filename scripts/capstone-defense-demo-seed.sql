-- =============================================================================
-- SLMS2026 — Capstone Defense Demo Seed (ADDITIVE — GIỮ DATA CŨ)
--
-- AN TOÀN CHO PRODUCTION:
--   • KHÔNG TRUNCATE / DELETE data hiện có
--   • Chỉ INSERT thêm nhà/khách/HĐ/hoá đơn/bảo trì demo
--   • Prefix rõ ràng: property_code demo#101..106, username demo_* , phone 0988*
--   • Idempotent: chạy lại lần 2 → bỏ qua nếu đã có demo#101
--
-- CÁCH DÙNG (khi cần bảo vệ mới chạy):
--   1. API production đã chạy (schema + property_code đã có)
--   2. Backup DB nếu muốn chắc chắn
--   3. Supabase SQL Editor → paste file này → Run (without RLS nếu cần)
--   4. Login demo: demo_owner / demo_tenant01..07 / demo_manager01 — password 123456
--
-- GỠ DEMO SAU BẢO VỆ (tuỳ chọn): xem cuối file — block cleanup (comment sẵn)
-- =============================================================================

BEGIN;

DO $$
DECLARE
  -- zones (reuse production)
  z_binhthanh uuid;
  z_phunhuan uuid;
  z_quan3 uuid;
  z_govap uuid;
  z_quan1 uuid;

  -- catalog ids
  cat_ac bigint;
  cat_fridge bigint;
  cat_washer bigint;
  cat_table bigint;
  cat_bed bigint;
  cat_wardrobe bigint;
  cat_stove bigint;
  cat_heater bigint;
  cat_fan bigint;

  -- users
  user_tbl text;
  pw text := '$2a$10$pDrQSc03zsx2Fialkri.M.QLNgm1lqDszY56vmTIpjZlVXMY9oW7a'; -- 123456

  uid_owner   uuid := 'd0d00000-0000-4000-8000-000000000001';
  uid_mgr1    uuid := 'd0d00000-0000-4000-8000-000000000011';
  uid_mgr2    uuid := 'd0d00000-0000-4000-8000-000000000012';
  uid_t01     uuid := 'd0d00000-0000-4000-8000-000000000101'; -- An
  uid_t02     uuid := 'd0d00000-0000-4000-8000-000000000102'; -- Bình
  uid_t03     uuid := 'd0d00000-0000-4000-8000-000000000103'; -- Cường
  uid_t04     uuid := 'd0d00000-0000-4000-8000-000000000104'; -- Dung
  uid_t05     uuid := 'd0d00000-0000-4000-8000-000000000105'; -- Em
  uid_t06     uuid := 'd0d00000-0000-4000-8000-000000000106'; -- Phương
  uid_t07     uuid := 'd0d00000-0000-4000-8000-000000000107'; -- Huy

  -- properties / rooms / contracts / equipment
  p101 bigint; p102 bigint; p103 bigint; p104 bigint; p105 bigint; p106 bigint;
  r101 bigint; r102 bigint; r103 bigint; r106 bigint;
  r104_p101 bigint; r104_p102 bigint; r104_p103 bigint;
  r105_201 bigint; r105_202 bigint;

  c_an bigint; c_binh bigint; c_cuong bigint; c_dung bigint;
  c_em_old bigint; c_em_new bigint; c_phuong bigint; c_huy bigint;

  eq_101_ac bigint; eq_101_fr bigint; eq_101_wh bigint;
  eq_102_fn bigint; eq_102_wh bigint;
  eq_104_ac bigint;
  eq_106_ac bigint; eq_106_wm bigint;

  mr_id bigint;
  inv_id bigint;
  inv_code text;
  ym date;
  end_ym date;
  due date;
  created_ts timestamp;
  paid_ts timestamp;
  st text;
  rent numeric;
  prop_name text;
  room_no text;
  tenant_uid uuid;
  cycle text;
  elec_used numeric;
  water_used numeric;
  elec_amt numeric;
  water_amt numeric;
  elec_price numeric;
  water_price numeric;
  svc_fee numeric;
  contract_id bigint;
  contract_status text;
  start_d date;
  end_d date;

  has_damage_cause boolean;
  has_flow_type boolean;
  has_assigned_mgr boolean;
BEGIN
  -- -------------------------------------------------------------------------
  -- 0) Guards
  -- -------------------------------------------------------------------------
  IF NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'zone') THEN
    RAISE EXCEPTION 'Chưa có bảng zone. Restart API rồi chạy lại.';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'properties' AND column_name = 'property_code'
  ) THEN
    ALTER TABLE properties ADD COLUMN property_code VARCHAR(32);
  END IF;

  IF EXISTS (SELECT 1 FROM properties WHERE property_code = 'demo#101') THEN
    RAISE NOTICE 'Demo seed đã có (demo#101). Bỏ qua — không đụng data.';
    RETURN;
  END IF;

  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='maintenance_requests' AND column_name='damage_cause'
  ) INTO has_damage_cause;
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='maintenance_requests' AND column_name='flow_type'
  ) INTO has_flow_type;
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='maintenance_requests' AND column_name='assigned_manager_id'
  ) INTO has_assigned_mgr;

  -- -------------------------------------------------------------------------
  -- 1) Zones — reuse production theo tên
  -- -------------------------------------------------------------------------
  SELECT id INTO z_binhthanh FROM zone WHERE level = 2 AND lower(name) LIKE '%bình thạnh%' LIMIT 1;
  SELECT id INTO z_phunhuan  FROM zone WHERE level = 2 AND lower(name) LIKE '%phú nhuận%' LIMIT 1;
  SELECT id INTO z_quan3     FROM zone WHERE level = 2 AND lower(name) IN ('quận 3', 'quan 3') LIMIT 1;
  SELECT id INTO z_govap     FROM zone WHERE level = 2 AND lower(name) LIKE '%gò vấp%' LIMIT 1;
  SELECT id INTO z_quan1     FROM zone WHERE level = 2 AND lower(name) IN ('quận 1', 'quan 1') LIMIT 1;

  IF z_binhthanh IS NULL OR z_phunhuan IS NULL OR z_quan3 IS NULL OR z_govap IS NULL OR z_quan1 IS NULL THEN
    RAISE EXCEPTION
      'Thiếu zone quận (Bình Thạnh/Phú Nhuận/Quận 3/Gò Vấp/Quận 1). Seed zone trước hoặc chỉnh tên trong script.';
  END IF;

  -- -------------------------------------------------------------------------
  -- 2) Catalog — reuse theo tên
  -- -------------------------------------------------------------------------
  SELECT id INTO cat_ac       FROM equipment_catalog WHERE name = 'Điều hòa' LIMIT 1;
  SELECT id INTO cat_fridge   FROM equipment_catalog WHERE name = 'Tủ lạnh' LIMIT 1;
  SELECT id INTO cat_washer   FROM equipment_catalog WHERE name = 'Máy giặt' LIMIT 1;
  SELECT id INTO cat_table    FROM equipment_catalog WHERE name = 'Bàn ăn' LIMIT 1;
  SELECT id INTO cat_bed      FROM equipment_catalog WHERE name = 'Giường' LIMIT 1;
  SELECT id INTO cat_wardrobe FROM equipment_catalog WHERE name = 'Tủ quần áo' LIMIT 1;
  SELECT id INTO cat_stove    FROM equipment_catalog WHERE name = 'Bếp từ' LIMIT 1;
  SELECT id INTO cat_heater   FROM equipment_catalog WHERE name = 'Nóng lạnh' LIMIT 1;
  SELECT id INTO cat_fan      FROM equipment_catalog WHERE name = 'Quạt' LIMIT 1;

  IF cat_ac IS NULL THEN
    INSERT INTO equipment_catalog (name, description, active)
    VALUES ('Điều hòa', 'Máy lạnh / điều hòa không khí', true)
    ON CONFLICT (name) DO NOTHING
    RETURNING id INTO cat_ac;
    IF cat_ac IS NULL THEN
      SELECT id INTO cat_ac FROM equipment_catalog WHERE name = 'Điều hòa';
    END IF;
  END IF;
  IF cat_fridge IS NULL THEN
    INSERT INTO equipment_catalog (name, description, active) VALUES ('Tủ lạnh', 'Tủ lạnh các loại', true)
    ON CONFLICT (name) DO NOTHING;
    SELECT id INTO cat_fridge FROM equipment_catalog WHERE name = 'Tủ lạnh';
  END IF;
  IF cat_washer IS NULL THEN
    INSERT INTO equipment_catalog (name, description, active) VALUES ('Máy giặt', 'Máy giặt', true)
    ON CONFLICT (name) DO NOTHING;
    SELECT id INTO cat_washer FROM equipment_catalog WHERE name = 'Máy giặt';
  END IF;
  IF cat_table IS NULL THEN
    INSERT INTO equipment_catalog (name, description, active) VALUES ('Bàn ăn', 'Bàn ăn', true)
    ON CONFLICT (name) DO NOTHING;
    SELECT id INTO cat_table FROM equipment_catalog WHERE name = 'Bàn ăn';
  END IF;
  IF cat_bed IS NULL THEN
    INSERT INTO equipment_catalog (name, description, active) VALUES ('Giường', 'Giường ngủ', true)
    ON CONFLICT (name) DO NOTHING;
    SELECT id INTO cat_bed FROM equipment_catalog WHERE name = 'Giường';
  END IF;
  IF cat_wardrobe IS NULL THEN
    INSERT INTO equipment_catalog (name, description, active) VALUES ('Tủ quần áo', 'Tủ QA', true)
    ON CONFLICT (name) DO NOTHING;
    SELECT id INTO cat_wardrobe FROM equipment_catalog WHERE name = 'Tủ quần áo';
  END IF;
  IF cat_stove IS NULL THEN
    INSERT INTO equipment_catalog (name, description, active) VALUES ('Bếp từ', 'Bếp từ', true)
    ON CONFLICT (name) DO NOTHING;
    SELECT id INTO cat_stove FROM equipment_catalog WHERE name = 'Bếp từ';
  END IF;
  IF cat_heater IS NULL THEN
    INSERT INTO equipment_catalog (name, description, active) VALUES ('Nóng lạnh', 'Máy nước nóng', true)
    ON CONFLICT (name) DO NOTHING;
    SELECT id INTO cat_heater FROM equipment_catalog WHERE name = 'Nóng lạnh';
  END IF;
  IF cat_fan IS NULL THEN
    INSERT INTO equipment_catalog (name, description, active) VALUES ('Quạt', 'Quạt điện', true)
    ON CONFLICT (name) DO NOTHING;
    SELECT id INTO cat_fan FROM equipment_catalog WHERE name = 'Quạt';
  END IF;

  -- -------------------------------------------------------------------------
  -- 3) Demo users (username/phone riêng — không đụng account production)
  -- -------------------------------------------------------------------------
  SELECT tablename INTO user_tbl
  FROM pg_tables WHERE schemaname = 'public' AND lower(tablename) = 'user' LIMIT 1;
  IF user_tbl IS NULL THEN
    RAISE EXCEPTION 'Không tìm thấy bảng User/user.';
  END IF;

  -- Helper: insert user nếu chưa có username; luôn resolve id theo username
  EXECUTE format(
    'INSERT INTO %I (id, username, password, phone_number, email, full_name, role, status, create_at, is_first_login)
     SELECT $1,$2,$3,$4,$5,$6,$7,$8,now(),false
     WHERE NOT EXISTS (SELECT 1 FROM %I WHERE username = $2 OR phone_number = $4)',
    user_tbl, user_tbl
  ) USING uid_owner, 'demo_owner', pw, '0988000001', 'demo_owner@slms.local', 'Chủ Nhà Demo Bảo Vệ', 'ROLE_OWNER', 'ACTIVE';

  EXECUTE format(
    'INSERT INTO %I (id, username, password, phone_number, email, full_name, role, status, create_at, is_first_login)
     SELECT $1,$2,$3,$4,$5,$6,$7,$8,now(),false
     WHERE NOT EXISTS (SELECT 1 FROM %I WHERE username = $2 OR phone_number = $4)',
    user_tbl, user_tbl
  ) USING uid_mgr1, 'demo_manager01', pw, '0988000011', 'demo_mgr01@slms.local', 'QLVH Demo Hùng', 'ROLE_MANAGER', 'ACTIVE';

  EXECUTE format(
    'INSERT INTO %I (id, username, password, phone_number, email, full_name, role, status, create_at, is_first_login)
     SELECT $1,$2,$3,$4,$5,$6,$7,$8,now(),false
     WHERE NOT EXISTS (SELECT 1 FROM %I WHERE username = $2 OR phone_number = $4)',
    user_tbl, user_tbl
  ) USING uid_mgr2, 'demo_manager02', pw, '0988000012', 'demo_mgr02@slms.local', 'QLVH Demo Mai', 'ROLE_MANAGER', 'ACTIVE';

  EXECUTE format(
    'INSERT INTO %I (id, username, password, phone_number, email, full_name, role, status, create_at, is_first_login)
     SELECT $1,$2,$3,$4,$5,$6,$7,$8,now(),false
     WHERE NOT EXISTS (SELECT 1 FROM %I WHERE username = $2 OR phone_number = $4)',
    user_tbl, user_tbl
  ) USING uid_t01, 'demo_tenant01', pw, '0988000101', 'demo_t01@slms.local', 'Nguyễn Văn An', 'ROLE_TENANT', 'ACTIVE';

  EXECUTE format(
    'INSERT INTO %I (id, username, password, phone_number, email, full_name, role, status, create_at, is_first_login)
     SELECT $1,$2,$3,$4,$5,$6,$7,$8,now(),false
     WHERE NOT EXISTS (SELECT 1 FROM %I WHERE username = $2 OR phone_number = $4)',
    user_tbl, user_tbl
  ) USING uid_t02, 'demo_tenant02', pw, '0988000102', 'demo_t02@slms.local', 'Trần Thị Bình', 'ROLE_TENANT', 'ACTIVE';

  EXECUTE format(
    'INSERT INTO %I (id, username, password, phone_number, email, full_name, role, status, create_at, is_first_login)
     SELECT $1,$2,$3,$4,$5,$6,$7,$8,now(),false
     WHERE NOT EXISTS (SELECT 1 FROM %I WHERE username = $2 OR phone_number = $4)',
    user_tbl, user_tbl
  ) USING uid_t03, 'demo_tenant03', pw, '0988000103', 'demo_t03@slms.local', 'Lê Minh Cường', 'ROLE_TENANT', 'ACTIVE';

  EXECUTE format(
    'INSERT INTO %I (id, username, password, phone_number, email, full_name, role, status, create_at, is_first_login)
     SELECT $1,$2,$3,$4,$5,$6,$7,$8,now(),false
     WHERE NOT EXISTS (SELECT 1 FROM %I WHERE username = $2 OR phone_number = $4)',
    user_tbl, user_tbl
  ) USING uid_t04, 'demo_tenant04', pw, '0988000104', 'demo_t04@slms.local', 'Phạm Thị Dung', 'ROLE_TENANT', 'ACTIVE';

  EXECUTE format(
    'INSERT INTO %I (id, username, password, phone_number, email, full_name, role, status, create_at, is_first_login)
     SELECT $1,$2,$3,$4,$5,$6,$7,$8,now(),false
     WHERE NOT EXISTS (SELECT 1 FROM %I WHERE username = $2 OR phone_number = $4)',
    user_tbl, user_tbl
  ) USING uid_t05, 'demo_tenant05', pw, '0988000105', 'demo_t05@slms.local', 'Hoàng Văn Em', 'ROLE_TENANT', 'ACTIVE';

  EXECUTE format(
    'INSERT INTO %I (id, username, password, phone_number, email, full_name, role, status, create_at, is_first_login)
     SELECT $1,$2,$3,$4,$5,$6,$7,$8,now(),false
     WHERE NOT EXISTS (SELECT 1 FROM %I WHERE username = $2 OR phone_number = $4)',
    user_tbl, user_tbl
  ) USING uid_t06, 'demo_tenant06', pw, '0988000106', 'demo_t06@slms.local', 'Võ Thị Phương', 'ROLE_TENANT', 'ACTIVE';

  EXECUTE format(
    'INSERT INTO %I (id, username, password, phone_number, email, full_name, role, status, create_at, is_first_login)
     SELECT $1,$2,$3,$4,$5,$6,$7,$8,now(),false
     WHERE NOT EXISTS (SELECT 1 FROM %I WHERE username = $2 OR phone_number = $4)',
    user_tbl, user_tbl
  ) USING uid_t07, 'demo_tenant07', pw, '0988000107', 'demo_t07@slms.local', 'Đặng Quốc Huy', 'ROLE_TENANT', 'ACTIVE';

  EXECUTE format('SELECT id FROM %I WHERE username = %L', user_tbl, 'demo_owner') INTO uid_owner;
  EXECUTE format('SELECT id FROM %I WHERE username = %L', user_tbl, 'demo_manager01') INTO uid_mgr1;
  EXECUTE format('SELECT id FROM %I WHERE username = %L', user_tbl, 'demo_manager02') INTO uid_mgr2;
  EXECUTE format('SELECT id FROM %I WHERE username = %L', user_tbl, 'demo_tenant01') INTO uid_t01;
  EXECUTE format('SELECT id FROM %I WHERE username = %L', user_tbl, 'demo_tenant02') INTO uid_t02;
  EXECUTE format('SELECT id FROM %I WHERE username = %L', user_tbl, 'demo_tenant03') INTO uid_t03;
  EXECUTE format('SELECT id FROM %I WHERE username = %L', user_tbl, 'demo_tenant04') INTO uid_t04;
  EXECUTE format('SELECT id FROM %I WHERE username = %L', user_tbl, 'demo_tenant05') INTO uid_t05;
  EXECUTE format('SELECT id FROM %I WHERE username = %L', user_tbl, 'demo_tenant06') INTO uid_t06;
  EXECUTE format('SELECT id FROM %I WHERE username = %L', user_tbl, 'demo_tenant07') INTO uid_t07;

  IF uid_owner IS NULL OR uid_mgr1 IS NULL OR uid_t01 IS NULL THEN
    RAISE EXCEPTION 'Không tạo/resolve được demo users (demo_owner / demo_manager01 / demo_tenant01).';
  END IF;

  INSERT INTO owner (user_id) VALUES (uid_owner) ON CONFLICT DO NOTHING;
  INSERT INTO operation_management (user_id, start_at) VALUES
    (uid_mgr1, now()), (uid_mgr2, now())
  ON CONFLICT DO NOTHING;
  INSERT INTO tenant (user_id, cccd, date_of_birth, permanent_address) VALUES
    (uid_t01, '079203008001', '1995-03-12', 'Demo — Q.1'),
    (uid_t02, '079203008002', '1998-07-22', 'Demo — Phú Nhuận'),
    (uid_t03, '079203008003', '2000-11-05', 'Demo — Hà Nội'),
    (uid_t04, '079203008004', '1996-01-30', 'Demo — Phú Nhuận'),
    (uid_t05, '079203008005', '1993-09-18', 'Demo — Gò Vấp'),
    (uid_t06, '079203008006', '1997-12-02', 'Demo — Bình Thạnh'),
    (uid_t07, '079203008007', '1994-06-25', 'Demo — Đồng Nai')
  ON CONFLICT DO NOTHING;

  INSERT INTO manager_zones (manager_id, zone_id) VALUES
    (uid_mgr1, z_binhthanh), (uid_mgr1, z_phunhuan), (uid_mgr1, z_quan3),
    (uid_mgr2, z_govap), (uid_mgr2, z_quan1)
  ON CONFLICT DO NOTHING;

  INSERT INTO zone_managers (zone_id, manager_id, assigned_at)
  VALUES (z_binhthanh, uid_mgr1, now())
  ON CONFLICT (zone_id) DO NOTHING;

  -- -------------------------------------------------------------------------
  -- 4) Properties (demo#101..106)
  -- -------------------------------------------------------------------------
  INSERT INTO properties (
    property_name, property_code, address, zone_id, area_size, length_m, width_m,
    total_floor, is_whole_house, has_renovation, total_rooms, status,
    operation_manager_id, descriptions, price, applied_price,
    electricity_unit_price, water_unit_price, electricity_customer_code, water_customer_code,
    deposit_months, service_fee, renovation_completed, manager_accepted_at
  ) VALUES (
    'DEMO#101 Full NT — Nhà phố Bình Thạnh',
    'demo#101', '124 Xô Viết Nghệ Tĩnh, Bình Thạnh (DEMO)',
    z_binhthanh, 85, 8.5, 10, 2, true, true, 3, 'RENTED',
    uid_mgr1, 'DEMO — Full nội thất phục vụ bảo vệ capstone.',
    14000000, 14000000, 3500, 18000, 'DEMO-PE-101', 'DEMO-NW-101',
    2, 200000, true, '2024-08-01 09:00:00'
  ) RETURNING id INTO p101;

  INSERT INTO properties (
    property_name, property_code, address, zone_id, area_size, length_m, width_m,
    total_floor, is_whole_house, has_renovation, total_rooms, status,
    operation_manager_id, descriptions, price, applied_price,
    electricity_unit_price, water_unit_price, electricity_customer_code, water_customer_code,
    deposit_months, service_fee, renovation_completed, manager_accepted_at
  ) VALUES (
    'DEMO#102 NT cơ bản — Phú Nhuận',
    'demo#102', '56 Hoàng Văn Thụ, Phú Nhuận (DEMO)',
    z_phunhuan, 55, 7, 8, 1, true, false, 2, 'RENTED',
    uid_mgr1, 'DEMO — NT cơ bản: giường, tủ, quạt, nóng lạnh.',
    9000000, 9000000, 3500, 18000, 'DEMO-PE-102', 'DEMO-NW-102',
    1, 150000, true, '2025-07-01 10:00:00'
  ) RETURNING id INTO p102;

  INSERT INTO properties (
    property_name, property_code, address, zone_id, area_size, length_m, width_m,
    total_floor, is_whole_house, has_renovation, total_rooms, status,
    operation_manager_id, descriptions, price, applied_price,
    electricity_unit_price, water_unit_price, electricity_customer_code, water_customer_code,
    deposit_months, service_fee, renovation_completed, manager_accepted_at
  ) VALUES (
    'DEMO#103 Không NT — Quận 3',
    'demo#103', '18 Nam Kỳ Khởi Nghĩa, Quận 3 (DEMO)',
    z_quan3, 70, 7, 10, 2, true, false, 3, 'RENTED',
    uid_mgr1, 'DEMO — Nhà trống không nội thất.',
    7500000, 7500000, 3500, 18000, 'DEMO-PE-103', 'DEMO-NW-103',
    1, 100000, true, '2026-06-15 09:00:00'
  ) RETURNING id INTO p103;

  INSERT INTO properties (
    property_name, property_code, address, zone_id, area_size, length_m, width_m,
    total_floor, is_whole_house, has_renovation, total_rooms, status,
    operation_manager_id, descriptions, price, applied_price,
    electricity_unit_price, water_unit_price, electricity_customer_code, water_customer_code,
    deposit_months, service_fee, renovation_completed, manager_accepted_at
  ) VALUES (
    'DEMO#104 Theo phòng Full NT — Gò Vấp',
    'demo#104', '230 Quang Trung, Gò Vấp (DEMO)',
    z_govap, 120, 10, 12, 3, false, true, 3, 'RENTED',
    uid_mgr2, 'DEMO — Theo phòng full NT.',
    NULL, NULL, 3500, 18000, 'DEMO-PE-104', 'DEMO-NW-104',
    1, 50000, true, '2025-01-10 08:00:00'
  ) RETURNING id INTO p104;

  INSERT INTO properties (
    property_name, property_code, address, zone_id, area_size, length_m, width_m,
    total_floor, is_whole_house, has_renovation, total_rooms, status,
    operation_manager_id, descriptions, price, applied_price,
    electricity_unit_price, water_unit_price, electricity_customer_code, water_customer_code,
    deposit_months, service_fee, renovation_completed, manager_accepted_at
  ) VALUES (
    'DEMO#105 Theo phòng Không NT — Quận 1',
    'demo#105', '9 Nguyễn Huệ, Quận 1 (DEMO)',
    z_quan1, 90, 9, 10, 2, false, false, 2, 'RENTED',
    uid_mgr2, 'DEMO — Phòng trống không NT.',
    NULL, NULL, 3500, 18000, 'DEMO-PE-105', 'DEMO-NW-105',
    1, 80000, true, '2026-05-01 08:00:00'
  ) RETURNING id INTO p105;

  INSERT INTO properties (
    property_name, property_code, address, zone_id, area_size, length_m, width_m,
    total_floor, is_whole_house, has_renovation, total_rooms, status,
    operation_manager_id, descriptions, price, applied_price,
    electricity_unit_price, water_unit_price, electricity_customer_code, water_customer_code,
    deposit_months, service_fee, renovation_completed, manager_accepted_at
  ) VALUES (
    'DEMO#106 Full NT — Lịch sử nhiều khách',
    'demo#106', '88 Bạch Đằng, Bình Thạnh (DEMO)',
    z_binhthanh, 95, 9.5, 10, 2, true, true, 3, 'RENTED',
    uid_mgr1, 'DEMO — 3 thế hệ khách thuê (Phương → Em → Huy).',
    15000000, 15000000, 3500, 18000, 'DEMO-PE-106', 'DEMO-NW-106',
    2, 250000, true, '2023-01-05 09:00:00'
  ) RETURNING id INTO p106;

  -- inbound contracts (PnL)
  INSERT INTO inbound_contracts (property_id, contract_code, owner_name, total_rent_amount, start_date, end_date, status) VALUES
    (p101, 'DEMO-IB-101', 'Chủ gốc Demo A', 336000000, '2024-08-01', '2026-07-31', 'ACTIVE'),
    (p102, 'DEMO-IB-102', 'Chủ gốc Demo B', 162000000, '2025-07-01', '2026-12-31', 'ACTIVE'),
    (p103, 'DEMO-IB-103', 'Chủ gốc Demo C', 135000000, '2026-06-01', '2027-11-30', 'ACTIVE'),
    (p104, 'DEMO-IB-104', 'Chủ gốc Demo D', 216000000, '2025-01-01', '2026-12-31', 'ACTIVE'),
    (p105, 'DEMO-IB-105', 'Chủ gốc Demo E', 132000000, '2026-05-01', '2027-04-30', 'ACTIVE'),
    (p106, 'DEMO-IB-106', 'Chủ gốc Demo F', 540000000, '2023-01-01', '2026-12-31', 'ACTIVE');

  -- -------------------------------------------------------------------------
  -- 5) Rooms
  -- -------------------------------------------------------------------------
  INSERT INTO rooms (property_id, room_number, floor, price, applied_price, deposit, area, max_occupants,
                     structure_description, room_type, status)
  VALUES (p101, 'NGUYEN_CAN', 1, 14000000, 14000000, 28000000, 85, 4, '3PN full NT', 'WHOLE_HOUSE', 'RENTED')
  RETURNING id INTO r101;

  INSERT INTO rooms (property_id, room_number, floor, price, applied_price, deposit, area, max_occupants,
                     structure_description, room_type, status)
  VALUES (p102, 'NGUYEN_CAN', 1, 9000000, 9000000, 9000000, 55, 3, '2PN NT cơ bản', 'WHOLE_HOUSE', 'RENTED')
  RETURNING id INTO r102;

  INSERT INTO rooms (property_id, room_number, floor, price, applied_price, deposit, area, max_occupants,
                     structure_description, room_type, status)
  VALUES (p103, 'NGUYEN_CAN', 1, 7500000, 7500000, 7500000, 70, 4, '3PN không NT', 'WHOLE_HOUSE', 'RENTED')
  RETURNING id INTO r103;

  INSERT INTO rooms (property_id, room_number, floor, price, applied_price, deposit, area, max_occupants,
                     structure_description, room_type, status)
  VALUES (p106, 'NGUYEN_CAN', 1, 15000000, 15000000, 30000000, 95, 4, '3PN full NT', 'WHOLE_HOUSE', 'RENTED')
  RETURNING id INTO r106;

  INSERT INTO rooms (property_id, room_number, floor, price, applied_price, deposit, area, max_occupants,
                     room_type, status, electric_meter_code, water_meter_code)
  VALUES (p104, 'P101', 1, 4500000, 4500000, 4500000, 22, 2, 'INDIVIDUAL_ROOM', 'RENTED', 'DEMO-E104-P101', 'DEMO-W104-P101')
  RETURNING id INTO r104_p101;

  INSERT INTO rooms (property_id, room_number, floor, price, applied_price, deposit, area, max_occupants,
                     room_type, status, electric_meter_code, water_meter_code)
  VALUES (p104, 'P102', 1, 4200000, 4200000, 4200000, 20, 2, 'INDIVIDUAL_ROOM', 'AVAILABLE', 'DEMO-E104-P102', 'DEMO-W104-P102')
  RETURNING id INTO r104_p102;

  INSERT INTO rooms (property_id, room_number, floor, price, applied_price, deposit, area, max_occupants,
                     room_type, status, electric_meter_code, water_meter_code)
  VALUES (p104, 'P103', 2, 4800000, 4800000, 4800000, 24, 2, 'INDIVIDUAL_ROOM', 'AVAILABLE', 'DEMO-E104-P103', 'DEMO-W104-P103')
  RETURNING id INTO r104_p103;

  INSERT INTO rooms (property_id, room_number, floor, price, applied_price, deposit, area, max_occupants,
                     room_type, status, electric_meter_code, water_meter_code)
  VALUES (p105, 'R201', 2, 5500000, 5500000, 5500000, 25, 2, 'INDIVIDUAL_ROOM', 'RENTED', 'DEMO-E105-R201', 'DEMO-W105-R201')
  RETURNING id INTO r105_201;

  INSERT INTO rooms (property_id, room_number, floor, price, applied_price, deposit, area, max_occupants,
                     room_type, status, electric_meter_code, water_meter_code)
  VALUES (p105, 'R202', 2, 5200000, 5200000, 5200000, 23, 2, 'INDIVIDUAL_ROOM', 'AVAILABLE', 'DEMO-E105-R202', 'DEMO-W105-R202')
  RETURNING id INTO r105_202;

  -- -------------------------------------------------------------------------
  -- 6) Equipments
  -- -------------------------------------------------------------------------
  INSERT INTO equipments (property_id, room_id, catalog_id, house_area, source, status, price, equipment_name, category,
                          installation_date, warranty_months, warranty_start_date, warranty_end_date,
                          maintenance_count, last_maintenance_date, qr_code, penalty_fee)
  VALUES
    (p101, r101, cat_ac, 'BEDROOM', 'INITIAL_HANDOVER', 'GOOD', 12000000, 'Máy lạnh Daikin PN', 'APPLIANCE',
     '2024-08-01', 24, '2024-08-01', '2026-08-01', 2, '2026-03-10 14:00:00', 'DEMO-EQ-101-AC-01', 500000)
  RETURNING id INTO eq_101_ac;

  INSERT INTO equipments (property_id, room_id, catalog_id, house_area, source, status, price, equipment_name, category,
                          installation_date, warranty_months, warranty_start_date, warranty_end_date,
                          maintenance_count, last_maintenance_date, qr_code, penalty_fee)
  VALUES
    (p101, r101, cat_fridge, 'KITCHEN', 'INITIAL_HANDOVER', 'GOOD', 8000000, 'Tủ lạnh Toshiba', 'APPLIANCE',
     '2024-08-01', 24, '2024-08-01', '2026-08-01', 1, '2025-11-20 10:00:00', 'DEMO-EQ-101-FR-01', 400000)
  RETURNING id INTO eq_101_fr;

  INSERT INTO equipments (property_id, room_id, catalog_id, house_area, source, status, price, equipment_name, category,
                          installation_date, qr_code, penalty_fee)
  VALUES
    (p101, r101, cat_washer, 'OTHER', 'INITIAL_HANDOVER', 'GOOD', 7000000, 'Máy giặt LG', 'APPLIANCE',
     '2024-08-01', 'DEMO-EQ-101-WM-01', 400000),
    (p101, r101, cat_bed, 'BEDROOM', 'INITIAL_HANDOVER', 'GOOD', 3500000, 'Giường 1m6', 'FURNITURE',
     '2024-08-01', 'DEMO-EQ-101-BD-01', 200000),
    (p101, r101, cat_wardrobe, 'BEDROOM', 'INITIAL_HANDOVER', 'GOOD', 2500000, 'Tủ QA 3 cánh', 'FURNITURE',
     '2024-08-01', 'DEMO-EQ-101-WD-01', 150000),
    (p101, r101, cat_table, 'LIVING_ROOM', 'INITIAL_HANDOVER', 'GOOD', 4000000, 'Bàn ăn 4 ghế', 'FURNITURE',
     '2024-08-01', 'DEMO-EQ-101-TB-01', 200000),
    (p101, r101, cat_stove, 'KITCHEN', 'INITIAL_HANDOVER', 'GOOD', 3000000, 'Bếp từ đôi', 'APPLIANCE',
     '2024-08-01', 'DEMO-EQ-101-ST-01', 250000),
    (p101, r101, cat_ac, 'LIVING_ROOM', 'INITIAL_HANDOVER', 'GOOD', 10000000, 'Máy lạnh PK', 'APPLIANCE',
     '2024-08-01', 'DEMO-EQ-101-AC-02', 500000);

  INSERT INTO equipments (property_id, room_id, catalog_id, house_area, source, status, price, equipment_name, category,
                          installation_date, maintenance_count, last_maintenance_date, qr_code, penalty_fee)
  VALUES
    (p101, r101, cat_heater, 'BATHROOM', 'INITIAL_HANDOVER', 'GOOD', 2500000, 'Nóng lạnh Ariston', 'APPLIANCE',
     '2024-08-01', 1, '2026-01-15 09:30:00', 'DEMO-EQ-101-WH-01', 200000)
  RETURNING id INTO eq_101_wh;

  -- #102 basic
  INSERT INTO equipments (property_id, room_id, catalog_id, house_area, source, status, price, equipment_name, category,
                          installation_date, qr_code, penalty_fee)
  VALUES
    (p102, r102, cat_bed, 'BEDROOM', 'INITIAL_HANDOVER', 'GOOD', 2800000, 'Giường sắt', 'FURNITURE',
     '2025-07-01', 'DEMO-EQ-102-BD-01', 150000),
    (p102, r102, cat_wardrobe, 'BEDROOM', 'INITIAL_HANDOVER', 'GOOD', 1800000, 'Tủ QA 2 cánh', 'FURNITURE',
     '2025-07-01', 'DEMO-EQ-102-WD-01', 100000);

  INSERT INTO equipments (property_id, room_id, catalog_id, house_area, source, status, price, equipment_name, category,
                          installation_date, maintenance_count, last_maintenance_date, qr_code, penalty_fee)
  VALUES
    (p102, r102, cat_fan, 'BEDROOM', 'INITIAL_HANDOVER', 'GOOD', 450000, 'Quạt đứng', 'APPLIANCE',
     '2025-07-01', 1, '2026-06-05 16:00:00', 'DEMO-EQ-102-FN-01', 50000)
  RETURNING id INTO eq_102_fn;

  INSERT INTO equipments (property_id, room_id, catalog_id, house_area, source, status, price, equipment_name, category,
                          installation_date, maintenance_count, last_maintenance_date, qr_code, penalty_fee)
  VALUES
    (p102, r102, cat_heater, 'BATHROOM', 'INITIAL_HANDOVER', 'GOOD', 2200000, 'Nóng lạnh Rossi', 'APPLIANCE',
     '2025-07-01', 1, '2026-04-18 11:00:00', 'DEMO-EQ-102-WH-01', 200000)
  RETURNING id INTO eq_102_wh;

  -- #104 rooms
  INSERT INTO equipments (property_id, room_id, catalog_id, house_area, source, status, price, equipment_name, category,
                          installation_date, maintenance_count, qr_code, penalty_fee)
  VALUES
    (p104, r104_p101, cat_ac, 'BEDROOM', 'INITIAL_HANDOVER', 'GOOD', 8500000, 'Máy lạnh P101', 'APPLIANCE',
     '2025-01-10', 1, 'DEMO-EQ-104-P101-AC', 400000)
  RETURNING id INTO eq_104_ac;

  INSERT INTO equipments (property_id, room_id, catalog_id, house_area, source, status, price, equipment_name, category,
                          installation_date, qr_code, penalty_fee)
  VALUES
    (p104, r104_p101, cat_bed, 'BEDROOM', 'INITIAL_HANDOVER', 'GOOD', 2500000, 'Giường P101', 'FURNITURE',
     '2025-01-10', 'DEMO-EQ-104-P101-BD', 150000),
    (p104, r104_p101, cat_wardrobe, 'BEDROOM', 'INITIAL_HANDOVER', 'GOOD', 1500000, 'Tủ P101', 'FURNITURE',
     '2025-01-10', 'DEMO-EQ-104-P101-WD', 100000),
    (p104, r104_p101, cat_heater, 'BATHROOM', 'INITIAL_HANDOVER', 'GOOD', 2000000, 'Nóng lạnh P101', 'APPLIANCE',
     '2025-01-10', 'DEMO-EQ-104-P101-WH', 150000),
    (p104, r104_p102, cat_ac, 'BEDROOM', 'INITIAL_HANDOVER', 'NEW', 8500000, 'Máy lạnh P102', 'APPLIANCE',
     '2025-01-10', 'DEMO-EQ-104-P102-AC', 400000),
    (p104, r104_p102, cat_bed, 'BEDROOM', 'INITIAL_HANDOVER', 'NEW', 2500000, 'Giường P102', 'FURNITURE',
     '2025-01-10', 'DEMO-EQ-104-P102-BD', 150000),
    (p104, r104_p103, cat_ac, 'BEDROOM', 'INITIAL_HANDOVER', 'NEW', 9000000, 'Máy lạnh P103', 'APPLIANCE',
     '2025-01-10', 'DEMO-EQ-104-P103-AC', 400000),
    (p104, r104_p103, cat_bed, 'BEDROOM', 'INITIAL_HANDOVER', 'NEW', 2800000, 'Giường P103', 'FURNITURE',
     '2025-01-10', 'DEMO-EQ-104-P103-BD', 150000);

  -- #106 full
  INSERT INTO equipments (property_id, room_id, catalog_id, house_area, source, status, price, equipment_name, category,
                          installation_date, maintenance_count, last_maintenance_date, qr_code, penalty_fee)
  VALUES
    (p106, r106, cat_ac, 'BEDROOM', 'INITIAL_HANDOVER', 'GOOD', 11000000, 'Máy lạnh #106', 'APPLIANCE',
     '2023-01-05', 3, '2026-02-01 13:00:00', 'DEMO-EQ-106-AC-01', 500000)
  RETURNING id INTO eq_106_ac;

  INSERT INTO equipments (property_id, room_id, catalog_id, house_area, source, status, price, equipment_name, category,
                          installation_date, maintenance_count, last_maintenance_date, qr_code, penalty_fee)
  VALUES
    (p106, r106, cat_washer, 'OTHER', 'INITIAL_HANDOVER', 'GOOD', 6500000, 'Máy giặt #106', 'APPLIANCE',
     '2023-01-05', 1, '2024-09-01 15:00:00', 'DEMO-EQ-106-WM-01', 400000)
  RETURNING id INTO eq_106_wm;

  INSERT INTO equipments (property_id, room_id, catalog_id, house_area, source, status, price, equipment_name, category,
                          installation_date, qr_code, penalty_fee)
  VALUES
    (p106, r106, cat_fridge, 'KITCHEN', 'INITIAL_HANDOVER', 'GOOD', 7500000, 'Tủ lạnh #106', 'APPLIANCE',
     '2023-01-05', 'DEMO-EQ-106-FR-01', 400000),
    (p106, r106, cat_bed, 'BEDROOM', 'INITIAL_HANDOVER', 'GOOD', 3000000, 'Giường #106', 'FURNITURE',
     '2023-01-05', 'DEMO-EQ-106-BD-01', 200000),
    (p106, r106, cat_wardrobe, 'BEDROOM', 'INITIAL_HANDOVER', 'GOOD', 2200000, 'Tủ QA #106', 'FURNITURE',
     '2023-01-05', 'DEMO-EQ-106-WD-01', 150000),
    (p106, r106, cat_stove, 'KITCHEN', 'INITIAL_HANDOVER', 'GOOD', 2800000, 'Bếp từ #106', 'APPLIANCE',
     '2023-01-05', 'DEMO-EQ-106-ST-01', 250000),
    (p106, r106, cat_heater, 'BATHROOM', 'INITIAL_HANDOVER', 'GOOD', 2300000, 'Nóng lạnh #106', 'APPLIANCE',
     '2023-01-05', 'DEMO-EQ-106-WH-01', 200000);

  -- -------------------------------------------------------------------------
  -- 7) Tenant contracts
  -- -------------------------------------------------------------------------
  INSERT INTO tenant_contracts (
    tenant_user_id, property_id, room_id, contract_code, rent_amount, base_rent_amount,
    rent_escalation_type, deposit, deposit_months, move_in_date, start_date, end_date,
    payment_status, deposit_paid_at, deposit_method, paid_at, activated_at,
    assigned_manager_id, onboarded_by_manager_id, onboarded_at, status,
    initial_electric_reading, initial_water_reading, room_condition_note
  ) VALUES (
    uid_t01, p101, NULL, 'DEMO-TC-101-AN',
    14000000, 14000000, 'NONE', 28000000, 2,
    '2024-10-01', '2024-10-01', '2026-10-01',
    'PAID', '2024-09-28 10:00:00', 'CASH', '2024-09-28 10:00:00', '2024-10-01 09:00:00',
    uid_mgr1, uid_mgr1, '2024-10-01 09:00:00', 'ACTIVE',
    1250, 85, 'DEMO — Full NT, ở ~2 năm'
  ) RETURNING id INTO c_an;

  INSERT INTO tenant_contracts (
    tenant_user_id, property_id, room_id, contract_code, rent_amount, base_rent_amount,
    rent_escalation_type, deposit, deposit_months, move_in_date, start_date, end_date,
    payment_status, deposit_paid_at, deposit_method, paid_at, activated_at,
    assigned_manager_id, onboarded_by_manager_id, onboarded_at, status,
    initial_electric_reading, initial_water_reading, room_condition_note
  ) VALUES (
    uid_t02, p102, NULL, 'DEMO-TC-102-BINH',
    9000000, 9000000, 'NONE', 9000000, 1,
    '2025-09-15', '2025-09-15', '2026-09-15',
    'PAID', '2025-09-12 11:00:00', 'PAYOS', '2025-09-12 11:00:00', '2025-09-15 10:00:00',
    uid_mgr1, uid_mgr1, '2025-09-15 10:00:00', 'ACTIVE',
    320, 40, 'DEMO — NT cơ bản, ở ~1 năm'
  ) RETURNING id INTO c_binh;

  INSERT INTO tenant_contracts (
    tenant_user_id, property_id, room_id, contract_code, rent_amount, base_rent_amount,
    rent_escalation_type, deposit, deposit_months, move_in_date, start_date, end_date,
    payment_status, deposit_paid_at, deposit_method, paid_at, activated_at,
    assigned_manager_id, onboarded_by_manager_id, onboarded_at, status,
    initial_electric_reading, initial_water_reading, room_condition_note
  ) VALUES (
    uid_t03, p103, NULL, 'DEMO-TC-103-CUONG',
    7500000, 7500000, 'NONE', 7500000, 1,
    '2026-08-20', '2026-08-20', '2027-08-19',
    'PAID', '2026-08-18 14:00:00', 'PAYOS', '2026-08-18 14:00:00', '2026-08-20 09:00:00',
    uid_mgr1, uid_mgr1, '2026-08-20 09:00:00', 'ACTIVE',
    10, 2, 'DEMO — không NT, khách mới'
  ) RETURNING id INTO c_cuong;

  INSERT INTO tenant_contracts (
    tenant_user_id, property_id, room_id, contract_code, rent_amount, base_rent_amount,
    rent_escalation_type, deposit, deposit_months, move_in_date, start_date, end_date,
    payment_status, deposit_paid_at, deposit_method, paid_at, activated_at,
    assigned_manager_id, onboarded_by_manager_id, onboarded_at, status,
    initial_electric_reading, initial_water_reading, room_condition_note
  ) VALUES (
    uid_t04, p104, r104_p101, 'DEMO-TC-104-DUNG',
    4500000, 4500000, 'NONE', 4500000, 1,
    '2025-10-01', '2025-10-01', '2026-09-25',
    'PAID', '2025-09-28 16:00:00', 'CASH', '2025-09-28 16:00:00', '2025-10-01 08:00:00',
    uid_mgr2, uid_mgr2, '2025-10-01 08:00:00', 'ACTIVE',
    180, 25, 'DEMO — sắp hết HĐ 25/09/2026'
  ) RETURNING id INTO c_dung;

  INSERT INTO tenant_contracts (
    tenant_user_id, property_id, room_id, contract_code, rent_amount, base_rent_amount,
    rent_escalation_type, deposit, deposit_months, move_in_date, start_date, end_date,
    payment_status, deposit_paid_at, deposit_method, paid_at, activated_at,
    assigned_manager_id, onboarded_by_manager_id, onboarded_at, status,
    terminated_at, termination_type, termination_reason,
    initial_electric_reading, initial_water_reading, room_condition_note
  ) VALUES (
    uid_t05, p106, NULL, 'DEMO-TC-106-EM-OLD',
    14500000, 14500000, 'NONE', 29000000, 2,
    '2024-07-01', '2024-07-01', '2026-06-30',
    'PAID', '2024-06-28 10:00:00', 'CASH', '2024-06-28 10:00:00', '2024-07-01 09:00:00',
    uid_mgr1, uid_mgr1, '2024-07-01 09:00:00', 'EXPIRED',
    '2026-06-30 17:00:00', 'OTHER', 'Hết hạn — chuyển thuê nhà khác',
    900, 60, 'DEMO — HĐ cũ tại #106'
  ) RETURNING id INTO c_em_old;

  INSERT INTO tenant_contracts (
    tenant_user_id, property_id, room_id, contract_code, rent_amount, base_rent_amount,
    rent_escalation_type, deposit, deposit_months, move_in_date, start_date, end_date,
    payment_status, deposit_paid_at, deposit_method, paid_at, activated_at,
    assigned_manager_id, onboarded_by_manager_id, onboarded_at, status,
    initial_electric_reading, initial_water_reading, room_condition_note
  ) VALUES (
    uid_t05, p105, r105_201, 'DEMO-TC-105-EM-NEW',
    5500000, 5500000, 'NONE', 5500000, 1,
    '2026-07-01', '2026-07-01', '2027-06-30',
    'PAID', '2026-06-28 15:00:00', 'PAYOS', '2026-06-28 15:00:00', '2026-07-01 10:00:00',
    uid_mgr2, uid_mgr2, '2026-07-01 10:00:00', 'ACTIVE',
    5, 1, 'DEMO — thuê lại sau khi hết HĐ #106'
  ) RETURNING id INTO c_em_new;

  INSERT INTO tenant_contracts (
    tenant_user_id, property_id, room_id, contract_code, rent_amount, base_rent_amount,
    rent_escalation_type, deposit, deposit_months, move_in_date, start_date, end_date,
    payment_status, deposit_paid_at, deposit_method, paid_at, activated_at,
    assigned_manager_id, onboarded_by_manager_id, onboarded_at, status,
    terminated_at, termination_type, termination_reason,
    initial_electric_reading, initial_water_reading, room_condition_note
  ) VALUES (
    uid_t06, p106, NULL, 'DEMO-TC-106-PHUONG',
    13000000, 13000000, 'NONE', 26000000, 2,
    '2023-02-01', '2023-02-01', '2024-06-30',
    'PAID', '2023-01-28 10:00:00', 'CASH', '2023-01-28 10:00:00', '2023-02-01 09:00:00',
    uid_mgr1, uid_mgr1, '2023-02-01 09:00:00', 'EXPIRED',
    '2024-06-30 16:00:00', 'OTHER', 'Hết hạn — trả nhà',
    50, 5, 'DEMO — khách đầu tiên #106'
  ) RETURNING id INTO c_phuong;

  INSERT INTO tenant_contracts (
    tenant_user_id, property_id, room_id, contract_code, rent_amount, base_rent_amount,
    rent_escalation_type, deposit, deposit_months, move_in_date, start_date, end_date,
    payment_status, deposit_paid_at, deposit_method, paid_at, activated_at,
    assigned_manager_id, onboarded_by_manager_id, onboarded_at, status,
    initial_electric_reading, initial_water_reading, room_condition_note
  ) VALUES (
    uid_t07, p106, NULL, 'DEMO-TC-106-HUY',
    15000000, 15000000, 'NONE', 30000000, 2,
    '2026-07-05', '2026-07-05', '2027-07-04',
    'PAID', '2026-07-02 11:00:00', 'PAYOS', '2026-07-02 11:00:00', '2026-07-05 09:00:00',
    uid_mgr1, uid_mgr1, '2026-07-05 09:00:00', 'ACTIVE',
    2100, 140, 'DEMO — khách hiện tại #106'
  ) RETURNING id INTO c_huy;

  INSERT INTO household_members (tenant_contract_id, full_name, relation, phone) VALUES
    (c_an, 'Nguyễn Thị Hoa', 'Vợ', '0911111101'),
    (c_binh, 'Trần Văn Nam', 'Chồng', '0911111102');

  -- -------------------------------------------------------------------------
  -- 8) Monthly invoices + payments (chỉ cho các HĐ DEMO)
  -- -------------------------------------------------------------------------
  FOR contract_id, tenant_uid, rent, prop_name, room_no, start_d, end_d, contract_status,
      elec_price, water_price, svc_fee IN
    SELECT tc.id, tc.tenant_user_id, tc.rent_amount, p.property_name,
           COALESCE(rm.room_number, 'NGUYEN_CAN'),
           tc.start_date, tc.end_date, tc.status,
           p.electricity_unit_price, p.water_unit_price, p.service_fee
    FROM tenant_contracts tc
    JOIN properties p ON p.id = tc.property_id
    LEFT JOIN rooms rm ON rm.id = tc.room_id
    WHERE tc.contract_code LIKE 'DEMO-TC-%'
  LOOP
    ym := date_trunc('month', start_d)::date;
    end_ym := LEAST(date_trunc('month', COALESCE(end_d, DATE '2026-09-01'))::date, DATE '2026-09-01');

    WHILE ym <= end_ym LOOP
      inv_code := 'DEMO-RENT-' || contract_id || '-' || to_char(ym, 'YYYY-MM');
      IF EXISTS (SELECT 1 FROM tenant_invoices WHERE code = inv_code) THEN
        ym := (ym + INTERVAL '1 month')::date;
        CONTINUE;
      END IF;

      due := (ym + INTERVAL '1 month' - INTERVAL '1 day')::date;
      created_ts := (ym + INTERVAL '1 day')::timestamp + TIME '08:00';
      cycle := CASE WHEN ym = date_trunc('month', start_d)::date THEN 'FIRST' ELSE 'REGULAR' END;

      IF ym < DATE '2026-09-01' THEN
        st := 'PAID';
        paid_ts := (due + INTERVAL '2 days')::timestamp + TIME '19:30';
      ELSIF contract_id = c_dung THEN
        st := 'PENDING';
        paid_ts := NULL;
      ELSIF contract_status = 'EXPIRED' THEN
        st := 'PAID';
        paid_ts := (due + INTERVAL '1 day')::timestamp + TIME '18:00';
      ELSE
        st := 'PENDING';
        paid_ts := NULL;
      END IF;

      INSERT INTO tenant_invoices (
        code, tenant_user_id, tenant_contract_id, invoice_type, cycle_type,
        property_name, room_number, billing_month, billing_year, billing_period,
        note, total_amount, late_fee, grand_total, status, due_date,
        created_at, paid_at, payment_method, transaction_id, auto_issued
      ) VALUES (
        inv_code, tenant_uid, contract_id, 'RENT', cycle,
        prop_name, room_no, EXTRACT(MONTH FROM ym)::int, EXTRACT(YEAR FROM ym)::int,
        'Tiền nhà tháng ' || EXTRACT(MONTH FROM ym)::int || '/' || EXTRACT(YEAR FROM ym)::int,
        'DEMO seed', rent, 0, rent, st, due,
        created_ts, paid_ts,
        CASE WHEN st = 'PAID' THEN 'PAYOS' END,
        CASE WHEN st = 'PAID' THEN 'DEMO-TXN-R-' || contract_id || '-' || to_char(ym, 'YYYYMM') END,
        true
      ) RETURNING id INTO inv_id;

      IF st = 'PAID' THEN
        INSERT INTO tenant_payments (
          tenant_invoice_id, tenant_user_id, invoice_code, invoice_type,
          amount, method, paid_at, transaction_id, property_name, room_number, collection_mode
        ) VALUES (
          inv_id, tenant_uid, inv_code, 'RENT', rent, 'PAYOS', paid_ts,
          'DEMO-TXN-R-' || contract_id || '-' || to_char(ym, 'YYYYMM'),
          prop_name, room_no, 'ONLINE'
        );
      END IF;

      IF ym < DATE '2026-09-01'
         AND ym >= (date_trunc('month', start_d)::date + INTERVAL '1 month') THEN
        elec_used := 80 + (EXTRACT(MONTH FROM ym)::int * 3) % 40;
        water_used := 8 + (EXTRACT(MONTH FROM ym)::int) % 6;
        elec_amt := round(elec_used * COALESCE(elec_price, 3500), 0);
        water_amt := round(water_used * COALESCE(water_price, 18000), 0);

        inv_code := 'DEMO-ELEC-' || contract_id || '-' || to_char(ym, 'YYYY-MM');
        INSERT INTO tenant_invoices (
          code, tenant_user_id, tenant_contract_id, invoice_type, cycle_type,
          property_name, room_number, billing_month, billing_year, billing_period,
          total_amount, late_fee, grand_total, status, due_date,
          created_at, paid_at, payment_method, transaction_id,
          kwh_used, electricity_rate, auto_issued
        ) VALUES (
          inv_code, tenant_uid, contract_id, 'ELECTRICITY', 'REGULAR',
          prop_name, room_no, EXTRACT(MONTH FROM ym)::int, EXTRACT(YEAR FROM ym)::int,
          'Tiền điện tháng ' || EXTRACT(MONTH FROM ym)::int || '/' || EXTRACT(YEAR FROM ym)::int,
          elec_amt, 0, elec_amt, 'PAID', due,
          created_ts + INTERVAL '1 hour', paid_ts + INTERVAL '1 hour', 'PAYOS',
          'DEMO-TXN-E-' || contract_id || '-' || to_char(ym, 'YYYYMM'),
          elec_used, COALESCE(elec_price, 3500), true
        ) RETURNING id INTO inv_id;

        INSERT INTO tenant_payments (
          tenant_invoice_id, tenant_user_id, invoice_code, invoice_type,
          amount, method, paid_at, transaction_id, property_name, room_number, collection_mode
        ) VALUES (
          inv_id, tenant_uid, inv_code, 'ELECTRICITY', elec_amt, 'PAYOS', paid_ts + INTERVAL '1 hour',
          'DEMO-TXN-E-' || contract_id || '-' || to_char(ym, 'YYYYMM'), prop_name, room_no, 'ONLINE'
        );

        inv_code := 'DEMO-WATER-' || contract_id || '-' || to_char(ym, 'YYYY-MM');
        INSERT INTO tenant_invoices (
          code, tenant_user_id, tenant_contract_id, invoice_type, cycle_type,
          property_name, room_number, billing_month, billing_year, billing_period,
          total_amount, late_fee, grand_total, status, due_date,
          created_at, paid_at, payment_method, transaction_id,
          m3_used, water_rate, auto_issued
        ) VALUES (
          inv_code, tenant_uid, contract_id, 'WATER', 'REGULAR',
          prop_name, room_no, EXTRACT(MONTH FROM ym)::int, EXTRACT(YEAR FROM ym)::int,
          'Tiền nước tháng ' || EXTRACT(MONTH FROM ym)::int || '/' || EXTRACT(YEAR FROM ym)::int,
          water_amt, 0, water_amt, 'PAID', due,
          created_ts + INTERVAL '2 hour', paid_ts + INTERVAL '2 hour', 'PAYOS',
          'DEMO-TXN-W-' || contract_id || '-' || to_char(ym, 'YYYYMM'),
          water_used, COALESCE(water_price, 18000), true
        ) RETURNING id INTO inv_id;

        INSERT INTO tenant_payments (
          tenant_invoice_id, tenant_user_id, invoice_code, invoice_type,
          amount, method, paid_at, transaction_id, property_name, room_number, collection_mode
        ) VALUES (
          inv_id, tenant_uid, inv_code, 'WATER', water_amt, 'PAYOS', paid_ts + INTERVAL '2 hour',
          'DEMO-TXN-W-' || contract_id || '-' || to_char(ym, 'YYYYMM'), prop_name, room_no, 'ONLINE'
        );

        IF svc_fee IS NOT NULL AND svc_fee > 0 THEN
          inv_code := 'DEMO-SVC-' || contract_id || '-' || to_char(ym, 'YYYY-MM');
          INSERT INTO tenant_invoices (
            code, tenant_user_id, tenant_contract_id, invoice_type, cycle_type,
            property_name, room_number, billing_month, billing_year, billing_period,
            total_amount, late_fee, grand_total, status, due_date,
            created_at, paid_at, payment_method, transaction_id, auto_issued
          ) VALUES (
            inv_code, tenant_uid, contract_id, 'SERVICE', 'REGULAR',
            prop_name, room_no, EXTRACT(MONTH FROM ym)::int, EXTRACT(YEAR FROM ym)::int,
            'Phí dịch vụ tháng ' || EXTRACT(MONTH FROM ym)::int || '/' || EXTRACT(YEAR FROM ym)::int,
            svc_fee, 0, svc_fee, 'PAID', due,
            created_ts + INTERVAL '3 hour', paid_ts + INTERVAL '3 hour', 'PAYOS',
            'DEMO-TXN-S-' || contract_id || '-' || to_char(ym, 'YYYYMM'), true
          ) RETURNING id INTO inv_id;

          INSERT INTO tenant_payments (
            tenant_invoice_id, tenant_user_id, invoice_code, invoice_type,
            amount, method, paid_at, transaction_id, property_name, room_number, collection_mode
          ) VALUES (
            inv_id, tenant_uid, inv_code, 'SERVICE', svc_fee, 'PAYOS', paid_ts + INTERVAL '3 hour',
            'DEMO-TXN-S-' || contract_id || '-' || to_char(ym, 'YYYYMM'), prop_name, room_no, 'ONLINE'
          );
        END IF;
      END IF;

      ym := (ym + INTERVAL '1 month')::date;
    END LOOP;
  END LOOP;

  -- -------------------------------------------------------------------------
  -- 9) Maintenance
  -- -------------------------------------------------------------------------
  INSERT INTO maintenance_requests (
    request_code, tenant_id, property_id, room_id, tenant_contract_id, equipment_id,
    title, description, category, priority, status,
    created_at, updated_at, acknowledged_at, done_at, resolved_at,
    resolution_note, repair_cost, cost_agreement_status, is_deleted
  ) VALUES (
    'DEMO-MR-101-001', uid_t01, p101, NULL, c_an, eq_101_ac,
    'Máy lạnh phòng ngủ không lạnh',
    'Máy lạnh chạy nhưng không lạnh, có tiếng kêu lạ.',
    'APPLIANCE', 'HIGH', 'CLOSED',
    '2025-06-02 09:15:00', '2025-06-05 17:00:00',
    '2025-06-02 11:00:00', '2025-06-05 16:30:00', '2025-06-05 17:00:00',
    'Nạp gas + vệ sinh dàn lạnh.', 850000, 'AGREED', false
  ) RETURNING id INTO mr_id;

  IF has_damage_cause THEN
    EXECUTE 'UPDATE maintenance_requests SET damage_cause = ''WEAR'' WHERE id = $1' USING mr_id;
  END IF;
  IF has_flow_type THEN
    EXECUTE 'UPDATE maintenance_requests SET flow_type = ''NORMAL_WEAR'' WHERE id = $1' USING mr_id;
  END IF;

  INSERT INTO equipment_maintenance_histories (equipment_id, maintenance_request_id, maintenance_date, repair_cost, note)
  VALUES (eq_101_ac, mr_id, '2025-06-05 16:30:00', 850000, 'Nạp gas + vệ sinh dàn lạnh');

  INSERT INTO maintenance_history (maintenance_request_id, old_status, new_status, note, changed_by, changed_at) VALUES
    (mr_id, NULL, 'OPEN', 'Tenant tạo yêu cầu', uid_t01, '2025-06-02 09:15:00'),
    (mr_id, 'OPEN', 'IN_REPAIR', 'Manager nhận sửa', uid_mgr1, '2025-06-02 11:00:00'),
    (mr_id, 'IN_REPAIR', 'CLOSED', 'Hoàn tất', uid_mgr1, '2025-06-05 17:00:00');

  INSERT INTO maintenance_requests (
    request_code, tenant_id, property_id, room_id, tenant_contract_id, equipment_id,
    title, description, category, priority, status,
    created_at, updated_at, acknowledged_at, done_at, resolved_at,
    resolution_note, repair_cost, cost_agreement_status, is_deleted
  ) VALUES (
    'DEMO-MR-101-002', uid_t01, p101, NULL, c_an, eq_101_fr,
    'Tủ lạnh đóng tuyết ngăn đá', 'Ngăn đá đóng tuyết dày.',
    'APPLIANCE', 'MEDIUM', 'CLOSED',
    '2025-11-18 08:00:00', '2025-11-20 15:00:00',
    '2025-11-18 10:00:00', '2025-11-20 14:30:00', '2025-11-20 15:00:00',
    'Thay gioăng cửa + xả tuyết.', 650000, 'AGREED', false
  ) RETURNING id INTO mr_id;
  INSERT INTO equipment_maintenance_histories (equipment_id, maintenance_request_id, maintenance_date, repair_cost, note)
  VALUES (eq_101_fr, mr_id, '2025-11-20 14:30:00', 650000, 'Thay gioăng cửa tủ lạnh');

  INSERT INTO maintenance_requests (
    request_code, tenant_id, property_id, room_id, tenant_contract_id, equipment_id,
    title, description, category, priority, status,
    created_at, updated_at, acknowledged_at, done_at, resolved_at,
    resolution_note, repair_cost, cost_agreement_status, is_deleted
  ) VALUES (
    'DEMO-MR-101-003', uid_t01, p101, NULL, c_an, eq_101_wh,
    'Máy nước nóng rò nước nhẹ', 'Nhỏ giọt dưới bình nóng lạnh.',
    'PLUMBING', 'MEDIUM', 'CLOSED',
    '2026-01-12 19:00:00', '2026-01-15 11:00:00',
    '2026-01-13 09:00:00', '2026-01-15 10:30:00', '2026-01-15 11:00:00',
    'Thay van một chiều.', 420000, 'AGREED', false
  ) RETURNING id INTO mr_id;
  INSERT INTO equipment_maintenance_histories (equipment_id, maintenance_request_id, maintenance_date, repair_cost, note)
  VALUES (eq_101_wh, mr_id, '2026-01-15 10:30:00', 420000, 'Thay van một chiều');

  INSERT INTO maintenance_requests (
    request_code, tenant_id, property_id, room_id, tenant_contract_id, equipment_id,
    title, description, category, priority, status,
    created_at, updated_at, acknowledged_at, done_at, resolved_at,
    resolution_note, repair_cost, cost_agreement_status, is_deleted
  ) VALUES (
    'DEMO-MR-102-001', uid_t02, p102, NULL, c_binh, eq_102_fn,
    'Quạt đứng kêu to khi quay', 'Quạt rung khi tốc độ cao.',
    'APPLIANCE', 'LOW', 'CLOSED',
    '2026-06-03 10:00:00', '2026-06-05 16:30:00',
    '2026-06-03 14:00:00', '2026-06-05 16:00:00', '2026-06-05 16:30:00',
    'Bôi trơn bạc đạn.', 150000, 'WAIVED', false
  ) RETURNING id INTO mr_id;
  INSERT INTO equipment_maintenance_histories (equipment_id, maintenance_request_id, maintenance_date, repair_cost, note)
  VALUES (eq_102_fn, mr_id, '2026-06-05 16:00:00', 150000, 'Bôi trơn bạc đạn quạt');

  INSERT INTO maintenance_requests (
    request_code, tenant_id, property_id, room_id, tenant_contract_id, equipment_id,
    title, description, category, priority, status,
    created_at, updated_at, acknowledged_at, done_at, resolved_at,
    resolution_note, repair_cost, cost_agreement_status, is_deleted
  ) VALUES (
    'DEMO-MR-102-002', uid_t02, p102, NULL, c_binh, eq_102_wh,
    'Nóng lạnh không ra nước nóng', 'Bật máy nhưng nước vẫn lạnh.',
    'ELECTRICAL', 'HIGH', 'CLOSED',
    '2026-04-16 07:30:00', '2026-04-18 12:00:00',
    '2026-04-16 09:00:00', '2026-04-18 11:30:00', '2026-04-18 12:00:00',
    'Thay thanh đốt.', 780000, 'AGREED', false
  ) RETURNING id INTO mr_id;
  INSERT INTO equipment_maintenance_histories (equipment_id, maintenance_request_id, maintenance_date, repair_cost, note)
  VALUES (eq_102_wh, mr_id, '2026-04-18 11:30:00', 780000, 'Thay thanh đốt nóng lạnh');

  -- OPEN ticket (Dung)
  INSERT INTO maintenance_requests (
    request_code, tenant_id, property_id, room_id, tenant_contract_id, equipment_id,
    title, description, category, priority, status,
    created_at, updated_at, is_deleted
  ) VALUES (
    'DEMO-MR-104-001', uid_t04, p104, r104_p101, c_dung, eq_104_ac,
    'Máy lạnh P101 chảy nước vào phòng',
    'Ống thoát nước tắc, nước chảy xuống giường.',
    'APPLIANCE', 'URGENT', 'OPEN',
    '2026-09-10 21:00:00', '2026-09-10 21:00:00', false
  ) RETURNING id INTO mr_id;

  IF has_assigned_mgr THEN
    EXECUTE 'UPDATE maintenance_requests SET assigned_manager_id = $1 WHERE id = $2' USING uid_mgr2, mr_id;
  END IF;

  INSERT INTO maintenance_history (maintenance_request_id, old_status, new_status, note, changed_by, changed_at)
  VALUES (mr_id, NULL, 'OPEN', 'Tenant báo khẩn', uid_t04, '2026-09-10 21:00:00');

  INSERT INTO maintenance_requests (
    request_code, tenant_id, property_id, room_id, tenant_contract_id, equipment_id,
    title, description, category, priority, status,
    created_at, updated_at, acknowledged_at, done_at, resolved_at,
    resolution_note, repair_cost, cost_agreement_status, is_deleted
  ) VALUES (
    'DEMO-MR-106-001', uid_t05, p106, NULL, c_em_old, eq_106_wm,
    'Máy giặt không xả nước', 'Chu trình dừng ở bước xả.',
    'APPLIANCE', 'HIGH', 'CLOSED',
    '2024-09-01 08:00:00', '2024-09-03 17:00:00',
    '2024-09-01 10:00:00', '2024-09-03 16:30:00', '2024-09-03 17:00:00',
    'Thông ống xả + thay phin lọc.', 550000, 'AGREED', false
  ) RETURNING id INTO mr_id;
  INSERT INTO equipment_maintenance_histories (equipment_id, maintenance_request_id, maintenance_date, repair_cost, note)
  VALUES (eq_106_wm, mr_id, '2024-09-03 16:30:00', 550000, 'Thông ống xả máy giặt');

  INSERT INTO maintenance_requests (
    request_code, tenant_id, property_id, room_id, tenant_contract_id, equipment_id,
    title, description, category, priority, status,
    created_at, updated_at, acknowledged_at, done_at, resolved_at,
    resolution_note, repair_cost, cost_agreement_status, is_deleted
  ) VALUES (
    'DEMO-MR-106-002', uid_t07, p106, NULL, c_huy, eq_106_ac,
    'Máy lạnh yếu hơi lạnh', 'Phòng ngủ lâu mới mát.',
    'APPLIANCE', 'MEDIUM', 'CLOSED',
    '2026-08-01 09:00:00', '2026-08-03 16:00:00',
    '2026-08-01 11:00:00', '2026-08-03 15:30:00', '2026-08-03 16:00:00',
    'Vệ sinh dàn lạnh.', 400000, 'AGREED', false
  ) RETURNING id INTO mr_id;
  INSERT INTO equipment_maintenance_histories (equipment_id, maintenance_request_id, maintenance_date, repair_cost, note)
  VALUES (eq_106_ac, mr_id, '2026-08-03 15:30:00', 400000, 'Vệ sinh dàn lạnh');

  INSERT INTO maintenance_requests (
    request_code, tenant_id, property_id, room_id, tenant_contract_id, equipment_id,
    title, description, category, priority, status,
    created_at, updated_at, acknowledged_at, done_at, resolved_at,
    resolution_note, repair_cost, cost_agreement_status, is_deleted
  ) VALUES (
    'DEMO-MR-106-003', uid_t07, p106, NULL, c_huy, NULL,
    'Vòi lavabo phòng tắm bị rò', 'Khớp nối dưới lavabo nhỏ giọt.',
    'PLUMBING', 'MEDIUM', 'CLOSED',
    '2026-08-20 18:00:00', '2026-08-22 12:00:00',
    '2026-08-21 09:00:00', '2026-08-22 11:30:00', '2026-08-22 12:00:00',
    'Thay gioăng + siết lại.', 280000, 'AGREED', false
  );

  -- -------------------------------------------------------------------------
  -- 10) Host expenses (PnL)
  -- -------------------------------------------------------------------------
  INSERT INTO host_expenses (property_id, category, amount, month, note, created_at) VALUES
    (p101, 'MAINTENANCE', 850000,  '2025-06', 'DEMO sửa máy lạnh', now()),
    (p101, 'MAINTENANCE', 650000,  '2025-11', 'DEMO sửa tủ lạnh', now()),
    (p101, 'MANAGEMENT',  2000000, '2026-09', 'DEMO phí QL tháng 9', now()),
    (p102, 'MAINTENANCE', 780000,  '2026-04', 'DEMO sửa nóng lạnh', now()),
    (p106, 'MAINTENANCE', 550000,  '2024-09', 'DEMO sửa máy giặt', now()),
    (p106, 'MAINTENANCE', 400000,  '2026-08', 'DEMO vệ sinh máy lạnh', now()),
    (p106, 'MANAGEMENT',  2500000, '2026-09', 'DEMO phí QL tháng 9', now());

  RAISE NOTICE '======= DEMO ADDITIVE SEED OK =======';
  RAISE NOTICE 'Properties: demo#101..106 (không đụng data cũ)';
  RAISE NOTICE 'Login: demo_owner / demo_tenant01..07 / demo_manager01 — password 123456';
  RAISE NOTICE 'Highlights: An 2 năm, Bình 1 năm, Cường mới, Dung sắp hết HĐ, Em thuê lại, #106 3 khách';
END $$;

COMMIT;

-- =============================================================================
-- (TUỲ CHỌN) Cleanup chỉ data DEMO — bỏ comment khi muốn gỡ sau bảo vệ
-- KHÔNG chạy nhầm nếu chưa backup.
-- =============================================================================
/*
BEGIN;

DELETE FROM tenant_payments WHERE invoice_code LIKE 'DEMO-%';
DELETE FROM tenant_invoices WHERE code LIKE 'DEMO-%';
DELETE FROM equipment_maintenance_histories
 WHERE maintenance_request_id IN (SELECT id FROM maintenance_requests WHERE request_code LIKE 'DEMO-MR-%');
DELETE FROM maintenance_history
 WHERE maintenance_request_id IN (SELECT id FROM maintenance_requests WHERE request_code LIKE 'DEMO-MR-%');
DELETE FROM maintenance_timelines
 WHERE maintenance_request_id IN (SELECT id FROM maintenance_requests WHERE request_code LIKE 'DEMO-MR-%');
DELETE FROM maintenance_requests WHERE request_code LIKE 'DEMO-MR-%';
DELETE FROM host_expenses WHERE note LIKE 'DEMO%';
DELETE FROM household_members
 WHERE tenant_contract_id IN (SELECT id FROM tenant_contracts WHERE contract_code LIKE 'DEMO-TC-%');
DELETE FROM tenant_contracts WHERE contract_code LIKE 'DEMO-TC-%';
DELETE FROM inbound_contracts WHERE contract_code LIKE 'DEMO-IB-%';
DELETE FROM equipments WHERE qr_code LIKE 'DEMO-EQ-%';
DELETE FROM rooms WHERE property_id IN (SELECT id FROM properties WHERE property_code LIKE 'demo#%');
DELETE FROM properties WHERE property_code LIKE 'demo#%';

-- gỡ account demo (chỉ khi không còn FK)
-- DELETE FROM tenant WHERE user_id IN (SELECT id FROM "User" WHERE username LIKE 'demo_%');
-- DELETE FROM owner WHERE user_id IN (SELECT id FROM "User" WHERE username LIKE 'demo_%');
-- DELETE FROM operation_management WHERE user_id IN (SELECT id FROM "User" WHERE username LIKE 'demo_%');
-- DELETE FROM manager_zones WHERE manager_id IN (SELECT id FROM "User" WHERE username LIKE 'demo_%');
-- DELETE FROM "User" WHERE username LIKE 'demo_%';

COMMIT;
*/
