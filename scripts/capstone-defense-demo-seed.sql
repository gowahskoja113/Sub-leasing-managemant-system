-- =============================================================================
-- DEMO SEED — Bảo vệ capstone 04/10/2026 (additive, không đụng data production)
--
-- Tài khoản (mật khẩu 123456):
--   owner01                       — Chủ nhà
--   manager01                     — QLVH Bình Thạnh / Phú Nhuận / Quận 3 (nhà 1, 2, 3, 6), lương 12.000.000đ
--   manager02                     — QLVH Gò Vấp / Quận 1 (nhà 4, 5), lương 9.000.000đ
--
-- 6 nhà mang mã MTX#(n+1)..MTX#(n+6), n = số MTX lớn nhất đang có trong DB (biến p101..p106 = nhà 1..6).
-- Data seed được nhận diện qua mã KH điện PE05150000110..115 (cleanup xoá theo mã này).
--   demo_tenant01..10             — Khách thuê (mọi nhà / mọi phòng đều có khách đang ở)
--
-- Mốc thời gian: "hôm nay" = đầu tháng 10/2026.
--   - Tiền nhà + phí DV: đủ từng tháng từ lúc vào ở → 10/2026 (tháng đầu tính theo ngày).
--   - Điện / nước: đủ từng kỳ → kỳ 08/2026. Không chốt cuối tháng nữa: kỳ tháng M được quản lý chụp
--     công tơ ngày 04 tháng M+1, admin nhập giấy EVN / nước cùng ngày → hoá đơn khách phát hành, hạn +5 ngày.
--     Kỳ 09/2026 để trống — demo chụp số + nhập giấy trực tiếp ngày 04/10.
--   - Chỉ số công tơ cũ → mới mỗi tháng cho từng nhà từ lúc nhận nhà → kỳ 08/2026 (kể cả tháng nhà trống),
--     kèm hoá đơn điện/nước của nhà (utility_bills) mỗi kỳ. Mã KH điện PE05150000110..115, nước 15015000110..115.
--     Nhà theo phòng (nhà 4, nhà 5): mỗi phòng 1 chuỗi chỉ số riêng (kể cả phòng trống) + công tơ tổng của nhà.
--   - Điện nước kỳ 08/2026 (hạn 09/09): mọi khách đã trả.
--   - Tiền nhà + DV 10/2026 (cron tự phát hành ngày 01/10, hạn 05/10): An, Em, Giang đã trả, còn lại PENDING.
--   - Ticket lỗi do khách (TENANT_FAULT) đều có hoá đơn MAINTENANCE.
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- Helpers (pg_temp — tự huỷ khi đóng session)
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION pg_temp.demo_user(
  p_tbl text, p_id uuid, p_username text, p_pw text, p_phone text,
  p_email text, p_name text, p_role text
) RETURNS uuid LANGUAGE plpgsql AS $f$
DECLARE
  v_id uuid;
