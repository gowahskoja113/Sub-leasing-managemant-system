-- Xóa cứng toàn bộ dữ liệu của một căn nhà (bỏ qua Hibernate).
-- Dùng khi property mồ côi do import sai hoặc cần dọn tay trên DB.
--
-- Cách chạy:
--   psql -h localhost -U postgres -d slms2026_db -v pid=19 -f docs/Temp/cleanup_property.sql
--
-- Đổi :pid thành property_id thực tế.

BEGIN;

-- 1) Khấu hao (ref inbound_contract_id + room_id)
DELETE FROM depreciation_results
 WHERE room_id IN (SELECT id FROM rooms WHERE property_id = :pid)
    OR inbound_contract_id IN (SELECT id FROM inbound_contracts WHERE property_id = :pid);

-- 2) Thiết bị (property_id, room_id, manifest_id)
DELETE FROM equipments WHERE property_id = :pid;

-- 3) Chỉ số điện/nước
DELETE FROM monthly_readings WHERE property_id = :pid;

-- 4) Hợp đồng đầu vào (sau khấu hao)
DELETE FROM inbound_contracts WHERE property_id = :pid;

-- 5) Cải tạo: dòng trước, đợt sau (renovation_lines.session_id → renovation_sessions)
DELETE FROM renovation_lines WHERE property_id = :pid;
DELETE FROM renovation_sessions WHERE property_id = :pid;

-- 6) Manifest thiết bị (sau equipments)
DELETE FROM equipment_manifests WHERE property_id = :pid;

-- 7) Ảnh
DELETE FROM property_images WHERE property_id = :pid;

-- 8) Phòng
DELETE FROM rooms WHERE property_id = :pid;

-- 9) Căn nhà
DELETE FROM properties WHERE id = :pid;

COMMIT;