BEGIN
  EXECUTE format('SELECT id FROM %I WHERE username = $1', p_tbl) INTO v_id USING p_username;
  IF v_id IS NOT NULL THEN
    -- vd. manager01/manager02 do DataSeeder tạo sẵn → dùng luôn account đó
    IF v_id <> p_id THEN
      RAISE NOTICE 'Username "%" đã có sẵn (id=%) — dùng lại account này.', p_username, v_id;
    END IF;
    RETURN v_id;
  END IF;

  -- account demo đời cũ (demo_owner / demo_manager0x) cùng UUID → đổi sang username mới
  EXECUTE format('SELECT id FROM %I WHERE id = $1', p_tbl) INTO v_id USING p_id;
  IF v_id IS NOT NULL THEN
    EXECUTE format('UPDATE %I SET username = $2, email = $3, full_name = $4 WHERE id = $1', p_tbl)
    USING p_id, p_username, p_email, p_name;
    RETURN p_id;
  END IF;

  EXECUTE format('SELECT id FROM %I WHERE phone_number = $1', p_tbl) INTO v_id USING p_phone;
  IF v_id IS NOT NULL THEN
    RAISE EXCEPTION 'SĐT % đã thuộc account khác (id=%) — đổi SĐT demo trong script.', p_phone, v_id;
  END IF;

  EXECUTE format(
    'INSERT INTO %I (id, username, password, phone_number, email, full_name, role, status, create_at, is_first_login)
     VALUES ($1, $2, $3, $4, $5, $6, $7, ''ACTIVE'', now(), false)', p_tbl)
  USING p_id, p_username, p_pw, p_phone, p_email, p_name, p_role;
  RETURN p_id;
END $f$;

-- Tạo tenant_invoice (+ tenant_payment nếu p_paid có giá trị). Trả về invoice id.
CREATE OR REPLACE FUNCTION pg_temp.demo_invoice(
  p_code text, p_tenant uuid, p_contract bigint, p_type text, p_cycle text,
  p_prop text, p_room text, p_ym date, p_period text, p_note text,
  p_amount numeric, p_due date, p_created timestamp, p_paid timestamp,
  p_method text, p_manager uuid,
  p_utility_id bigint DEFAULT NULL,
  p_kwh numeric DEFAULT NULL, p_erate numeric DEFAULT NULL,
  p_m3 numeric DEFAULT NULL, p_wrate numeric DEFAULT NULL
) RETURNS bigint LANGUAGE plpgsql AS $f$
DECLARE
  v_id bigint;
  v_txn text := CASE WHEN p_paid IS NOT NULL THEN 'TXN-' || p_code END;
BEGIN
  INSERT INTO tenant_invoices (
    code, tenant_user_id, tenant_contract_id, utility_invoice_id, invoice_type, cycle_type,
    property_name, room_number, billing_month, billing_year, billing_period, note,
    total_amount, late_fee, grand_total, status, due_date, created_at, paid_at,
    payment_method, transaction_id, kwh_used, electricity_rate, m3_used, water_rate, auto_issued
  ) VALUES (
    p_code, p_tenant, p_contract, p_utility_id, p_type, p_cycle,
    p_prop, p_room, EXTRACT(MONTH FROM p_ym)::int, EXTRACT(YEAR FROM p_ym)::int, p_period, p_note,
    p_amount, 0, p_amount, CASE WHEN p_paid IS NOT NULL THEN 'PAID' ELSE 'PENDING' END,
    p_due, p_created, p_paid,
    CASE WHEN p_paid IS NOT NULL THEN p_method END, v_txn,
    p_kwh, p_erate, p_m3, p_wrate, p_type <> 'MAINTENANCE'
  ) RETURNING id INTO v_id;

  IF p_paid IS NOT NULL THEN
    INSERT INTO tenant_payments (
      tenant_invoice_id, tenant_user_id, invoice_code, invoice_type, amount, method, paid_at,
      transaction_id, property_name, room_number, collection_mode, facilitated_by, payment_note
    ) VALUES (
      v_id, p_tenant, p_code, p_type, p_amount, p_method, p_paid,
      v_txn, p_prop, p_room,
      CASE WHEN p_method = 'CASH' THEN 'MANAGER_CASH' ELSE 'SELF' END,
      CASE WHEN p_method = 'CASH' THEN p_manager END,
      'Thanh toán tiền thuê/dịch vụ'
    );
  END IF;
  RETURN v_id;
END $f$;

-- Hoá đơn bồi thường cho ticket lỗi do khách (giống MaintenanceServiceImpl.issueMaintenanceCharge).
CREATE OR REPLACE FUNCTION pg_temp.demo_maint_charge(
  p_mr bigint, p_amount numeric, p_issue timestamp, p_paid timestamp, p_method text
) RETURNS bigint LANGUAGE plpgsql AS $f$
DECLARE
  r record;
  v_inv bigint;
BEGIN
  UPDATE maintenance_requests SET request_code = 'M-' || id WHERE id = p_mr;
  SELECT m.request_code, m.tenant_id, m.tenant_contract_id, p.property_name,
         COALESCE(rm.room_number, p.property_name) AS room_label,
         tc.assigned_manager_id AS mgr
    INTO r
  FROM maintenance_requests m
  JOIN tenant_contracts tc ON tc.id = m.tenant_contract_id
  JOIN properties p ON p.id = m.property_id
  LEFT JOIN rooms rm ON rm.id = tc.room_id
  WHERE m.id = p_mr;

  v_inv := pg_temp.demo_invoice(
    'HD-MAINT-' || r.tenant_contract_id || '-' || (EXTRACT(EPOCH FROM p_issue) * 1000)::bigint,
    r.tenant_id, r.tenant_contract_id, 'MAINTENANCE', NULL,
    r.property_name, r.room_label, date_trunc('month', p_issue)::date,
    'Phí bảo trì', 'Bồi thường sửa chữa (lỗi do khách) — phiếu ' || r.request_code,
    p_amount, p_issue::date + 5, p_issue, p_paid, p_method, r.mgr);

  INSERT INTO tenant_pending_charges (
    tenant_contract_id, invoice_id, amount, category, note, maintenance_request_id, status, created_at
  ) VALUES (
    r.tenant_contract_id, v_inv, p_amount, 'MAINTENANCE',
    'Bồi thường sửa chữa — phiếu ' || r.request_code, p_mr, 'INVOICED', p_issue
  );

  UPDATE maintenance_requests SET charge_invoice_id = v_inv WHERE id = p_mr;
  RETURN v_inv;
END $f$;

-- Ghi timeline vào cả maintenance_history lẫn maintenance_timelines.
CREATE OR REPLACE FUNCTION pg_temp.demo_mr_step(
  p_mr bigint, p_old text, p_new text, p_note text, p_by uuid, p_by_name text, p_at timestamp
) RETURNS void LANGUAGE plpgsql AS $f$
BEGIN
  INSERT INTO maintenance_history (maintenance_request_id, old_status, new_status, note, changed_by, changed_at)
  VALUES (p_mr, p_old, p_new, p_note, p_by, p_at);
  INSERT INTO maintenance_timelines (maintenance_request_id, old_status, new_status, note, changed_by, changed_by_name, changed_at)
  VALUES (p_mr, p_old, p_new, p_note, p_by, p_by_name, p_at);
END $f$;

-- Địa chỉ đầy đủ đúng format backend: "<số nhà, đường, phường>, <quận>, <thành phố>"
CREATE OR REPLACE FUNCTION pg_temp.demo_addr(p_short text, p_zone uuid)
RETURNS text LANGUAGE sql AS $f$
  SELECT p_short || ', ' || z.name || COALESCE(', ' || pz.name, '')
  FROM zone z LEFT JOIN zone pz ON pz.id = z.parent_id
  WHERE z.id = p_zone
$f$;

-- Khoá cố định cho các công thức chỉ số điện nước / ngày giờ trả tiền, để drop DB chạy lại vẫn ra đúng số cũ.
-- k = ID của nhà / phòng / HĐ ở lần seed đã chốt số. NULL = dùng ID thật của lần chạy này.
-- Lấy lại giá trị: chạy query ở cuối file (phần "FREEZE KEYS").
CREATE TEMP TABLE IF NOT EXISTS demo_key (kind char(1), code text, k bigint, PRIMARY KEY (kind, code));
TRUNCATE pg_temp.demo_key;
INSERT INTO pg_temp.demo_key (kind, code, k) VALUES
  -- nhà: mã KH điện
  ('P', 'PE05150000110', 43),
  ('P', 'PE05150000111', 44),
  ('P', 'PE05150000112', 45),
  ('P', 'PE05150000113', 46),
  ('P', 'PE05150000114', 47),
  ('P', 'PE05150000115', 48),
  -- phòng: mã công tơ điện
  ('R', 'CTD-104-P101', 60),
  ('R', 'CTD-104-P102', 61),
  ('R', 'CTD-104-P103', 62),
  ('R', 'CTD-105-R201', 63),
  ('R', 'CTD-105-R202', 64),
  -- hợp đồng khách: mã HĐ
  ('C', 'HDT-2023-0107', 58),
  ('C', 'HDT-2024-0101', 52),
  ('C', 'HDT-2024-0105', 56),
  ('C', 'HDT-2025-0102', 53),
  ('C', 'HDT-2025-0104', 55),
  ('C', 'HDT-2025-0109', 60),
  ('C', 'HDT-2025-0110', 61),
  ('C', 'HDT-2026-0103', 54),
  ('C', 'HDT-2026-0106', 57),
  ('C', 'HDT-2026-0108', 59),
  ('C', 'HDT-2026-0111', 62);

CREATE OR REPLACE FUNCTION pg_temp.demo_k(p_kind char, p_id bigint)
RETURNS bigint LANGUAGE sql STABLE AS $f$
  SELECT COALESCE((
    SELECT d.k FROM pg_temp.demo_key d
    WHERE d.kind = p_kind AND d.code = CASE p_kind
      WHEN 'P' THEN (SELECT upper(trim(electricity_customer_code)) FROM properties WHERE id = p_id)
      WHEN 'R' THEN (SELECT electric_meter_code FROM rooms WHERE id = p_id)
      WHEN 'C' THEN (SELECT contract_code FROM tenant_contracts WHERE id = p_id)
    END
  ), p_id)
$f$;

DO $$
DECLARE
  mtx_base int;
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
  uid_t08     uuid := 'd0d00000-0000-4000-8000-000000000108'; -- Giang
  uid_t09     uuid := 'd0d00000-0000-4000-8000-000000000109'; -- Khánh
  uid_t10     uuid := 'd0d00000-0000-4000-8000-000000000110'; -- Linh

  -- properties / rooms / contracts / equipment
  p101 bigint; p102 bigint; p103 bigint; p104 bigint; p105 bigint; p106 bigint;
  r101 bigint; r102 bigint; r103 bigint; r106 bigint;
  r104_p101 bigint; r104_p102 bigint; r104_p103 bigint;
  r105_201 bigint; r105_202 bigint;

  c_an bigint; c_binh bigint; c_cuong bigint; c_dung bigint;
  c_em_old bigint; c_em_new bigint; c_phuong bigint; c_huy bigint;
  c_giang bigint; c_khanh bigint; c_linh bigint;

  eq_101_ac bigint; eq_101_fr bigint; eq_101_wh bigint;
  eq_102_fn bigint; eq_102_wh bigint; eq_102_wd bigint;
  eq_104_ac bigint;
  eq_106_ac bigint; eq_106_wm bigint;

  mr_id bigint;

  -- billing
  demo_month  date      := DATE '2026-10-01';            -- tháng hiện tại (bảo vệ 04/10/2026)
  util_month  date      := DATE '2026-08-01';            -- kỳ điện nước mới nhất (chụp số 04/09, phát hành 04/09)
  pay_now_ts  timestamp := TIMESTAMP '2026-10-02 20:15'; -- khách trả sớm kỳ hiện tại

  c_rec record;
  ck bigint;
  ym date;
  month_end date;
  first_m date;
  last_rent_m date;
  last_util_m date;
  dim int;
  billed int;
  mon int;
  summer int;
  amt numeric;
  period_label text;
  note_txt text;
  cycle text;
  due date;
  created_ts timestamp;
  paid_ts timestamp;
  pay_method text;
  pays_now boolean;
  p_start date;
  p_end date;
  prev_e numeric;
  prev_w numeric;
  kwh numeric;
  m3 numeric;
  issue_ts timestamp;
  rec_ts timestamp;
  ui_id bigint;
  h_rec record;
  admin_id uuid;
  qty numeric;
  billed_qty numeric;

  col_rec record;
BEGIN
  -- -------------------------------------------------------------------------
  -- 0) Guards
  -- -------------------------------------------------------------------------
  IF NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'zone') THEN
    RAISE EXCEPTION 'Chưa có bảng zone. Restart API rồi chạy lại.';
  END IF;

  ALTER TABLE properties ADD COLUMN IF NOT EXISTS property_code VARCHAR(32);

  -- 6 nhà seed nhận diện bằng mã KH điện PE05150000110..115 (cleanup cũng xoá theo mã này)
  IF EXISTS (
    SELECT 1 FROM properties
    WHERE property_code LIKE 'demo#%'
       OR upper(trim(electricity_customer_code)) IN
            ('PE05150000110', 'PE05150000111', 'PE05150000112', 'PE05150000113', 'PE05150000114', 'PE05150000115')
       OR trim(water_customer_code) IN
            ('15015000110', '15015000111', '15015000112', '15015000113', '15015000114', '15015000115')
  ) THEN
    RAISE NOTICE 'Seed đã có (mã KH điện PE05150000110..115 đang gắn nhà). Bỏ qua — chạy scripts/capstone-defense-demo-cleanup.sql trước.';
    RETURN;
  END IF;

  -- DB tạo mới: Hibernate tạo cột NOT NULL không DEFAULT, DatabaseSchemaMigration bỏ qua vì cột đã tồn tại
  FOR col_rec IN
    SELECT c.table_name, c.column_name
    FROM information_schema.columns c
    WHERE c.table_schema = 'public'
      AND c.data_type = 'boolean'
      AND c.is_nullable = 'NO'
      AND c.column_default IS NULL
  LOOP
    EXECUTE format('ALTER TABLE %I ALTER COLUMN %I SET DEFAULT FALSE', col_rec.table_name, col_rec.column_name);
  END LOOP;

  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'equipments' AND column_name = 'maintenance_count'
      AND column_default IS NULL
  ) THEN
    ALTER TABLE equipments ALTER COLUMN maintenance_count SET DEFAULT 0;
  END IF;

  ALTER TABLE maintenance_requests ADD COLUMN IF NOT EXISTS flow_type VARCHAR(50);
  ALTER TABLE maintenance_requests ADD COLUMN IF NOT EXISTS damage_cause VARCHAR(50);
  ALTER TABLE maintenance_requests ADD COLUMN IF NOT EXISTS fault_reason TEXT;
  ALTER TABLE maintenance_requests ADD COLUMN IF NOT EXISTS fault_resolution_path VARCHAR(50);
  ALTER TABLE maintenance_requests ADD COLUMN IF NOT EXISTS estimated_damage_amount NUMERIC(19, 2);
  ALTER TABLE maintenance_requests ADD COLUMN IF NOT EXISTS charge_invoice_id BIGINT;
  ALTER TABLE maintenance_requests ADD COLUMN IF NOT EXISTS cost_agreement_status VARCHAR(50);
  ALTER TABLE maintenance_requests ADD COLUMN IF NOT EXISTS repair_started_at TIMESTAMP;
  ALTER TABLE maintenance_requests ADD COLUMN IF NOT EXISTS repair_description TEXT;
  ALTER TABLE maintenance_requests ADD COLUMN IF NOT EXISTS invoice_vendor VARCHAR(255);
  ALTER TABLE maintenance_requests ADD COLUMN IF NOT EXISTS invoice_number VARCHAR(255);
  ALTER TABLE maintenance_requests ADD COLUMN IF NOT EXISTS invoice_date DATE;

  -- -------------------------------------------------------------------------
  -- 1) Zones — reuse production theo tên
  -- -------------------------------------------------------------------------
  -- trùng tên → ưu tiên zone admin đã phân công (zone_managers) để khớp trang Phân công khu vực
  SELECT z.id INTO z_binhthanh FROM zone z WHERE z.level = 2 AND lower(z.name) LIKE '%bình thạnh%'
  ORDER BY EXISTS (SELECT 1 FROM zone_managers zm WHERE zm.zone_id = z.id) DESC, z.id LIMIT 1;
  SELECT z.id INTO z_phunhuan  FROM zone z WHERE z.level = 2 AND lower(z.name) LIKE '%phú nhuận%'
  ORDER BY EXISTS (SELECT 1 FROM zone_managers zm WHERE zm.zone_id = z.id) DESC, z.id LIMIT 1;
  SELECT z.id INTO z_quan3     FROM zone z WHERE z.level = 2 AND lower(z.name) IN ('quận 3', 'quan 3')
  ORDER BY EXISTS (SELECT 1 FROM zone_managers zm WHERE zm.zone_id = z.id) DESC, z.id LIMIT 1;
  SELECT z.id INTO z_govap     FROM zone z WHERE z.level = 2 AND lower(z.name) LIKE '%gò vấp%'
  ORDER BY EXISTS (SELECT 1 FROM zone_managers zm WHERE zm.zone_id = z.id) DESC, z.id LIMIT 1;
  SELECT z.id INTO z_quan1     FROM zone z WHERE z.level = 2 AND lower(z.name) IN ('quận 1', 'quan 1')
  ORDER BY EXISTS (SELECT 1 FROM zone_managers zm WHERE zm.zone_id = z.id) DESC, z.id LIMIT 1;

  IF z_binhthanh IS NULL OR z_phunhuan IS NULL OR z_quan3 IS NULL OR z_govap IS NULL OR z_quan1 IS NULL THEN
    RAISE EXCEPTION
      'Thiếu zone quận (Bình Thạnh/Phú Nhuận/Quận 3/Gò Vấp/Quận 1). Seed zone trước hoặc chỉnh tên trong script.';
  END IF;

  -- -------------------------------------------------------------------------
  -- 2) Catalog — reuse theo tên
  -- -------------------------------------------------------------------------
  INSERT INTO equipment_catalog (name, description, active) VALUES
    ('Điều hòa', 'Máy lạnh / điều hòa không khí', true),
    ('Tủ lạnh', 'Tủ lạnh các loại', true),
    ('Máy giặt', 'Máy giặt', true),
    ('Bàn ăn', 'Bàn ăn', true),
    ('Giường', 'Giường ngủ', true),
    ('Tủ quần áo', 'Tủ QA', true),
    ('Bếp từ', 'Bếp từ', true),
    ('Nóng lạnh', 'Máy nước nóng', true),
    ('Quạt', 'Quạt điện', true)
  ON CONFLICT (name) DO NOTHING;

  SELECT id INTO cat_ac       FROM equipment_catalog WHERE name = 'Điều hòa' LIMIT 1;
  SELECT id INTO cat_fridge   FROM equipment_catalog WHERE name = 'Tủ lạnh' LIMIT 1;
  SELECT id INTO cat_washer   FROM equipment_catalog WHERE name = 'Máy giặt' LIMIT 1;
  SELECT id INTO cat_table    FROM equipment_catalog WHERE name = 'Bàn ăn' LIMIT 1;
  SELECT id INTO cat_bed      FROM equipment_catalog WHERE name = 'Giường' LIMIT 1;
  SELECT id INTO cat_wardrobe FROM equipment_catalog WHERE name = 'Tủ quần áo' LIMIT 1;
  SELECT id INTO cat_stove    FROM equipment_catalog WHERE name = 'Bếp từ' LIMIT 1;
  SELECT id INTO cat_heater   FROM equipment_catalog WHERE name = 'Nóng lạnh' LIMIT 1;
  SELECT id INTO cat_fan      FROM equipment_catalog WHERE name = 'Quạt' LIMIT 1;

  -- -------------------------------------------------------------------------
  -- 3) Demo users — owner/manager đặt tên theo role + số thứ tự
  -- -------------------------------------------------------------------------
  SELECT tablename INTO user_tbl
  FROM pg_tables WHERE schemaname = 'public' AND lower(tablename) = 'user' LIMIT 1;
  IF user_tbl IS NULL THEN
    RAISE EXCEPTION 'Không tìm thấy bảng User/user.';
  END IF;

  uid_owner := pg_temp.demo_user(user_tbl, uid_owner, 'owner01',   pw, '0988000001', 'owner01@slms.local',   'Trần Quốc Bảo',   'ROLE_OWNER');
  uid_mgr1  := pg_temp.demo_user(user_tbl, uid_mgr1,  'manager01', pw, '0988000011', 'manager01@slms.local', 'Nguyễn Văn Hùng', 'ROLE_MANAGER');
  uid_mgr2  := pg_temp.demo_user(user_tbl, uid_mgr2,  'manager02', pw, '0988000012', 'manager02@slms.local', 'Lê Thị Mai',      'ROLE_MANAGER');
  uid_t01 := pg_temp.demo_user(user_tbl, uid_t01, 'demo_tenant01', pw, '0988000101', 'demo_t01@slms.local', 'Nguyễn Văn An',  'ROLE_TENANT');
  uid_t02 := pg_temp.demo_user(user_tbl, uid_t02, 'demo_tenant02', pw, '0988000102', 'demo_t02@slms.local', 'Trần Thị Bình',  'ROLE_TENANT');
  uid_t03 := pg_temp.demo_user(user_tbl, uid_t03, 'demo_tenant03', pw, '0988000103', 'demo_t03@slms.local', 'Lê Minh Cường',  'ROLE_TENANT');
  uid_t04 := pg_temp.demo_user(user_tbl, uid_t04, 'demo_tenant04', pw, '0988000104', 'demo_t04@slms.local', 'Phạm Thị Dung',  'ROLE_TENANT');
  uid_t05 := pg_temp.demo_user(user_tbl, uid_t05, 'demo_tenant05', pw, '0988000105', 'demo_t05@slms.local', 'Hoàng Văn Em',   'ROLE_TENANT');
  uid_t06 := pg_temp.demo_user(user_tbl, uid_t06, 'demo_tenant06', pw, '0988000106', 'demo_t06@slms.local', 'Võ Thị Phương',  'ROLE_TENANT');
  uid_t07 := pg_temp.demo_user(user_tbl, uid_t07, 'demo_tenant07', pw, '0988000107', 'demo_t07@slms.local', 'Đặng Quốc Huy',  'ROLE_TENANT');
  uid_t08 := pg_temp.demo_user(user_tbl, uid_t08, 'demo_tenant08', pw, '0988000108', 'demo_t08@slms.local', 'Bùi Thị Giang',  'ROLE_TENANT');
  uid_t09 := pg_temp.demo_user(user_tbl, uid_t09, 'demo_tenant09', pw, '0988000109', 'demo_t09@slms.local', 'Ngô Minh Khánh', 'ROLE_TENANT');
  uid_t10 := pg_temp.demo_user(user_tbl, uid_t10, 'demo_tenant10', pw, '0988000110', 'demo_t10@slms.local', 'Trịnh Mỹ Linh',  'ROLE_TENANT');

  INSERT INTO owner (user_id) VALUES (uid_owner) ON CONFLICT DO NOTHING;
  INSERT INTO operation_management (user_id, start_at) VALUES
    (uid_mgr1, '2023-01-02 08:00:00'), (uid_mgr2, '2024-12-15 08:00:00')
  ON CONFLICT DO NOTHING;
  INSERT INTO tenant (user_id, cccd, date_of_birth, permanent_address) VALUES
    (uid_t01, '079203008001', '1995-03-12', '12 Trần Hưng Đạo, Phường Phạm Ngũ Lão, Quận 1, TP. Hồ Chí Minh'),
    (uid_t02, '079203008002', '1998-07-22', '34 Phan Đăng Lưu, Phường 5, Phú Nhuận, TP. Hồ Chí Minh'),
    (uid_t03, '079203008003', '2000-11-05', '21 Nguyễn Trãi, Thanh Xuân, Hà Nội'),
    (uid_t04, '079203008004', '1996-01-30', '210 Lê Văn Sỹ, Phường 14, Quận 3, TP. Hồ Chí Minh'),
    (uid_t05, '079203008005', '1993-09-18', '78 Nguyễn Oanh, Phường 7, Gò Vấp, TP. Hồ Chí Minh'),
    (uid_t06, '079203008006', '1997-12-02', '15 Nơ Trang Long, Phường 7, Bình Thạnh, TP. Hồ Chí Minh'),
    (uid_t07, '079203008007', '1994-06-25', '102 Phạm Văn Thuận, Tân Mai, Biên Hòa, Đồng Nai'),
    (uid_t08, '079203008008', '1999-04-14', '45 Hùng Vương, Phường 2, Tân An, Long An'),
    (uid_t09, '079203008009', '1997-08-09', '9 Yersin, Phú Cường, Thủ Dầu Một, Bình Dương'),
    (uid_t10, '079203008010', '2001-02-27', '27 Ấp Bắc, Phường 5, Mỹ Tho, Tiền Giang')
  ON CONFLICT DO NOTHING;

  -- -------------------------------------------------------------------------
  -- 3b) Phân công khu vực + lương quản lý vận hành
  --     zone_managers (1 khu vực = 1 quản lý) là nguồn của trang admin "Phân công khu vực"
  --     và trang owner "Quản lý vận hành" / "Lương quản lý" → phải khớp operation_manager_id của nhà.
  -- -------------------------------------------------------------------------
  EXECUTE format('SELECT id FROM %I WHERE username = $1', user_tbl) INTO admin_id USING 'admin01';

  -- account demo đời cũ (demo_manager01/02) còn sót → khoá để không hiện trùng trong danh sách quản lý
  EXECUTE format(
    'UPDATE %I SET status = ''INACTIVE'' WHERE id IN ($1, $2) AND id NOT IN ($3, $4) AND role = ''ROLE_MANAGER''',
    user_tbl)
  USING 'd0d00000-0000-4000-8000-000000000011'::uuid, 'd0d00000-0000-4000-8000-000000000012'::uuid,
        uid_mgr1, uid_mgr2;
  EXECUTE format('UPDATE %I SET status = ''ACTIVE'' WHERE id IN ($1, $2)', user_tbl) USING uid_mgr1, uid_mgr2;

  DELETE FROM manager_zones
  WHERE zone_id IN (z_binhthanh, z_phunhuan, z_quan3, z_govap, z_quan1);
  INSERT INTO manager_zones (manager_id, zone_id) VALUES
    (uid_mgr1, z_binhthanh), (uid_mgr1, z_phunhuan), (uid_mgr1, z_quan3),
    (uid_mgr2, z_govap), (uid_mgr2, z_quan1)
  ON CONFLICT DO NOTHING;

  INSERT INTO zone_managers (zone_id, manager_id, assigned_by, assigned_at) VALUES
    (z_binhthanh, uid_mgr1, admin_id, '2023-01-02 08:00:00'),
    (z_phunhuan,  uid_mgr1, admin_id, '2025-07-01 08:00:00'),
    (z_quan3,     uid_mgr1, admin_id, '2026-06-15 08:00:00'),
    (z_govap,     uid_mgr2, admin_id, '2024-12-15 08:00:00'),
    (z_quan1,     uid_mgr2, admin_id, '2026-05-01 08:00:00')
  ON CONFLICT (zone_id) DO UPDATE
    SET manager_id = EXCLUDED.manager_id, assigned_by = EXCLUDED.assigned_by, assigned_at = EXCLUDED.assigned_at;

  -- nhà khác (không phải demo) đã gán quản lý trong 5 khu vực này → theo quản lý của khu vực
  UPDATE properties p
  SET operation_manager_id = CASE WHEN p.zone_id IN (z_govap, z_quan1) THEN uid_mgr2 ELSE uid_mgr1 END
  WHERE p.zone_id IN (z_binhthanh, z_phunhuan, z_quan3, z_govap, z_quan1)
    AND p.operation_manager_id IS NOT NULL
    AND p.operation_manager_id <> CASE WHEN p.zone_id IN (z_govap, z_quan1) THEN uid_mgr2 ELSE uid_mgr1 END;
  GET DIAGNOSTICS billed = ROW_COUNT;
  IF billed > 0 THEN
    RAISE NOTICE 'Đã chuyển % nhà (ngoài demo) trong 5 khu vực sang manager01/manager02 cho khớp phân công.', billed;
    UPDATE tenant_contracts tc
    SET assigned_manager_id = p.operation_manager_id
    FROM properties p
    WHERE p.id = tc.property_id
      AND p.zone_id IN (z_binhthanh, z_phunhuan, z_quan3, z_govap, z_quan1)
      AND tc.status NOT IN ('EXPIRED', 'TERMINATED', 'CANCELLED')
      AND tc.assigned_manager_id IS DISTINCT FROM p.operation_manager_id;
  END IF;

  -- Lương quản lý (trang owner "Lương quản lý" đọc pricing_config.manager_salaries_json)
  IF NOT EXISTS (SELECT 1 FROM pricing_config WHERE id = 1) THEN
    RAISE EXCEPTION 'Chưa có pricing_config (id=1). Restart API một lần để DatabaseSchemaMigration tạo rồi chạy lại.';
  END IF;

  UPDATE pricing_config
  SET manager_salaries_json = (
        CASE WHEN manager_salaries_json IS NULL OR btrim(manager_salaries_json) = '' THEN '{}'::jsonb
             ELSE manager_salaries_json::jsonb END
        || jsonb_build_object(uid_mgr1::text, 12000000, uid_mgr2::text, 9000000)
      )::text,
      updated_at = now(),
      updated_by = admin_id
  WHERE id = 1;

  -- -------------------------------------------------------------------------
  -- 4) Properties — mã nhà MTX#n nối tiếp số MTX lớn nhất đang có (tránh trùng nhà thật)
  --    Mã KH điện PE05150000110..115 / nước 15015000110..115 lần lượt cho nhà 1..6
  --    (biến p101..p106 trong script = nhà 1..6, chỉ là tên biến)
  -- -------------------------------------------------------------------------
  SELECT COALESCE(MAX((regexp_match(property_code, '^mtx#(\d+)$', 'i'))[1]::int), 0)
    INTO mtx_base FROM properties;

  INSERT INTO properties (
    property_name, property_code, address, zone_id, area_size, length_m, width_m,
    total_floor, is_whole_house, has_renovation, total_rooms, status,
    operation_manager_id, descriptions, price, applied_price,
    electricity_unit_price, water_unit_price, electricity_customer_code, water_customer_code,
    deposit_months, service_fee, renovation_completed, manager_accepted_at
  ) VALUES (
    'MTX#' || (mtx_base + 1) || ' Nhà phố Xô Viết Nghệ Tĩnh',
    'mtx#' || (mtx_base + 1), pg_temp.demo_addr('124/7 Xô Viết Nghệ Tĩnh, Phường 21', z_binhthanh),
    z_binhthanh, 85, 8.5, 10, 2, true, true, 3, 'ACTIVE',
    uid_mgr1, 'Nhà phố 1 trệt 1 lầu, 3 phòng ngủ, full nội thất, hẻm xe hơi, gần chợ Thị Nghè.',
    14000000, 14000000, 3500, 18000, 'PE05150000110', '15015000110',
    2, 200000, true, '2024-08-01 09:00:00'
  ) RETURNING id INTO p101;

  INSERT INTO properties (
    property_name, property_code, address, zone_id, area_size, length_m, width_m,
    total_floor, is_whole_house, has_renovation, total_rooms, status,
    operation_manager_id, descriptions, price, applied_price,
    electricity_unit_price, water_unit_price, electricity_customer_code, water_customer_code,
    deposit_months, service_fee, renovation_completed, manager_accepted_at
  ) VALUES (
    'MTX#' || (mtx_base + 2) || ' Nhà nguyên căn Hoàng Văn Thụ',
    'mtx#' || (mtx_base + 2), pg_temp.demo_addr('56/12 Hoàng Văn Thụ, Phường 9', z_phunhuan),
    z_phunhuan, 55, 7, 8, 1, true, false, 2, 'ACTIVE',
    uid_mgr1, 'Nhà cấp 4 gác lửng, 2 phòng ngủ, nội thất cơ bản (giường, tủ, quạt, nóng lạnh), gần sân bay.',
    9000000, 9000000, 3500, 18000, 'PE05150000111', '15015000111',
    1, 150000, true, '2025-07-01 10:00:00'
  ) RETURNING id INTO p102;

  INSERT INTO properties (
    property_name, property_code, address, zone_id, area_size, length_m, width_m,
    total_floor, is_whole_house, has_renovation, total_rooms, status,
    operation_manager_id, descriptions, price, applied_price,
    electricity_unit_price, water_unit_price, electricity_customer_code, water_customer_code,
    deposit_months, service_fee, renovation_completed, manager_accepted_at
  ) VALUES (
    'MTX#' || (mtx_base + 3) || ' Nhà nguyên căn Kỳ Đồng',
    'mtx#' || (mtx_base + 3), pg_temp.demo_addr('18/3 Kỳ Đồng, Phường 9', z_quan3),
    z_quan3, 70, 7, 10, 2, true, false, 3, 'ACTIVE',
    uid_mgr1, 'Nhà 1 trệt 1 lầu, 3 phòng ngủ, bàn giao nhà trống (không nội thất), hẻm 4m.',
    7500000, 7500000, 3500, 18000, 'PE05150000112', '15015000112',
    1, 100000, true, '2026-06-15 09:00:00'
  ) RETURNING id INTO p103;

  INSERT INTO properties (
    property_name, property_code, address, zone_id, area_size, length_m, width_m,
    total_floor, is_whole_house, has_renovation, total_rooms, status,
    operation_manager_id, descriptions, price, applied_price,
    electricity_unit_price, water_unit_price, electricity_customer_code, water_customer_code,
    deposit_months, service_fee, renovation_completed, manager_accepted_at
  ) VALUES (
    'MTX#' || (mtx_base + 4) || ' Nhà trọ Quang Trung',
    'mtx#' || (mtx_base + 4), pg_temp.demo_addr('230/15 Quang Trung, Phường 10', z_govap),
    z_govap, 120, 10, 12, 3, false, true, 3, 'ACTIVE',
    uid_mgr2, 'Nhà 3 tầng cho thuê theo phòng, 3 phòng full nội thất, công tơ điện nước riêng từng phòng.',
    NULL, NULL, 3500, 18000, 'PE05150000113', '15015000113',
    1, 50000, true, '2025-01-10 08:00:00'
  ) RETURNING id INTO p104;

  INSERT INTO properties (
    property_name, property_code, address, zone_id, area_size, length_m, width_m,
    total_floor, is_whole_house, has_renovation, total_rooms, status,
    operation_manager_id, descriptions, price, applied_price,
    electricity_unit_price, water_unit_price, electricity_customer_code, water_customer_code,
    deposit_months, service_fee, renovation_completed, manager_accepted_at
  ) VALUES (
    'MTX#' || (mtx_base + 5) || ' Nhà trọ Nguyễn Cảnh Chân',
    'mtx#' || (mtx_base + 5), pg_temp.demo_addr('45/6 Nguyễn Cảnh Chân, Phường Cầu Kho', z_quan1),
    z_quan1, 90, 9, 10, 2, false, false, 2, 'ACTIVE',
    uid_mgr2, 'Nhà 2 tầng cho thuê theo phòng, 2 phòng không nội thất, công tơ điện nước riêng từng phòng.',
    NULL, NULL, 3500, 18000, 'PE05150000114', '15015000114',
    1, 80000, true, '2026-05-01 08:00:00'
  ) RETURNING id INTO p105;

  INSERT INTO properties (
    property_name, property_code, address, zone_id, area_size, length_m, width_m,
    total_floor, is_whole_house, has_renovation, total_rooms, status,
    operation_manager_id, descriptions, price, applied_price,
    electricity_unit_price, water_unit_price, electricity_customer_code, water_customer_code,
    deposit_months, service_fee, renovation_completed, manager_accepted_at
  ) VALUES (
    'MTX#' || (mtx_base + 6) || ' Nhà phố Bạch Đằng',
    'mtx#' || (mtx_base + 6), pg_temp.demo_addr('88/21 Bạch Đằng, Phường 24', z_binhthanh),
    z_binhthanh, 95, 9.5, 10, 2, true, true, 3, 'ACTIVE',
    uid_mgr1, 'Nhà phố 1 trệt 1 lầu, 3 phòng ngủ, full nội thất, gần cầu Bình Triệu.',
    15000000, 15000000, 3500, 18000, 'PE05150000115', '15015000115',
    2, 250000, true, '2023-01-05 09:00:00'
  ) RETURNING id INTO p106;

  -- inbound contracts (thuê từ chủ gốc — còn hiệu lực qua ngày bảo vệ)
  INSERT INTO inbound_contracts (property_id, contract_code, owner_name, total_rent_amount, start_date, end_date, status) VALUES
    (p101, 'HDG-2024-0101', 'Nguyễn Văn Thành', 396000000, '2024-08-01', '2027-07-31', 'ACTIVE'), -- 36 th × 11tr
    (p102, 'HDG-2025-0102', 'Trần Thị Lan', 168000000, '2025-07-01', '2027-06-30', 'ACTIVE'), -- 24 th × 7tr
    (p103, 'HDG-2026-0103', 'Lê Hoàng Phúc', 104400000, '2026-06-01', '2027-11-30', 'ACTIVE'), -- 18 th × 5,8tr
    (p104, 'HDG-2025-0104', 'Phạm Văn Đức', 324000000, '2025-01-01', '2027-12-31', 'ACTIVE'), -- 36 th × 9tr
    (p105, 'HDG-2026-0105', 'Huỳnh Thị Ngọc',  96000000, '2026-05-01', '2027-04-30', 'ACTIVE'), -- 12 th × 8tr
    (p106, 'HDG-2023-0106', 'Võ Minh Tâm', 690000000, '2023-01-01', '2027-12-31', 'ACTIVE'); -- 60 th × 11,5tr

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
  VALUES (p104, 'P101', 1, 4500000, 4500000, 4500000, 22, 2, 'INDIVIDUAL_ROOM', 'RENTED', 'CTD-104-P101', 'CTN-104-P101')
  RETURNING id INTO r104_p101;

  INSERT INTO rooms (property_id, room_number, floor, price, applied_price, deposit, area, max_occupants,
                     room_type, status, electric_meter_code, water_meter_code)
  VALUES (p104, 'P102', 1, 4200000, 4200000, 4200000, 20, 2, 'INDIVIDUAL_ROOM', 'AVAILABLE', 'CTD-104-P102', 'CTN-104-P102')
  RETURNING id INTO r104_p102;

  INSERT INTO rooms (property_id, room_number, floor, price, applied_price, deposit, area, max_occupants,
                     room_type, status, electric_meter_code, water_meter_code)
  VALUES (p104, 'P103', 2, 4800000, 4800000, 4800000, 24, 2, 'INDIVIDUAL_ROOM', 'AVAILABLE', 'CTD-104-P103', 'CTN-104-P103')
  RETURNING id INTO r104_p103;

  INSERT INTO rooms (property_id, room_number, floor, price, applied_price, deposit, area, max_occupants,
                     room_type, status, electric_meter_code, water_meter_code)
  VALUES (p105, 'R201', 2, 5500000, 5500000, 5500000, 25, 2, 'INDIVIDUAL_ROOM', 'RENTED', 'CTD-105-R201', 'CTN-105-R201')
  RETURNING id INTO r105_201;

  INSERT INTO rooms (property_id, room_number, floor, price, applied_price, deposit, area, max_occupants,
                     room_type, status, electric_meter_code, water_meter_code)
  VALUES (p105, 'R202', 2, 5200000, 5200000, 5200000, 23, 2, 'INDIVIDUAL_ROOM', 'AVAILABLE', 'CTD-105-R202', 'CTN-105-R202')
  RETURNING id INTO r105_202;

  -- -------------------------------------------------------------------------
  -- 6) Equipments
  -- -------------------------------------------------------------------------
  INSERT INTO equipments (property_id, room_id, catalog_id, house_area, source, status, price, equipment_name, category,
                          installation_date, warranty_months, warranty_start_date, warranty_end_date,
                          maintenance_count, last_maintenance_date, qr_code, penalty_fee)
  VALUES
    (p101, r101, cat_ac, 'BEDROOM', 'INITIAL_HANDOVER', 'GOOD', 12000000, 'Máy lạnh Daikin PN', 'APPLIANCE',
     '2024-08-01', 24, '2024-08-01', '2026-08-01', 1, '2025-06-05 16:30:00', 'TB-101-AC-01', 500000)
  RETURNING id INTO eq_101_ac;

  INSERT INTO equipments (property_id, room_id, catalog_id, house_area, source, status, price, equipment_name, category,
                          installation_date, warranty_months, warranty_start_date, warranty_end_date,
                          maintenance_count, last_maintenance_date, qr_code, penalty_fee)
  VALUES
    (p101, r101, cat_fridge, 'KITCHEN', 'INITIAL_HANDOVER', 'GOOD', 8000000, 'Tủ lạnh Toshiba', 'APPLIANCE',
     '2024-08-01', 24, '2024-08-01', '2026-08-01', 1, '2025-11-20 14:30:00', 'TB-101-FR-01', 400000)
  RETURNING id INTO eq_101_fr;

  INSERT INTO equipments (property_id, room_id, catalog_id, house_area, source, status, price, equipment_name, category,
                          installation_date, qr_code, penalty_fee)
  VALUES
    (p101, r101, cat_washer, 'OTHER', 'INITIAL_HANDOVER', 'GOOD', 7000000, 'Máy giặt LG', 'APPLIANCE',
     '2024-08-01', 'TB-101-WM-01', 400000),
    (p101, r101, cat_bed, 'BEDROOM', 'INITIAL_HANDOVER', 'GOOD', 3500000, 'Giường 1m6', 'FURNITURE',
     '2024-08-01', 'TB-101-BD-01', 200000),
    (p101, r101, cat_wardrobe, 'BEDROOM', 'INITIAL_HANDOVER', 'GOOD', 2500000, 'Tủ QA 3 cánh', 'FURNITURE',
     '2024-08-01', 'TB-101-WD-01', 150000),
    (p101, r101, cat_table, 'LIVING_ROOM', 'INITIAL_HANDOVER', 'GOOD', 4000000, 'Bàn ăn 4 ghế', 'FURNITURE',
     '2024-08-01', 'TB-101-TB-01', 200000),
    (p101, r101, cat_stove, 'KITCHEN', 'INITIAL_HANDOVER', 'GOOD', 3000000, 'Bếp từ đôi', 'APPLIANCE',
     '2024-08-01', 'TB-101-ST-01', 250000),
    (p101, r101, cat_ac, 'LIVING_ROOM', 'INITIAL_HANDOVER', 'GOOD', 10000000, 'Máy lạnh PK', 'APPLIANCE',
     '2024-08-01', 'TB-101-AC-02', 500000);

  INSERT INTO equipments (property_id, room_id, catalog_id, house_area, source, status, price, equipment_name, category,
                          installation_date, maintenance_count, last_maintenance_date, qr_code, penalty_fee)
  VALUES
    (p101, r101, cat_heater, 'BATHROOM', 'INITIAL_HANDOVER', 'GOOD', 2500000, 'Nóng lạnh Ariston', 'APPLIANCE',
     '2024-08-01', 1, '2026-01-15 10:30:00', 'TB-101-WH-01', 200000)
  RETURNING id INTO eq_101_wh;

  -- #102 basic
  INSERT INTO equipments (property_id, room_id, catalog_id, house_area, source, status, price, equipment_name, category,
                          installation_date, qr_code, penalty_fee)
  VALUES
    (p102, r102, cat_bed, 'BEDROOM', 'INITIAL_HANDOVER', 'GOOD', 2800000, 'Giường sắt', 'FURNITURE',
     '2025-07-01', 'TB-102-BD-01', 150000);

  INSERT INTO equipments (property_id, room_id, catalog_id, house_area, source, status, price, equipment_name, category,
                          installation_date, maintenance_count, last_maintenance_date, qr_code, penalty_fee)
  VALUES
    (p102, r102, cat_wardrobe, 'BEDROOM', 'INITIAL_HANDOVER', 'GOOD', 1800000, 'Tủ QA 2 cánh', 'FURNITURE',
     '2025-07-01', 1, '2026-09-30 14:30:00', 'TB-102-WD-01', 100000)
  RETURNING id INTO eq_102_wd;

  INSERT INTO equipments (property_id, room_id, catalog_id, house_area, source, status, price, equipment_name, category,
                          installation_date, maintenance_count, last_maintenance_date, qr_code, penalty_fee)
  VALUES
    (p102, r102, cat_fan, 'BEDROOM', 'INITIAL_HANDOVER', 'GOOD', 450000, 'Quạt đứng', 'APPLIANCE',
     '2025-07-01', 1, '2026-06-05 16:00:00', 'TB-102-FN-01', 50000)
  RETURNING id INTO eq_102_fn;

  INSERT INTO equipments (property_id, room_id, catalog_id, house_area, source, status, price, equipment_name, category,
                          installation_date, maintenance_count, last_maintenance_date, qr_code, penalty_fee)
  VALUES
    (p102, r102, cat_heater, 'BATHROOM', 'INITIAL_HANDOVER', 'GOOD', 2200000, 'Nóng lạnh Rossi', 'APPLIANCE',
     '2025-07-01', 1, '2026-04-18 11:30:00', 'TB-102-WH-01', 200000)
  RETURNING id INTO eq_102_wh;

  -- #104 rooms
  INSERT INTO equipments (property_id, room_id, catalog_id, house_area, source, status, price, equipment_name, category,
                          installation_date, maintenance_count, qr_code, penalty_fee)
  VALUES
    (p104, r104_p101, cat_ac, 'BEDROOM', 'INITIAL_HANDOVER', 'GOOD', 8500000, 'Máy lạnh P101', 'APPLIANCE',
     '2025-01-10', 0, 'TB-104-P101-AC', 400000)
  RETURNING id INTO eq_104_ac;

  INSERT INTO equipments (property_id, room_id, catalog_id, house_area, source, status, price, equipment_name, category,
                          installation_date, qr_code, penalty_fee)
  VALUES
    (p104, r104_p101, cat_bed, 'BEDROOM', 'INITIAL_HANDOVER', 'GOOD', 2500000, 'Giường P101', 'FURNITURE',
     '2025-01-10', 'TB-104-P101-BD', 150000),
    (p104, r104_p101, cat_wardrobe, 'BEDROOM', 'INITIAL_HANDOVER', 'GOOD', 1500000, 'Tủ P101', 'FURNITURE',
     '2025-01-10', 'TB-104-P101-WD', 100000),
    (p104, r104_p101, cat_heater, 'BATHROOM', 'INITIAL_HANDOVER', 'GOOD', 2000000, 'Nóng lạnh P101', 'APPLIANCE',
     '2025-01-10', 'TB-104-P101-WH', 150000),
    (p104, r104_p102, cat_ac, 'BEDROOM', 'INITIAL_HANDOVER', 'NEW', 8500000, 'Máy lạnh P102', 'APPLIANCE',
     '2025-01-10', 'TB-104-P102-AC', 400000),
    (p104, r104_p102, cat_bed, 'BEDROOM', 'INITIAL_HANDOVER', 'NEW', 2500000, 'Giường P102', 'FURNITURE',
     '2025-01-10', 'TB-104-P102-BD', 150000),
    (p104, r104_p103, cat_ac, 'BEDROOM', 'INITIAL_HANDOVER', 'NEW', 9000000, 'Máy lạnh P103', 'APPLIANCE',
     '2025-01-10', 'TB-104-P103-AC', 400000),
    (p104, r104_p103, cat_bed, 'BEDROOM', 'INITIAL_HANDOVER', 'NEW', 2800000, 'Giường P103', 'FURNITURE',
     '2025-01-10', 'TB-104-P103-BD', 150000);

  -- #106 full
  INSERT INTO equipments (property_id, room_id, catalog_id, house_area, source, status, price, equipment_name, category,
                          installation_date, maintenance_count, last_maintenance_date, qr_code, penalty_fee)
  VALUES
    (p106, r106, cat_ac, 'BEDROOM', 'INITIAL_HANDOVER', 'GOOD', 11000000, 'Máy lạnh Panasonic 1.5HP', 'APPLIANCE',
     '2023-01-05', 1, '2026-08-03 15:30:00', 'TB-106-AC-01', 500000)
  RETURNING id INTO eq_106_ac;

  INSERT INTO equipments (property_id, room_id, catalog_id, house_area, source, status, price, equipment_name, category,
                          installation_date, maintenance_count, last_maintenance_date, qr_code, penalty_fee)
  VALUES
    (p106, r106, cat_washer, 'OTHER', 'INITIAL_HANDOVER', 'GOOD', 6500000, 'Máy giặt Electrolux', 'APPLIANCE',
     '2023-01-05', 1, '2024-09-03 16:30:00', 'TB-106-WM-01', 400000)
  RETURNING id INTO eq_106_wm;

  INSERT INTO equipments (property_id, room_id, catalog_id, house_area, source, status, price, equipment_name, category,
                          installation_date, qr_code, penalty_fee)
  VALUES
    (p106, r106, cat_fridge, 'KITCHEN', 'INITIAL_HANDOVER', 'GOOD', 7500000, 'Tủ lạnh Samsung', 'APPLIANCE',
     '2023-01-05', 'TB-106-FR-01', 400000),
    (p106, r106, cat_bed, 'BEDROOM', 'INITIAL_HANDOVER', 'GOOD', 3000000, 'Giường gỗ 1m6', 'FURNITURE',
     '2023-01-05', 'TB-106-BD-01', 200000),
    (p106, r106, cat_wardrobe, 'BEDROOM', 'INITIAL_HANDOVER', 'GOOD', 2200000, 'Tủ QA 3 cánh', 'FURNITURE',
     '2023-01-05', 'TB-106-WD-01', 150000),
    (p106, r106, cat_stove, 'KITCHEN', 'INITIAL_HANDOVER', 'GOOD', 2800000, 'Bếp từ đôi', 'APPLIANCE',
     '2023-01-05', 'TB-106-ST-01', 250000),
    (p106, r106, cat_heater, 'BATHROOM', 'INITIAL_HANDOVER', 'GOOD', 2300000, 'Nóng lạnh Ariston', 'APPLIANCE',
     '2023-01-05', 'TB-106-WH-01', 200000);

  -- -------------------------------------------------------------------------
  -- 7) Tenant contracts — mốc ngày tính theo ngày bảo vệ 04/10/2026
  -- -------------------------------------------------------------------------
  -- An: ở 2 năm (10/2024 → nay), HĐ đến 31/03/2027
  INSERT INTO tenant_contracts (
    tenant_user_id, property_id, room_id, contract_code, rent_amount, base_rent_amount,
    rent_escalation_type, deposit, deposit_months, move_in_date, start_date, end_date,
    payment_status, deposit_paid_at, deposit_method, paid_at, activated_at,
    assigned_manager_id, onboarded_by_manager_id, onboarded_at, status,
    initial_electric_reading, initial_water_reading, room_condition_note
  ) VALUES (
    uid_t01, p101, NULL, 'HDT-2024-0101',
    14000000, 14000000, 'NONE', 28000000, 2,
    '2024-10-01', '2024-10-01', '2027-03-31',
    'PAID', '2024-09-28 10:00:00', 'CASH', '2024-09-28 10:00:00', '2024-10-01 09:00:00',
    uid_mgr1, uid_mgr1, '2024-10-01 09:00:00', 'ACTIVE',
    1250, 85, 'Bàn giao đủ nội thất, tường sơn mới, máy lạnh 2 phòng hoạt động tốt'
  ) RETURNING id INTO c_an;

  -- Bình: ở ~1 năm (15/09/2025 → nay), HĐ đến 14/03/2027
  INSERT INTO tenant_contracts (
    tenant_user_id, property_id, room_id, contract_code, rent_amount, base_rent_amount,
    rent_escalation_type, deposit, deposit_months, move_in_date, start_date, end_date,
    payment_status, deposit_paid_at, deposit_method, paid_at, activated_at,
    assigned_manager_id, onboarded_by_manager_id, onboarded_at, status,
    initial_electric_reading, initial_water_reading, room_condition_note
  ) VALUES (
    uid_t02, p102, NULL, 'HDT-2025-0102',
    9000000, 9000000, 'NONE', 9000000, 1,
    '2025-09-15', '2025-09-15', '2027-03-14',
    'PAID', '2025-09-12 11:00:00', 'PAYOS', '2025-09-12 11:00:00', '2025-09-15 10:00:00',
    uid_mgr1, uid_mgr1, '2025-09-15 10:00:00', 'ACTIVE',
    320, 40, 'Nội thất cơ bản đầy đủ, nóng lạnh hoạt động bình thường'
  ) RETURNING id INTO c_binh;

  -- Cường: khách mới (20/08/2026)
  INSERT INTO tenant_contracts (
    tenant_user_id, property_id, room_id, contract_code, rent_amount, base_rent_amount,
    rent_escalation_type, deposit, deposit_months, move_in_date, start_date, end_date,
    payment_status, deposit_paid_at, deposit_method, paid_at, activated_at,
    assigned_manager_id, onboarded_by_manager_id, onboarded_at, status,
    initial_electric_reading, initial_water_reading, room_condition_note
  ) VALUES (
    uid_t03, p103, NULL, 'HDT-2026-0103',
    7500000, 7500000, 'NONE', 7500000, 1,
    '2026-08-20', '2026-08-20', '2027-08-19',
    'PAID', '2026-08-18 14:00:00', 'PAYOS', '2026-08-18 14:00:00', '2026-08-20 09:00:00',
    uid_mgr1, uid_mgr1, '2026-08-20 09:00:00', 'ACTIVE',
    10, 2, 'Nhà trống, tường sạch, điện nước ổn định'
  ) RETURNING id INTO c_cuong;

  -- Dung: HĐ 1 năm, sắp hết hạn 31/10/2026 (còn ~27 ngày tính từ ngày bảo vệ)
  INSERT INTO tenant_contracts (
    tenant_user_id, property_id, room_id, contract_code, rent_amount, base_rent_amount,
    rent_escalation_type, deposit, deposit_months, move_in_date, start_date, end_date,
    payment_status, deposit_paid_at, deposit_method, paid_at, activated_at,
    assigned_manager_id, onboarded_by_manager_id, onboarded_at, status,
    initial_electric_reading, initial_water_reading, room_condition_note
  ) VALUES (
    uid_t04, p104, r104_p101, 'HDT-2025-0104',
    4500000, 4500000, 'NONE', 4500000, 1,
    '2025-11-01', '2025-11-01', '2026-10-31',
    'PAID', '2025-10-29 16:00:00', 'CASH', '2025-10-29 16:00:00', '2025-11-01 08:00:00',
    uid_mgr2, uid_mgr2, '2025-11-01 08:00:00', 'ACTIVE',
    180, 25, 'Phòng sạch, nội thất đầy đủ, máy lạnh mới vệ sinh'
  ) RETURNING id INTO c_dung;

  -- Em (HĐ cũ #106): 07/2024 → 30/06/2026, đã hết hạn
  INSERT INTO tenant_contracts (
    tenant_user_id, property_id, room_id, contract_code, rent_amount, base_rent_amount,
    rent_escalation_type, deposit, deposit_months, move_in_date, start_date, end_date,
    payment_status, deposit_paid_at, deposit_method, paid_at, activated_at,
    assigned_manager_id, onboarded_by_manager_id, onboarded_at, status,
    terminated_at, termination_type, termination_reason,
    initial_electric_reading, initial_water_reading, room_condition_note
  ) VALUES (
    uid_t05, p106, NULL, 'HDT-2024-0105',
    14500000, 14500000, 'NONE', 29000000, 2,
    '2024-07-01', '2024-07-01', '2026-06-30',
    'PAID', '2024-06-28 10:00:00', 'CASH', '2024-06-28 10:00:00', '2024-07-01 09:00:00',
    uid_mgr1, uid_mgr1, '2024-07-01 09:00:00', 'EXPIRED',
    '2026-06-30 17:00:00', 'OTHER', 'Hết hạn — chuyển thuê nhà khác',
    NULL, NULL, 'Bàn giao đủ nội thất, máy giặt và tủ lạnh hoạt động tốt'
  ) RETURNING id INTO c_em_old;

  -- Em (HĐ mới #105): thuê lại từ 01/07/2026
  INSERT INTO tenant_contracts (
    tenant_user_id, property_id, room_id, contract_code, rent_amount, base_rent_amount,
    rent_escalation_type, deposit, deposit_months, move_in_date, start_date, end_date,
    payment_status, deposit_paid_at, deposit_method, paid_at, activated_at,
    assigned_manager_id, onboarded_by_manager_id, onboarded_at, status,
    initial_electric_reading, initial_water_reading, room_condition_note
  ) VALUES (
    uid_t05, p105, r105_201, 'HDT-2026-0106',
    5500000, 5500000, 'NONE', 5500000, 1,
    '2026-07-01', '2026-07-01', '2027-06-30',
    'PAID', '2026-06-28 15:00:00', 'PAYOS', '2026-06-28 15:00:00', '2026-07-01 10:00:00',
    uid_mgr2, uid_mgr2, '2026-07-01 10:00:00', 'ACTIVE',
    5, 1, 'Phòng trống, tường sạch, công tơ điện nước mới'
  ) RETURNING id INTO c_em_new;

  -- Phương: khách đầu tiên #106, 02/2023 → 30/06/2024
  INSERT INTO tenant_contracts (
    tenant_user_id, property_id, room_id, contract_code, rent_amount, base_rent_amount,
    rent_escalation_type, deposit, deposit_months, move_in_date, start_date, end_date,
    payment_status, deposit_paid_at, deposit_method, paid_at, activated_at,
    assigned_manager_id, onboarded_by_manager_id, onboarded_at, status,
    terminated_at, termination_type, termination_reason,
    initial_electric_reading, initial_water_reading, room_condition_note
  ) VALUES (
    uid_t06, p106, NULL, 'HDT-2023-0107',
    13000000, 13000000, 'NONE', 26000000, 2,
    '2023-02-01', '2023-02-01', '2024-06-30',
    'PAID', '2023-01-28 10:00:00', 'CASH', '2023-01-28 10:00:00', '2023-02-01 09:00:00',
    uid_mgr1, uid_mgr1, '2023-02-01 09:00:00', 'EXPIRED',
    '2024-06-30 16:00:00', 'OTHER', 'Hết hạn — trả nhà',
    50, 5, 'Nhà mới cải tạo, bàn giao đủ nội thất'
  ) RETURNING id INTO c_phuong;

  -- Huy: khách hiện tại #106 từ 05/07/2026
  INSERT INTO tenant_contracts (
    tenant_user_id, property_id, room_id, contract_code, rent_amount, base_rent_amount,
    rent_escalation_type, deposit, deposit_months, move_in_date, start_date, end_date,
    payment_status, deposit_paid_at, deposit_method, paid_at, activated_at,
    assigned_manager_id, onboarded_by_manager_id, onboarded_at, status,
    initial_electric_reading, initial_water_reading, room_condition_note
  ) VALUES (
    uid_t07, p106, NULL, 'HDT-2026-0108',
    15000000, 15000000, 'NONE', 30000000, 2,
    '2026-07-05', '2026-07-05', '2027-07-04',
    'PAID', '2026-07-02 11:00:00', 'PAYOS', '2026-07-02 11:00:00', '2026-07-05 09:00:00',
    uid_mgr1, uid_mgr1, '2026-07-05 09:00:00', 'ACTIVE',
    NULL, NULL, 'Bàn giao đủ nội thất, máy giặt đã thay mới 09/2024'
  ) RETURNING id INTO c_huy;

  -- Giang: #104 P102, ở lâu (01/02/2025 → nay), HĐ 2 năm đến 31/01/2027
  INSERT INTO tenant_contracts (
    tenant_user_id, property_id, room_id, contract_code, rent_amount, base_rent_amount,
    rent_escalation_type, deposit, deposit_months, move_in_date, start_date, end_date,
    payment_status, deposit_paid_at, deposit_method, paid_at, activated_at,
    assigned_manager_id, onboarded_by_manager_id, onboarded_at, status,
    initial_electric_reading, initial_water_reading, room_condition_note
  ) VALUES (
    uid_t08, p104, r104_p102, 'HDT-2025-0109',
    4200000, 4200000, 'NONE', 4200000, 1,
    '2025-02-01', '2025-02-01', '2027-01-31',
    'PAID', '2025-01-29 10:00:00', 'PAYOS', '2025-01-29 10:00:00', '2025-02-01 09:00:00',
    uid_mgr2, uid_mgr2, '2025-02-01 09:00:00', 'ACTIVE',
    140, 9, 'Phòng sạch, nội thất đầy đủ, máy lạnh hoạt động tốt'
  ) RETURNING id INTO c_giang;

  -- Khánh: #104 P103, từ 10/06/2025 (~1 năm 4 tháng), HĐ đến 09/06/2027
  INSERT INTO tenant_contracts (
    tenant_user_id, property_id, room_id, contract_code, rent_amount, base_rent_amount,
    rent_escalation_type, deposit, deposit_months, move_in_date, start_date, end_date,
    payment_status, deposit_paid_at, deposit_method, paid_at, activated_at,
    assigned_manager_id, onboarded_by_manager_id, onboarded_at, status,
    initial_electric_reading, initial_water_reading, room_condition_note
  ) VALUES (
    uid_t09, p104, r104_p103, 'HDT-2025-0110',
    4800000, 4800000, 'NONE', 4800000, 1,
    '2025-06-10', '2025-06-10', '2027-06-09',
    'PAID', '2025-06-07 15:00:00', 'PAYOS', '2025-06-07 15:00:00', '2025-06-10 09:00:00',
    uid_mgr2, uid_mgr2, '2025-06-10 09:00:00', 'ACTIVE',
    160, 11, 'Phòng tầng 2, nội thất đầy đủ, ban công thoáng'
  ) RETURNING id INTO c_khanh;

  -- Linh: #105 R202, từ 15/05/2026 (ngay sau khi nhận nhà), HĐ đến 14/05/2027
  INSERT INTO tenant_contracts (
    tenant_user_id, property_id, room_id, contract_code, rent_amount, base_rent_amount,
    rent_escalation_type, deposit, deposit_months, move_in_date, start_date, end_date,
    payment_status, deposit_paid_at, deposit_method, paid_at, activated_at,
    assigned_manager_id, onboarded_by_manager_id, onboarded_at, status,
    initial_electric_reading, initial_water_reading, room_condition_note
  ) VALUES (
    uid_t10, p105, r105_202, 'HDT-2026-0111',
    5200000, 5200000, 'NONE', 5200000, 1,
    '2026-05-15', '2026-05-15', '2027-05-14',
    'PAID', '2026-05-12 10:00:00', 'PAYOS', '2026-05-12 10:00:00', '2026-05-15 09:00:00',
    uid_mgr2, uid_mgr2, '2026-05-15 09:00:00', 'ACTIVE',
    150, 10, 'Phòng trống, tường sạch, cửa sổ thoáng'
  ) RETURNING id INTO c_linh;

  UPDATE rooms SET status = 'RENTED' WHERE id IN (r104_p102, r104_p103, r105_202);

  INSERT INTO household_members (tenant_contract_id, full_name, relation, phone) VALUES
    (c_an, 'Nguyễn Thị Hoa', 'Vợ', '0911111101'),
    (c_binh, 'Trần Văn Nam', 'Chồng', '0911111102'),
    (c_khanh, 'Ngô Thị Thu', 'Em gái', '0911111109');

  -- -------------------------------------------------------------------------
  -- 8a) Chỉ số công tơ nhà nguyên căn trong các tháng nhà còn trống
  --     (từ lúc nhận nhà → trước khách đầu tiên). Khách đầu nhận số bàn giao nối tiếp chuỗi này.
  -- -------------------------------------------------------------------------
  FOR h_rec IN
    SELECT p.id AS pid, p.operation_manager_id AS mgr,
           date_trunc('month', p.manager_accepted_at)::date AS start_m,
           fc.start_date AS first_start,
           COALESCE(fc.initial_electric_reading, 0) AS base_e,
           COALESCE(fc.initial_water_reading, 0) AS base_w
    FROM properties p
    JOIN LATERAL (
      SELECT tc.start_date, tc.initial_electric_reading, tc.initial_water_reading
      FROM tenant_contracts tc
      WHERE tc.property_id = p.id AND tc.room_id IS NULL
      ORDER BY tc.start_date
      LIMIT 1
    ) fc ON true
    WHERE p.id IN (p101, p102, p103, p104, p105, p106) AND p.is_whole_house
  LOOP
    prev_e := h_rec.base_e;
    prev_w := h_rec.base_w;
    ym := h_rec.start_m;
    WHILE ym < date_trunc('month', h_rec.first_start)::date LOOP
      mon := EXTRACT(MONTH FROM ym)::int;
      month_end := (ym + INTERVAL '1 month' - INTERVAL '1 day')::date;
      kwh := 8 + (pg_temp.demo_k('P', h_rec.pid) % 5) * 2 + mon % 3;
      m3 := 1;

      INSERT INTO meter_readings (property_id, room_id, utility_type, period, reading, prev_reading,
                                  recorded_at, recorded_by, utility_invoice_id)
      VALUES (h_rec.pid, NULL, 'ELECTRIC', to_char(ym, 'YYYY-MM'), prev_e + kwh, prev_e,
              (month_end + 4) + TIME '08:00', h_rec.mgr, NULL),
             (h_rec.pid, NULL, 'WATER', to_char(ym, 'YYYY-MM'), prev_w + m3, prev_w,
              (month_end + 4) + TIME '08:01', h_rec.mgr, NULL);

      prev_e := prev_e + kwh;
      prev_w := prev_w + m3;
      ym := (ym + INTERVAL '1 month')::date;
    END LOOP;
  END LOOP;

  -- -------------------------------------------------------------------------
  -- 8a') Công tơ riêng từng phòng (nhà cho thuê theo phòng): mỗi phòng 1 chuỗi chỉ số cũ → mới
  --      từ lúc nhận nhà → kỳ 08/2026. Phòng trống cả kỳ: chạy hết; phòng có khách: chạy tới trước
  --      tháng khách vào, khách nhận số bàn giao nối tiếp (vòng 8 ghi tiếp các tháng có khách).
  -- -------------------------------------------------------------------------
  FOR h_rec IN
    SELECT p.id AS pid, rm.id AS rid, p.operation_manager_id AS mgr,
           date_trunc('month', p.manager_accepted_at)::date AS start_m,
           COALESCE((SELECT date_trunc('month', MIN(tc.start_date))::date
                     FROM tenant_contracts tc WHERE tc.room_id = rm.id),
                    util_month + INTERVAL '1 month')::date AS stop_m
    FROM properties p
    JOIN rooms rm ON rm.property_id = p.id
    WHERE p.id IN (p101, p102, p103, p104, p105, p106) AND NOT p.is_whole_house
    ORDER BY rm.id
  LOOP
    prev_e := 120 + (pg_temp.demo_k('R', h_rec.rid) % 9) * 23;
    prev_w := 8 + (pg_temp.demo_k('R', h_rec.rid) % 4) * 3;
    ym := h_rec.start_m;
    WHILE ym < h_rec.stop_m LOOP
      mon := EXTRACT(MONTH FROM ym)::int;
      month_end := (ym + INTERVAL '1 month' - INTERVAL '1 day')::date;
      -- phòng trống: chỉ đèn hành lang / dọn phòng / thử máy
      kwh := 3 + (pg_temp.demo_k('R', h_rec.rid) % 3) + mon % 2;
      m3 := (pg_temp.demo_k('R', h_rec.rid) + mon) % 2;

      INSERT INTO meter_readings (property_id, room_id, utility_type, period, reading, prev_reading,
                                  recorded_at, recorded_by, utility_invoice_id)
      VALUES (h_rec.pid, h_rec.rid, 'ELECTRIC', to_char(ym, 'YYYY-MM'), prev_e + kwh, prev_e,
              (month_end + 4) + TIME '08:30', h_rec.mgr, NULL),
             (h_rec.pid, h_rec.rid, 'WATER', to_char(ym, 'YYYY-MM'), prev_w + m3, prev_w,
              (month_end + 4) + TIME '08:31', h_rec.mgr, NULL);

      prev_e := prev_e + kwh;
      prev_w := prev_w + m3;
      ym := (ym + INTERVAL '1 month')::date;
    END LOOP;
  END LOOP;

  -- -------------------------------------------------------------------------
  -- 8) Hoá đơn hàng tháng — từ lúc vào ở đến hiện tại
  --    Tiền nhà + phí DV: tháng vào ở (FIRST, tính theo ngày) → 10/2026, hạn ngày 5.
  --    Điện/nước: kỳ = tháng sử dụng, chốt số cuối tháng, phát hành ngày 1 tháng sau, hạn +5 ngày.
  --    Chỉ số công tơ nối tiếp giữa các khách cùng nhà (#106: Phương → Em → Huy).
  -- -------------------------------------------------------------------------
  FOR c_rec IN
    SELECT tc.id, tc.tenant_user_id, tc.property_id, tc.room_id, tc.rent_amount,
           tc.start_date, tc.end_date, tc.status, tc.paid_at AS onboard_paid_at,
           tc.initial_electric_reading AS init_e, tc.initial_water_reading AS init_w,
           tc.assigned_manager_id AS mgr,
           p.property_name,
           COALESCE(rm.room_number, p.property_name) AS room_label,
           COALESCE(p.electricity_unit_price, 3500) AS e_price,
           COALESCE(p.water_unit_price, 18000) AS w_price,
           COALESCE(p.service_fee, 0) AS svc
    FROM tenant_contracts tc
    JOIN properties p ON p.id = tc.property_id
    LEFT JOIN rooms rm ON rm.id = tc.room_id
    WHERE tc.property_id IN (p101, p102, p103, p104, p105, p106)
    ORDER BY tc.start_date
  LOOP
    ck          := pg_temp.demo_k('C', c_rec.id);
    first_m     := date_trunc('month', c_rec.start_date)::date;
    last_rent_m := LEAST(date_trunc('month', c_rec.end_date)::date, demo_month);
    last_util_m := LEAST(date_trunc('month', c_rec.end_date)::date, util_month);
    pay_method  := CASE WHEN c_rec.id IN (c_dung, c_phuong, c_khanh) THEN 'CASH' ELSE 'QR' END;
    pays_now    := c_rec.id IN (c_an, c_em_new, c_giang);

    -- ---- Tiền nhà + phí dịch vụ ----
    ym := first_m;
    WHILE ym <= last_rent_m LOOP
      month_end := (ym + INTERVAL '1 month' - INTERVAL '1 day')::date;
      dim := EXTRACT(DAY FROM month_end)::int;
      mon := EXTRACT(MONTH FROM ym)::int;

      IF ym = first_m THEN
        cycle := 'FIRST';
        p_start := c_rec.start_date;
        p_end := LEAST(month_end, c_rec.end_date);
        billed := p_end - p_start + 1;
        amt := CASE WHEN billed < dim THEN round(c_rec.rent_amount * billed / dim, 0) ELSE c_rec.rent_amount END;
        period_label := CASE WHEN billed < dim
          THEN 'Tiền nhà ' || to_char(p_start, 'DD/MM') || '–' || to_char(p_end, 'DD/MM/YYYY')
               || ' (' || billed || '/' || dim || ' ngày)'
          ELSE 'Tiền nhà tháng ' || to_char(ym, 'MM/YYYY') END;
        note_txt := 'FIRST_CYCLE|onboardPaid=true|days=' || billed || '|daysInMonth=' || dim
                    || '|rentAmount=' || round(c_rec.rent_amount, 0)
                    || '|periodStart=' || p_start || '|periodEnd=' || p_end;
        created_ts := c_rec.onboard_paid_at;
        paid_ts := c_rec.onboard_paid_at;
        due := c_rec.onboard_paid_at::date;
      ELSE
        cycle := 'REGULAR';
        amt := c_rec.rent_amount;
        period_label := 'Tiền nhà tháng ' || to_char(ym, 'MM/YYYY');
        note_txt := 'REGULAR|rentAmount=' || round(c_rec.rent_amount, 0);
        IF ym = date_trunc('month', c_rec.end_date)::date THEN
          billed := EXTRACT(DAY FROM c_rec.end_date)::int;
          IF billed <= 3 THEN
            ym := (ym + INTERVAL '1 month')::date;
            CONTINUE;
          END IF;
          IF billed < dim THEN
            amt := round(c_rec.rent_amount * billed / dim, 0);
            period_label := 'Tiền nhà 01/' || to_char(ym, 'MM') || '–' || to_char(c_rec.end_date, 'DD/MM/YYYY')
                     || ' (' || billed || '/' || dim || ' ngày)';
          END IF;
        END IF;
        created_ts := ym + TIME '00:05';
        due := ym + 4;
        IF ym = demo_month THEN
          paid_ts := CASE WHEN pays_now THEN pay_now_ts END;
        ELSE
          paid_ts := (ym + ((ck + mon) % 4)::int) + TIME '19:30'
                     + ((ck % 50)::int * INTERVAL '1 minute');
        END IF;
      END IF;

      PERFORM pg_temp.demo_invoice(
        'HD-RENT-' || c_rec.id || '-' || to_char(ym, 'YYYY-MM'),
        c_rec.tenant_user_id, c_rec.id, 'RENT', cycle,
        c_rec.property_name, c_rec.room_label, ym, period_label, note_txt,
        amt, due, created_ts, paid_ts, pay_method, c_rec.mgr);

      IF c_rec.svc > 0 THEN
        PERFORM pg_temp.demo_invoice(
          'HD-SVC-' || c_rec.id || '-' || to_char(ym, 'YYYY-MM'),
          c_rec.tenant_user_id, c_rec.id, 'SERVICE', NULL,
          c_rec.property_name, c_rec.room_label, ym,
          'Phí dịch vụ tháng ' || mon || '/' || EXTRACT(YEAR FROM ym)::int, NULL,
          c_rec.svc, due, created_ts + INTERVAL '1 minute', paid_ts + INTERVAL '2 minute',
          pay_method, c_rec.mgr);
      END IF;

      ym := (ym + INTERVAL '1 month')::date;
    END LOOP;

    -- ---- Điện / nước ----
    SELECT mr.reading INTO prev_e FROM meter_readings mr
    WHERE mr.property_id = c_rec.property_id AND mr.room_id IS NOT DISTINCT FROM c_rec.room_id
      AND mr.utility_type = 'ELECTRIC'
    ORDER BY mr.recorded_at DESC LIMIT 1;
    SELECT mr.reading INTO prev_w FROM meter_readings mr
    WHERE mr.property_id = c_rec.property_id AND mr.room_id IS NOT DISTINCT FROM c_rec.room_id
      AND mr.utility_type = 'WATER'
    ORDER BY mr.recorded_at DESC LIMIT 1;

    IF prev_e IS NOT NULL THEN
      -- số bàn giao = chỉ số mới kỳ trước (chuỗi liền: mới tháng này = cũ tháng sau)
      prev_w := COALESCE(prev_w, 0);
      UPDATE tenant_contracts
      SET initial_electric_reading = prev_e, initial_water_reading = prev_w
      WHERE id = c_rec.id;
    ELSE
      prev_e := COALESCE(c_rec.init_e, 0);
      prev_w := COALESCE(c_rec.init_w, 0);
    END IF;

    ym := first_m;
    WHILE ym <= last_util_m LOOP
      month_end := (ym + INTERVAL '1 month' - INTERVAL '1 day')::date;
      dim := EXTRACT(DAY FROM month_end)::int;
      mon := EXTRACT(MONTH FROM ym)::int;
      summer := CASE WHEN mon BETWEEN 4 AND 8 THEN 1 ELSE 0 END;
      p_start := GREATEST(c_rec.start_date, ym);
      p_end := LEAST(month_end, c_rec.end_date);
      billed := p_end - p_start + 1;

      IF c_rec.room_id IS NOT NULL THEN
        kwh := 65 + summer * 35 + (ck * 5 + mon * 11) % 20;
        m3  := 3 + (ck + mon) % 3;
      ELSE
        kwh := 170 + summer * 90 + (ck * 7 + mon * 13) % 45;
        m3  := 10 + (ck + mon * 3) % 6;
      END IF;
      IF billed < dim THEN
        kwh := GREATEST(round(kwh * billed / dim), 1);
        m3  := GREATEST(round(m3 * billed / dim), 1);
      END IF;

      IF p_end = c_rec.end_date THEN
        -- kỳ cuối khi trả nhà: chốt số + phát hành ngay ngày trả
        rec_ts := p_end + TIME '09:00';
        issue_ts := p_end + TIME '10:00';
        paid_ts := issue_ts + INTERVAL '2 hour';
      ELSE
        -- không chốt cuối tháng: quản lý chụp công tơ ngày 04 tháng sau, admin nhập giấy
        -- EVN / nước chiều cùng ngày → máy chủ phát hành cho khách theo bản chốt
        rec_ts := (month_end + 4) + TIME '08:30' + ((ck % 20)::int * INTERVAL '1 minute');
        issue_ts := (month_end + 4) + TIME '14:05';
        paid_ts := (issue_ts::date + (((ck + mon) % 3)::int + 1)) + TIME '20:00'
                   + ((ck % 50)::int * INTERVAL '1 minute');
      END IF;
      due := issue_ts::date + 5;

      -- điện
      INSERT INTO utility_invoices (
        property_id, room_id, tenant_contract_id, utility_type, billing_period,
        prev_reading, new_reading, consumption, unit_price, amount,
        status, sent_at, created_by, created_at, tenant_viewed_at
      ) VALUES (
        c_rec.property_id, c_rec.room_id, c_rec.id, 'ELECTRIC', to_char(ym, 'YYYY-MM'),
        prev_e, prev_e + kwh, kwh, c_rec.e_price, round(kwh * c_rec.e_price, 2),
        CASE WHEN paid_ts IS NOT NULL THEN 'PAID' ELSE 'SENT' END,
        issue_ts, c_rec.mgr, issue_ts, issue_ts + INTERVAL '3 hour'
      ) RETURNING id INTO ui_id;

      INSERT INTO meter_readings (property_id, room_id, utility_type, period, reading, prev_reading,
                                  recorded_at, recorded_by, utility_invoice_id)
      VALUES (c_rec.property_id, c_rec.room_id, 'ELECTRIC', to_char(ym, 'YYYY-MM'), prev_e + kwh, prev_e,
              rec_ts, c_rec.mgr, ui_id);

      PERFORM pg_temp.demo_invoice(
        'HD-ELE-' || c_rec.id || '-' || to_char(ym, 'YYYY-MM'),
        c_rec.tenant_user_id, c_rec.id, 'ELECTRICITY', NULL,
        c_rec.property_name, c_rec.room_label, ym, to_char(ym, 'YYYY-MM'),
        'Điện kỳ ' || to_char(ym, 'MM/YYYY') || ': ' || prev_e || ' → ' || (prev_e + kwh) || ' kWh',
        round(kwh * c_rec.e_price, 0), due, issue_ts, paid_ts, pay_method, c_rec.mgr,
        ui_id, kwh, c_rec.e_price, NULL, NULL);

      -- nước
      INSERT INTO utility_invoices (
        property_id, room_id, tenant_contract_id, utility_type, billing_period,
        prev_reading, new_reading, consumption, unit_price, amount,
        status, sent_at, created_by, created_at, tenant_viewed_at
      ) VALUES (
        c_rec.property_id, c_rec.room_id, c_rec.id, 'WATER', to_char(ym, 'YYYY-MM'),
        prev_w, prev_w + m3, m3, c_rec.w_price, round(m3 * c_rec.w_price, 2),
        CASE WHEN paid_ts IS NOT NULL THEN 'PAID' ELSE 'SENT' END,
        issue_ts + INTERVAL '1 minute', c_rec.mgr, issue_ts + INTERVAL '1 minute', issue_ts + INTERVAL '3 hour'
      ) RETURNING id INTO ui_id;

      INSERT INTO meter_readings (property_id, room_id, utility_type, period, reading, prev_reading,
                                  recorded_at, recorded_by, utility_invoice_id)
      VALUES (c_rec.property_id, c_rec.room_id, 'WATER', to_char(ym, 'YYYY-MM'), prev_w + m3, prev_w,
              rec_ts + INTERVAL '1 minute', c_rec.mgr, ui_id);

      PERFORM pg_temp.demo_invoice(
        'HD-WAT-' || c_rec.id || '-' || to_char(ym, 'YYYY-MM'),
        c_rec.tenant_user_id, c_rec.id, 'WATER', NULL,
        c_rec.property_name, c_rec.room_label, ym, to_char(ym, 'YYYY-MM'),
        'Nước kỳ ' || to_char(ym, 'MM/YYYY') || ': ' || prev_w || ' → ' || (prev_w + m3) || ' m³',
        round(m3 * c_rec.w_price, 0), due, issue_ts + INTERVAL '1 minute',
        paid_ts + INTERVAL '1 minute', pay_method, c_rec.mgr,
        ui_id, NULL, NULL, m3, c_rec.w_price);

      prev_e := prev_e + kwh;
      prev_w := prev_w + m3;
      ym := (ym + INTERVAL '1 month')::date;
    END LOOP;
  END LOOP;

  -- -------------------------------------------------------------------------
  -- 8b) Công tơ tổng nhà cho thuê theo phòng = tổng công tơ các phòng (kể cả phòng trống) + hành lang/cầu thang
  -- -------------------------------------------------------------------------
  FOR h_rec IN
    SELECT p.id AS pid, p.operation_manager_id AS mgr,
           date_trunc('month', p.manager_accepted_at)::date AS start_m
    FROM properties p
    WHERE p.id IN (p101, p102, p103, p104, p105, p106) AND NOT p.is_whole_house
  LOOP
    prev_e := 800 + (pg_temp.demo_k('P', h_rec.pid) % 7) * 37;
    prev_w := 60 + (pg_temp.demo_k('P', h_rec.pid) % 5) * 7;
    ym := h_rec.start_m;
    WHILE ym <= util_month LOOP
      mon := EXTRACT(MONTH FROM ym)::int;
      month_end := (ym + INTERVAL '1 month' - INTERVAL '1 day')::date;

      SELECT COALESCE(SUM(mr.reading - mr.prev_reading), 0) INTO kwh
      FROM meter_readings mr
      WHERE mr.property_id = h_rec.pid AND mr.room_id IS NOT NULL
        AND mr.utility_type = 'ELECTRIC' AND mr.period = to_char(ym, 'YYYY-MM');
      SELECT COALESCE(SUM(mr.reading - mr.prev_reading), 0) INTO m3
      FROM meter_readings mr
      WHERE mr.property_id = h_rec.pid AND mr.room_id IS NOT NULL
        AND mr.utility_type = 'WATER' AND mr.period = to_char(ym, 'YYYY-MM');
      kwh := kwh + 18 + (mon % 5) * 2;
      m3 := m3 + 2;

      INSERT INTO meter_readings (property_id, room_id, utility_type, period, reading, prev_reading,
                                  recorded_at, recorded_by, utility_invoice_id)
      VALUES (h_rec.pid, NULL, 'ELECTRIC', to_char(ym, 'YYYY-MM'), prev_e + kwh, prev_e,
              (month_end + 4) + TIME '08:00', h_rec.mgr, NULL),
             (h_rec.pid, NULL, 'WATER', to_char(ym, 'YYYY-MM'), prev_w + m3, prev_w,
              (month_end + 4) + TIME '08:01', h_rec.mgr, NULL);

      prev_e := prev_e + kwh;
      prev_w := prev_w + m3;
      ym := (ym + INTERVAL '1 month')::date;
    END LOOP;
  END LOOP;

  -- -------------------------------------------------------------------------
  -- 8c) Hoá đơn điện / nước của nhà (giấy EVN / cấp nước — utility_bills), mỗi kỳ 1 tờ / loại
  --     Chỉ số cũ → mới = công tơ tổng của nhà; phần khách chịu = tổng hoá đơn điện nước đã phát cho khách,
  --     phần còn lại (nhà trống, hành lang, chênh lệch bàn giao) công ty chịu.
  -- -------------------------------------------------------------------------
  FOR h_rec IN
    SELECT p.id AS pid, mr.utility_type, mr.period, mr.reading, mr.prev_reading,
           LAG(mr.reading) OVER (PARTITION BY p.id, mr.utility_type ORDER BY mr.period) AS prev_month_reading,
           CASE WHEN mr.utility_type = 'ELECTRIC'
                THEN COALESCE(p.electricity_unit_price, 3500)
                ELSE COALESCE(p.water_unit_price, 18000) END AS unit_price
    FROM properties p
    JOIN meter_readings mr ON mr.property_id = p.id AND mr.room_id IS NULL
    WHERE p.id IN (p101, p102, p103, p104, p105, p106)
    ORDER BY p.id, mr.utility_type, mr.period
  LOOP
    ym := to_date(h_rec.period, 'YYYY-MM');
    qty := h_rec.reading - COALESCE(h_rec.prev_month_reading, h_rec.prev_reading);

    SELECT COALESCE(SUM(ui.consumption), 0) INTO billed_qty
    FROM utility_invoices ui
    WHERE ui.property_id = h_rec.pid AND ui.utility_type = h_rec.utility_type
      AND ui.billing_period = h_rec.period AND ui.status <> 'CANCELLED';

    INSERT INTO utility_bills (
      property_id, type, billing_period, month, year, total_quantity, total_amount, unit_price,
      status, created_by, created_at, reading_deadline, billed_to_tenant_quantity, company_born_quantity
    ) VALUES (
      h_rec.pid, h_rec.utility_type, h_rec.period,
      EXTRACT(MONTH FROM ym)::int, EXTRACT(YEAR FROM ym)::int,
      qty::int, round(qty * h_rec.unit_price, 0), h_rec.unit_price,
      'PUBLISHED', admin_id, (ym + INTERVAL '1 month')::date + 3 + TIME '14:00', NULL,
      billed_qty, GREATEST(qty - billed_qty, 0)
    );
  END LOOP;

  -- -------------------------------------------------------------------------
  -- 9) Maintenance
  --    WEAR (hao mòn)            → công ty chịu, không hoá đơn khách.
  --    TENANT_MISUSE (lỗi khách) → hoá đơn MAINTENANCE cho khách.
  -- -------------------------------------------------------------------------

  -- 101-001 | An | Máy lạnh hết gas — hao mòn
  INSERT INTO maintenance_requests (
    request_code, tenant_id, property_id, room_id, tenant_contract_id, equipment_id,
    title, description, category, priority, status, flow_type, damage_cause,
    created_at, updated_at, acknowledged_at, repair_started_at, done_at, resolved_at,
    resolution_note, repair_description, invoice_vendor, invoice_number, invoice_date, invoice_amount,
    cost_agreement_status, is_deleted
  ) VALUES (
    'PBT-101-001', uid_t01, p101, NULL, c_an, eq_101_ac,
    'Máy lạnh phòng ngủ không lạnh', 'Máy lạnh chạy nhưng không lạnh, có tiếng kêu lạ.',
    'APPLIANCE', 'HIGH', 'CLOSED', 'NORMAL_WEAR', 'WEAR',
    '2025-06-02 09:15:00', '2025-06-05 17:00:00', '2025-06-02 11:00:00', '2025-06-05 14:00:00',
    '2025-06-05 16:30:00', '2025-06-05 17:00:00',
    'Nạp gas + vệ sinh dàn lạnh.', 'Nạp gas R32, vệ sinh dàn lạnh/dàn nóng.',
    'Điện lạnh Minh Phát', 'MP-2506-018', '2025-06-05', 850000,
    'NOT_APPLICABLE', false
  ) RETURNING id INTO mr_id;
  INSERT INTO equipment_maintenance_histories (equipment_id, maintenance_request_id, maintenance_date, repair_cost, note)
  VALUES (eq_101_ac, mr_id, '2025-06-05 16:30:00', 850000, 'Nạp gas + vệ sinh dàn lạnh');
  PERFORM pg_temp.demo_mr_step(mr_id, NULL, 'OPEN', 'Tenant tạo yêu cầu', uid_t01, 'Nguyễn Văn An', '2025-06-02 09:15:00');
  PERFORM pg_temp.demo_mr_step(mr_id, 'OPEN', 'IN_REPAIR', 'Manager nhận sửa — hao mòn, công ty chịu', uid_mgr1, 'Nguyễn Văn Hùng', '2025-06-02 11:00:00');
  PERFORM pg_temp.demo_mr_step(mr_id, 'IN_REPAIR', 'CLOSED', 'Hoàn tất', uid_mgr1, 'Nguyễn Văn Hùng', '2025-06-05 17:00:00');

  -- 101-002 | An | Tủ lạnh rách gioăng do cạy đá — LỖI KHÁCH → hoá đơn đã thanh toán
  INSERT INTO maintenance_requests (
    request_code, tenant_id, property_id, room_id, tenant_contract_id, equipment_id,
    title, description, category, priority, status, flow_type, damage_cause, fault_reason, fault_resolution_path,
    created_at, updated_at, acknowledged_at, repair_started_at, done_at, resolved_at,
    resolution_note, repair_description, invoice_vendor, invoice_number, invoice_date, invoice_amount,
    estimated_damage_amount, cost_agreement_status, is_deleted
  ) VALUES (
    'PBT-101-002', uid_t01, p101, NULL, c_an, eq_101_fr,
    'Tủ lạnh đóng tuyết ngăn đá', 'Ngăn đá đóng tuyết dày, cửa tủ đóng không kín.',
    'APPLIANCE', 'MEDIUM', 'CLOSED', 'TENANT_FAULT', 'TENANT_MISUSE',
    'Khách dùng dao cạy tuyết làm rách gioăng cửa ngăn đá.', 'MANAGER_REPAIR',
    '2025-11-18 08:00:00', '2025-11-21 19:00:00', '2025-11-18 10:00:00', '2025-11-20 13:30:00',
    '2025-11-20 14:30:00', '2025-11-21 19:00:00',
    'Thay gioăng cửa + xả tuyết. Khách đồng ý bồi thường.', 'Thay gioăng cửa ngăn đá, xả tuyết, test lạnh.',
    'Điện lạnh Minh Phát', 'MP-2511-042', '2025-11-20', 650000,
    650000, 'AGREED', false
  ) RETURNING id INTO mr_id;
  INSERT INTO equipment_maintenance_histories (equipment_id, maintenance_request_id, maintenance_date, repair_cost, note)
  VALUES (eq_101_fr, mr_id, '2025-11-20 14:30:00', 650000, 'Thay gioăng cửa tủ lạnh (khách bồi thường)');
  PERFORM pg_temp.demo_maint_charge(mr_id, 650000, '2025-11-20 14:40:00', '2025-11-21 19:00:00', 'QR');
  PERFORM pg_temp.demo_mr_step(mr_id, NULL, 'OPEN', 'Tenant tạo yêu cầu', uid_t01, 'Nguyễn Văn An', '2025-11-18 08:00:00');
  PERFORM pg_temp.demo_mr_step(mr_id, 'OPEN', 'REPAIR_SCHEDULED', 'Hẹn kiểm tra 18/11', uid_mgr1, 'Nguyễn Văn Hùng', '2025-11-18 10:00:00');
  PERFORM pg_temp.demo_mr_step(mr_id, 'REPAIR_SCHEDULED', 'TENANT_FAULT', 'Chẩn đoán: lỗi do khách (cạy tuyết rách gioăng) — khách đồng ý trả', uid_mgr1, 'Nguyễn Văn Hùng', '2025-11-19 09:00:00');
  PERFORM pg_temp.demo_mr_step(mr_id, 'TENANT_FAULT', 'WAITING_PAYMENT', 'Sửa xong — phát hành hoá đơn bồi thường 650.000đ', uid_mgr1, 'Nguyễn Văn Hùng', '2025-11-20 14:40:00');
  PERFORM pg_temp.demo_mr_step(mr_id, 'WAITING_PAYMENT', 'CLOSED', 'Khách đã thanh toán hoá đơn bảo trì', uid_t01, 'Nguyễn Văn An', '2025-11-21 19:00:00');

  -- 101-003 | An | Nóng lạnh rò van — hao mòn
  INSERT INTO maintenance_requests (
    request_code, tenant_id, property_id, room_id, tenant_contract_id, equipment_id,
    title, description, category, priority, status, flow_type, damage_cause,
    created_at, updated_at, acknowledged_at, repair_started_at, done_at, resolved_at,
    resolution_note, repair_description, invoice_vendor, invoice_number, invoice_date, invoice_amount,
    cost_agreement_status, is_deleted
  ) VALUES (
    'PBT-101-003', uid_t01, p101, NULL, c_an, eq_101_wh,
    'Máy nước nóng rò nước nhẹ', 'Nhỏ giọt dưới bình nóng lạnh.',
    'PLUMBING', 'MEDIUM', 'CLOSED', 'NORMAL_WEAR', 'WEAR',
    '2026-01-12 19:00:00', '2026-01-15 11:00:00', '2026-01-13 09:00:00', '2026-01-15 09:00:00',
    '2026-01-15 10:30:00', '2026-01-15 11:00:00',
    'Thay van một chiều.', 'Van một chiều mục theo thời gian — thay mới.',
    'Điện nước Thành Công', 'TC-2601-007', '2026-01-15', 420000,
    'NOT_APPLICABLE', false
  ) RETURNING id INTO mr_id;
  INSERT INTO equipment_maintenance_histories (equipment_id, maintenance_request_id, maintenance_date, repair_cost, note)
  VALUES (eq_101_wh, mr_id, '2026-01-15 10:30:00', 420000, 'Thay van một chiều');
  PERFORM pg_temp.demo_mr_step(mr_id, NULL, 'OPEN', 'Tenant tạo yêu cầu', uid_t01, 'Nguyễn Văn An', '2026-01-12 19:00:00');
  PERFORM pg_temp.demo_mr_step(mr_id, 'OPEN', 'IN_REPAIR', 'Manager nhận sửa — hao mòn, công ty chịu', uid_mgr1, 'Nguyễn Văn Hùng', '2026-01-13 09:00:00');
  PERFORM pg_temp.demo_mr_step(mr_id, 'IN_REPAIR', 'CLOSED', 'Hoàn tất', uid_mgr1, 'Nguyễn Văn Hùng', '2026-01-15 11:00:00');

  -- 102-001 | Bình | Quạt kêu — hao mòn
  INSERT INTO maintenance_requests (
    request_code, tenant_id, property_id, room_id, tenant_contract_id, equipment_id,
    title, description, category, priority, status, flow_type, damage_cause,
    created_at, updated_at, acknowledged_at, repair_started_at, done_at, resolved_at,
    resolution_note, repair_description, invoice_vendor, invoice_number, invoice_date, invoice_amount,
    cost_agreement_status, is_deleted
  ) VALUES (
    'PBT-102-001', uid_t02, p102, NULL, c_binh, eq_102_fn,
    'Quạt đứng kêu to khi quay', 'Quạt rung khi tốc độ cao.',
    'APPLIANCE', 'LOW', 'CLOSED', 'NORMAL_WEAR', 'WEAR',
    '2026-06-03 10:00:00', '2026-06-05 16:30:00', '2026-06-03 14:00:00', '2026-06-05 15:00:00',
    '2026-06-05 16:00:00', '2026-06-05 16:30:00',
    'Bôi trơn bạc đạn.', 'Bạc đạn khô dầu — vệ sinh, tra dầu.',
    'Điện máy Hoà Bình', 'HB-2606-003', '2026-06-05', 150000,
    'NOT_APPLICABLE', false
  ) RETURNING id INTO mr_id;
  INSERT INTO equipment_maintenance_histories (equipment_id, maintenance_request_id, maintenance_date, repair_cost, note)
  VALUES (eq_102_fn, mr_id, '2026-06-05 16:00:00', 150000, 'Bôi trơn bạc đạn quạt');
  PERFORM pg_temp.demo_mr_step(mr_id, NULL, 'OPEN', 'Tenant tạo yêu cầu', uid_t02, 'Trần Thị Bình', '2026-06-03 10:00:00');
  PERFORM pg_temp.demo_mr_step(mr_id, 'OPEN', 'IN_REPAIR', 'Manager nhận sửa — hao mòn, công ty chịu', uid_mgr1, 'Nguyễn Văn Hùng', '2026-06-03 14:00:00');
  PERFORM pg_temp.demo_mr_step(mr_id, 'IN_REPAIR', 'CLOSED', 'Hoàn tất', uid_mgr1, 'Nguyễn Văn Hùng', '2026-06-05 16:30:00');

  -- 102-002 | Bình | Nóng lạnh hỏng thanh đốt — hao mòn
  INSERT INTO maintenance_requests (
    request_code, tenant_id, property_id, room_id, tenant_contract_id, equipment_id,
    title, description, category, priority, status, flow_type, damage_cause,
    created_at, updated_at, acknowledged_at, repair_started_at, done_at, resolved_at,
    resolution_note, repair_description, invoice_vendor, invoice_number, invoice_date, invoice_amount,
    cost_agreement_status, is_deleted
  ) VALUES (
    'PBT-102-002', uid_t02, p102, NULL, c_binh, eq_102_wh,
    'Nóng lạnh không ra nước nóng', 'Bật máy nhưng nước vẫn lạnh.',
    'ELECTRICAL', 'HIGH', 'CLOSED', 'NORMAL_WEAR', 'WEAR',
    '2026-04-16 07:30:00', '2026-04-18 12:00:00', '2026-04-16 09:00:00', '2026-04-18 10:00:00',
    '2026-04-18 11:30:00', '2026-04-18 12:00:00',
    'Thay thanh đốt.', 'Thanh đốt cháy do đóng cặn — thay mới.',
    'Điện nước Thành Công', 'TC-2604-021', '2026-04-18', 780000,
    'NOT_APPLICABLE', false
  ) RETURNING id INTO mr_id;
  INSERT INTO equipment_maintenance_histories (equipment_id, maintenance_request_id, maintenance_date, repair_cost, note)
  VALUES (eq_102_wh, mr_id, '2026-04-18 11:30:00', 780000, 'Thay thanh đốt nóng lạnh');
  PERFORM pg_temp.demo_mr_step(mr_id, NULL, 'OPEN', 'Tenant tạo yêu cầu', uid_t02, 'Trần Thị Bình', '2026-04-16 07:30:00');
  PERFORM pg_temp.demo_mr_step(mr_id, 'OPEN', 'IN_REPAIR', 'Manager nhận sửa — hao mòn, công ty chịu', uid_mgr1, 'Nguyễn Văn Hùng', '2026-04-16 09:00:00');
  PERFORM pg_temp.demo_mr_step(mr_id, 'IN_REPAIR', 'CLOSED', 'Hoàn tất', uid_mgr1, 'Nguyễn Văn Hùng', '2026-04-18 12:00:00');

  -- 102-003 | Bình | Tủ quần áo gãy bản lề do treo quá tải — LỖI KHÁCH → hoá đơn CHỜ THANH TOÁN (demo trả tiền)
  INSERT INTO maintenance_requests (
    request_code, tenant_id, property_id, room_id, tenant_contract_id, equipment_id,
    title, description, category, priority, status, flow_type, damage_cause, fault_reason, fault_resolution_path,
    created_at, updated_at, acknowledged_at, repair_started_at, done_at,
    resolution_note, repair_description, invoice_vendor, invoice_number, invoice_date, invoice_amount,
    estimated_damage_amount, cost_agreement_status, is_deleted
  ) VALUES (
    'PBT-102-003', uid_t02, p102, NULL, c_binh, eq_102_wd,
    'Cánh tủ quần áo bị bung bản lề', 'Cánh trái tủ quần áo bung khỏi bản lề, không đóng được.',
    'FURNITURE', 'MEDIUM', 'WAITING_PAYMENT', 'TENANT_FAULT', 'TENANT_MISUSE',
    'Khách đu/treo đồ nặng lên cánh tủ làm gãy 2 bản lề và nứt ván.', 'MANAGER_REPAIR',
    '2026-09-27 20:00:00', '2026-09-30 14:40:00', '2026-09-28 08:30:00', '2026-09-30 13:00:00',
    '2026-09-30 14:30:00',
    'Thay 2 bản lề + nẹp ván cánh tủ. Khách đồng ý bồi thường.', 'Thay bản lề giảm chấn, bắt nẹp gia cố cánh tủ.',
    'Nội thất Phú Gia', 'PG-2609-115', '2026-09-30', 600000,
    600000, 'AGREED', false
  ) RETURNING id INTO mr_id;
  INSERT INTO equipment_maintenance_histories (equipment_id, maintenance_request_id, maintenance_date, repair_cost, note)
  VALUES (eq_102_wd, mr_id, '2026-09-30 14:30:00', 600000, 'Thay bản lề tủ quần áo (khách bồi thường)');
  PERFORM pg_temp.demo_maint_charge(mr_id, 600000, '2026-09-30 14:40:00', NULL, 'QR');
  PERFORM pg_temp.demo_mr_step(mr_id, NULL, 'OPEN', 'Tenant tạo yêu cầu', uid_t02, 'Trần Thị Bình', '2026-09-27 20:00:00');
  PERFORM pg_temp.demo_mr_step(mr_id, 'OPEN', 'REPAIR_SCHEDULED', 'Hẹn kiểm tra 28/09', uid_mgr1, 'Nguyễn Văn Hùng', '2026-09-28 08:30:00');
  PERFORM pg_temp.demo_mr_step(mr_id, 'REPAIR_SCHEDULED', 'TENANT_FAULT', 'Chẩn đoán: lỗi do khách (treo đồ quá tải) — khách đồng ý trả', uid_mgr1, 'Nguyễn Văn Hùng', '2026-09-28 10:00:00');
  PERFORM pg_temp.demo_mr_step(mr_id, 'TENANT_FAULT', 'WAITING_PAYMENT', 'Sửa xong — phát hành hoá đơn bồi thường 600.000đ (hạn 05/10)', uid_mgr1, 'Nguyễn Văn Hùng', '2026-09-30 14:40:00');

  -- 104-001 | Dung | Máy lạnh chảy nước — OPEN (chưa chẩn đoán)
  INSERT INTO maintenance_requests (
    request_code, tenant_id, property_id, room_id, tenant_contract_id, equipment_id,
    title, description, category, priority, status,
    created_at, updated_at, is_deleted
  ) VALUES (
    'PBT-104-001', uid_t04, p104, r104_p101, c_dung, eq_104_ac,
    'Máy lạnh P101 chảy nước vào phòng',
    'Ống thoát nước tắc, nước chảy xuống giường.',
    'APPLIANCE', 'URGENT', 'OPEN',
    '2026-10-01 21:00:00', '2026-10-01 21:00:00', false
  ) RETURNING id INTO mr_id;
  PERFORM pg_temp.demo_mr_step(mr_id, NULL, 'OPEN', 'Tenant báo khẩn', uid_t04, 'Phạm Thị Dung', '2026-10-01 21:00:00');

  -- 106-001 | Em (HĐ cũ) | Máy giặt kẹt bơm xả do để đồ trong túi quần — LỖI KHÁCH → hoá đơn đã thanh toán
  INSERT INTO maintenance_requests (
    request_code, tenant_id, property_id, room_id, tenant_contract_id, equipment_id,
    title, description, category, priority, status, flow_type, damage_cause, fault_reason, fault_resolution_path,
    created_at, updated_at, acknowledged_at, repair_started_at, done_at, resolved_at,
    resolution_note, repair_description, invoice_vendor, invoice_number, invoice_date, invoice_amount,
    estimated_damage_amount, cost_agreement_status, is_deleted
  ) VALUES (
    'PBT-106-001', uid_t05, p106, NULL, c_em_old, eq_106_wm,
    'Máy giặt không xả nước', 'Chu trình dừng ở bước xả.',
    'APPLIANCE', 'HIGH', 'CLOSED', 'TENANT_FAULT', 'TENANT_MISUSE',
    'Đồng xu + kẹp tóc trong túi quần kẹt cánh bơm xả.', 'MANAGER_REPAIR',
    '2024-09-01 08:00:00', '2024-09-04 20:00:00', '2024-09-01 10:00:00', '2024-09-03 14:00:00',
    '2024-09-03 16:30:00', '2024-09-04 20:00:00',
    'Thông ống xả + thay phin lọc. Khách đồng ý bồi thường.', 'Tháo bơm xả lấy dị vật, thay phin lọc.',
    'Điện máy Hoà Bình', 'HB-2409-011', '2024-09-03', 550000,
    550000, 'AGREED', false
  ) RETURNING id INTO mr_id;
  INSERT INTO equipment_maintenance_histories (equipment_id, maintenance_request_id, maintenance_date, repair_cost, note)
  VALUES (eq_106_wm, mr_id, '2024-09-03 16:30:00', 550000, 'Thông ống xả máy giặt (khách bồi thường)');
  PERFORM pg_temp.demo_maint_charge(mr_id, 550000, '2024-09-03 16:40:00', '2024-09-04 20:00:00', 'CASH');
  PERFORM pg_temp.demo_mr_step(mr_id, NULL, 'OPEN', 'Tenant tạo yêu cầu', uid_t05, 'Hoàng Văn Em', '2024-09-01 08:00:00');
  PERFORM pg_temp.demo_mr_step(mr_id, 'OPEN', 'REPAIR_SCHEDULED', 'Hẹn kiểm tra 01/09', uid_mgr1, 'Nguyễn Văn Hùng', '2024-09-01 10:00:00');
  PERFORM pg_temp.demo_mr_step(mr_id, 'REPAIR_SCHEDULED', 'TENANT_FAULT', 'Chẩn đoán: dị vật trong bơm xả — lỗi do khách, khách đồng ý trả', uid_mgr1, 'Nguyễn Văn Hùng', '2024-09-02 09:00:00');
  PERFORM pg_temp.demo_mr_step(mr_id, 'TENANT_FAULT', 'WAITING_PAYMENT', 'Sửa xong — phát hành hoá đơn bồi thường 550.000đ', uid_mgr1, 'Nguyễn Văn Hùng', '2024-09-03 16:40:00');
  PERFORM pg_temp.demo_mr_step(mr_id, 'WAITING_PAYMENT', 'CLOSED', 'Đã thu tiền mặt', uid_mgr1, 'Nguyễn Văn Hùng', '2024-09-04 20:00:00');

  -- 106-002 | Huy | Máy lạnh yếu — hao mòn
  INSERT INTO maintenance_requests (
    request_code, tenant_id, property_id, room_id, tenant_contract_id, equipment_id,
    title, description, category, priority, status, flow_type, damage_cause,
    created_at, updated_at, acknowledged_at, repair_started_at, done_at, resolved_at,
    resolution_note, repair_description, invoice_vendor, invoice_number, invoice_date, invoice_amount,
    cost_agreement_status, is_deleted
  ) VALUES (
    'PBT-106-002', uid_t07, p106, NULL, c_huy, eq_106_ac,
    'Máy lạnh yếu hơi lạnh', 'Phòng ngủ lâu mới mát.',
    'APPLIANCE', 'MEDIUM', 'CLOSED', 'NORMAL_WEAR', 'WEAR',
    '2026-08-01 09:00:00', '2026-08-03 16:00:00', '2026-08-01 11:00:00', '2026-08-03 14:00:00',
    '2026-08-03 15:30:00', '2026-08-03 16:00:00',
    'Vệ sinh dàn lạnh.', 'Dàn lạnh bám bụi — vệ sinh định kỳ.',
    'Điện lạnh Minh Phát', 'MP-2608-009', '2026-08-03', 400000,
    'NOT_APPLICABLE', false
  ) RETURNING id INTO mr_id;
  INSERT INTO equipment_maintenance_histories (equipment_id, maintenance_request_id, maintenance_date, repair_cost, note)
  VALUES (eq_106_ac, mr_id, '2026-08-03 15:30:00', 400000, 'Vệ sinh dàn lạnh');
  PERFORM pg_temp.demo_mr_step(mr_id, NULL, 'OPEN', 'Tenant tạo yêu cầu', uid_t07, 'Đặng Quốc Huy', '2026-08-01 09:00:00');
  PERFORM pg_temp.demo_mr_step(mr_id, 'OPEN', 'IN_REPAIR', 'Manager nhận sửa — hao mòn, công ty chịu', uid_mgr1, 'Nguyễn Văn Hùng', '2026-08-01 11:00:00');
  PERFORM pg_temp.demo_mr_step(mr_id, 'IN_REPAIR', 'CLOSED', 'Hoàn tất', uid_mgr1, 'Nguyễn Văn Hùng', '2026-08-03 16:00:00');

  -- 106-003 | Huy | Vòi lavabo gãy do va đập — LỖI KHÁCH → hoá đơn đã thanh toán
  INSERT INTO maintenance_requests (
    request_code, tenant_id, property_id, room_id, tenant_contract_id, equipment_id,
    title, description, category, priority, status, flow_type, damage_cause, fault_reason, fault_resolution_path,
    created_at, updated_at, acknowledged_at, repair_started_at, done_at, resolved_at,
    resolution_note, repair_description, invoice_vendor, invoice_number, invoice_date, invoice_amount,
    estimated_damage_amount, cost_agreement_status, is_deleted
  ) VALUES (
    'PBT-106-003', uid_t07, p106, NULL, c_huy, NULL,
    'Vòi lavabo phòng tắm bị gãy', 'Thân vòi lavabo gãy ở khớp nối, nước rò liên tục.',
    'PLUMBING', 'HIGH', 'CLOSED', 'TENANT_FAULT', 'TENANT_MISUSE',
    'Khách làm rơi vật nặng vào vòi, gãy thân vòi.', 'MANAGER_REPAIR',
    '2026-08-20 18:00:00', '2026-08-23 09:00:00', '2026-08-21 09:00:00', '2026-08-22 10:00:00',
    '2026-08-22 11:30:00', '2026-08-23 09:00:00',
    'Thay vòi lavabo mới. Khách đồng ý bồi thường.', 'Thay bộ vòi lavabo + dây cấp.',
    'Điện nước Thành Công', 'TC-2608-033', '2026-08-22', 480000,
    480000, 'AGREED', false
  ) RETURNING id INTO mr_id;
  PERFORM pg_temp.demo_maint_charge(mr_id, 480000, '2026-08-22 11:40:00', '2026-08-23 09:00:00', 'QR');
  PERFORM pg_temp.demo_mr_step(mr_id, NULL, 'OPEN', 'Tenant tạo yêu cầu', uid_t07, 'Đặng Quốc Huy', '2026-08-20 18:00:00');
  PERFORM pg_temp.demo_mr_step(mr_id, 'OPEN', 'REPAIR_SCHEDULED', 'Hẹn kiểm tra 21/08', uid_mgr1, 'Nguyễn Văn Hùng', '2026-08-21 09:00:00');
  PERFORM pg_temp.demo_mr_step(mr_id, 'REPAIR_SCHEDULED', 'TENANT_FAULT', 'Chẩn đoán: va đập làm gãy vòi — lỗi do khách, khách đồng ý trả', uid_mgr1, 'Nguyễn Văn Hùng', '2026-08-21 10:00:00');
  PERFORM pg_temp.demo_mr_step(mr_id, 'TENANT_FAULT', 'WAITING_PAYMENT', 'Sửa xong — phát hành hoá đơn bồi thường 480.000đ', uid_mgr1, 'Nguyễn Văn Hùng', '2026-08-22 11:40:00');
  PERFORM pg_temp.demo_mr_step(mr_id, 'WAITING_PAYMENT', 'CLOSED', 'Khách đã thanh toán hoá đơn bảo trì', uid_t07, 'Đặng Quốc Huy', '2026-08-23 09:00:00');

  -- mã phiếu giống backend: M-<id>
  UPDATE maintenance_requests SET request_code = 'M-' || id WHERE request_code LIKE 'PBT-%';

  -- -------------------------------------------------------------------------
  -- 10) Host expenses (PnL) — chi phí sửa chữa công ty trả cho thợ + phí quản lý
  -- -------------------------------------------------------------------------
  INSERT INTO host_expenses (property_id, category, amount, month, note, created_at) VALUES
    (p106, 'MAINTENANCE', 550000,  '2024-09', 'Sửa máy giặt (thu lại từ khách)', '2024-09-03 17:00:00'),
    (p101, 'MAINTENANCE', 850000,  '2025-06', 'Sửa máy lạnh (hao mòn)',         '2025-06-05 17:00:00'),
    (p101, 'MAINTENANCE', 650000,  '2025-11', 'Sửa tủ lạnh (thu lại từ khách)', '2025-11-20 15:00:00'),
    (p101, 'MAINTENANCE', 420000,  '2026-01', 'Thay van nóng lạnh (hao mòn)',   '2026-01-15 11:00:00'),
    (p102, 'MAINTENANCE', 780000,  '2026-04', 'Thay thanh đốt nóng lạnh (hao mòn)', '2026-04-18 12:00:00'),
    (p102, 'MAINTENANCE', 150000,  '2026-06', 'Bảo dưỡng quạt (hao mòn)',       '2026-06-05 16:30:00'),
    (p106, 'MAINTENANCE', 400000,  '2026-08', 'Vệ sinh máy lạnh (hao mòn)',     '2026-08-03 16:00:00'),
    (p106, 'MAINTENANCE', 480000,  '2026-08', 'Thay vòi lavabo (thu lại từ khách)', '2026-08-22 12:00:00'),
    (p102, 'MAINTENANCE', 600000,  '2026-09', 'Sửa tủ quần áo (chờ khách trả)', '2026-09-30 15:00:00'),
    (p101, 'MANAGEMENT',  2000000, '2026-09', 'Phí quản lý tháng 9',                 '2026-09-30 18:00:00'),
    (p106, 'MANAGEMENT',  2500000, '2026-09', 'Phí quản lý tháng 9',                 '2026-09-30 18:00:00');

  RAISE NOTICE '======= SEED OK (mốc bảo vệ 04/10/2026) =======';
  RAISE NOTICE 'Login (123456): owner01 / manager01 / manager02 / demo_tenant01..10';
  RAISE NOTICE 'Nhà mới: MTX#% .. MTX#% (mã KH điện PE05150000110..115 / nước 15015000110..115); công tơ + giấy EVN/nước đủ đến kỳ 08/2026 (chụp số 04/09)',
    mtx_base + 1, mtx_base + 6;
  RAISE NOTICE 'An 2 năm, Bình 1 năm, Cường mới, Dung hết HĐ 31/10, Em thuê lại, nhà 6 có 3 đời khách; nhà 4 P102 Giang, P103 Khánh; nhà 5 R202 Linh';
  RAISE NOTICE 'Điện nước kỳ 08 đã trả hết. Tiền nhà/DV 10/2026: An, Em, Giang đã trả; Bình/Cường/Dung/Huy/Khánh/Linh PENDING. Bình có hoá đơn bảo trì chờ trả.';
END $$;

COMMIT;


-- Seed lại / gỡ data demo: chạy scripts/capstone-defense-demo-cleanup.sql trước.

-- -----------------------------------------------------------------------------
-- FREEZE KEYS — chạy trên DB đang có số muốn giữ, dán kết quả đè lên khối
-- INSERT INTO pg_temp.demo_key ở đầu file.
-- -----------------------------------------------------------------------------
-- SELECT string_agg(format('  (%L, %L, %s)', kind, code, id), E',\n' ORDER BY ord, code) || ';'
-- FROM (
--   SELECT 1 AS ord, 'P' AS kind, upper(trim(electricity_customer_code)) AS code, id FROM properties
--   WHERE upper(trim(electricity_customer_code)) BETWEEN 'PE05150000110' AND 'PE05150000115'
--   UNION ALL
--   SELECT 2, 'R', electric_meter_code, id FROM rooms
--   WHERE electric_meter_code IN ('CTD-104-P101', 'CTD-104-P102', 'CTD-104-P103', 'CTD-105-R201', 'CTD-105-R202')
--   UNION ALL
--   SELECT 3, 'C', contract_code, id FROM tenant_contracts
--   WHERE contract_code IN ('HDT-2024-0101', 'HDT-2025-0102', 'HDT-2026-0103', 'HDT-2025-0104', 'HDT-2024-0105',
--                           'HDT-2026-0106', 'HDT-2023-0107', 'HDT-2026-0108', 'HDT-2025-0109', 'HDT-2025-0110',
--                           'HDT-2026-0111')
-- ) s;
