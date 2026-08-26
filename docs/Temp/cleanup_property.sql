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

-- --- con của tenant_invoices (phải trước khi xoá tenant_invoices) ---
DELETE FROM tenant_payments WHERE tenant_invoice_id IN (SELECT id FROM tenant_invoices WHERE tenant_contract_id IN (SELECT id FROM tenant_contracts WHERE property_id = :pid));
DELETE FROM tenant_invoice_payos_orders WHERE invoice_id IN (SELECT id FROM tenant_invoices WHERE tenant_contract_id IN (SELECT id FROM tenant_contracts WHERE property_id = :pid));
DELETE FROM invoice_disputes WHERE tenant_contract_id IN (SELECT id FROM tenant_contracts WHERE property_id = :pid);
DELETE FROM tenant_payment_claims WHERE tenant_invoice_id IN (SELECT id FROM tenant_invoices WHERE tenant_contract_id IN (SELECT id FROM tenant_contracts WHERE property_id = :pid));

-- --- con của tenant_contracts ---
DELETE FROM checkout_settlement_invoices WHERE checkout_settlement_id IN (SELECT s.id FROM checkout_settlements s JOIN checkout_requests r ON s.checkout_request_id = r.id WHERE r.tenant_contract_id IN (SELECT id FROM tenant_contracts WHERE property_id = :pid));
DELETE FROM checkout_settlement_adjustments WHERE checkout_settlement_id IN (SELECT s.id FROM checkout_settlements s JOIN checkout_requests r ON s.checkout_request_id = r.id WHERE r.tenant_contract_id IN (SELECT id FROM tenant_contracts WHERE property_id = :pid));
DELETE FROM checkout_damage_items WHERE checkout_inspection_id IN (SELECT i.id FROM checkout_inspections i JOIN checkout_requests r ON i.checkout_request_id = r.id WHERE r.tenant_contract_id IN (SELECT id FROM tenant_contracts WHERE property_id = :pid));
DELETE FROM checkout_inspection_photos WHERE inspection_id IN (SELECT i.id FROM checkout_inspections i JOIN checkout_requests r ON i.checkout_request_id = r.id WHERE r.tenant_contract_id IN (SELECT id FROM tenant_contracts WHERE property_id = :pid));
DELETE FROM checkout_settlements WHERE checkout_request_id IN (SELECT id FROM checkout_requests WHERE tenant_contract_id IN (SELECT id FROM tenant_contracts WHERE property_id = :pid));
DELETE FROM checkout_inspections WHERE checkout_request_id IN (SELECT id FROM checkout_requests WHERE tenant_contract_id IN (SELECT id FROM tenant_contracts WHERE property_id = :pid));
DELETE FROM checkout_request_dispute_photos WHERE checkout_request_id IN (SELECT id FROM checkout_requests WHERE tenant_contract_id IN (SELECT id FROM tenant_contracts WHERE property_id = :pid));
DELETE FROM checkout_requests WHERE tenant_contract_id IN (SELECT id FROM tenant_contracts WHERE property_id = :pid);
DELETE FROM tenant_pending_charges WHERE tenant_contract_id IN (SELECT id FROM tenant_contracts WHERE property_id = :pid);
DELETE FROM household_members WHERE tenant_contract_id IN (SELECT id FROM tenant_contracts WHERE property_id = :pid);
DELETE FROM tenant_contract_equipments WHERE tenant_contract_id IN (SELECT id FROM tenant_contracts WHERE property_id = :pid);
DELETE FROM tenant_contract_condition_photos WHERE tenant_contract_id IN (SELECT id FROM tenant_contracts WHERE property_id = :pid);

-- --- bảo trì (con trước, cha sau) ---
DELETE FROM maintenance_images WHERE maintenance_request_id IN (SELECT id FROM maintenance_requests WHERE property_id = :pid);
DELETE FROM maintenance_timelines WHERE maintenance_request_id IN (SELECT id FROM maintenance_requests WHERE property_id = :pid);
DELETE FROM maintenance_history WHERE maintenance_request_id IN (SELECT id FROM maintenance_requests WHERE property_id = :pid);
DELETE FROM equipment_maintenance_histories WHERE maintenance_request_id IN (SELECT id FROM maintenance_requests WHERE property_id = :pid) OR equipment_id IN (SELECT id FROM equipments WHERE property_id = :pid);
DELETE FROM maintenance_requests WHERE property_id = :pid;

-- --- điện nước / tài chính / lead theo property ---
DELETE FROM utility_invoices WHERE property_id = :pid;
DELETE FROM utility_bills WHERE property_id = :pid;
DELETE FROM meter_readings WHERE property_id = :pid;
DELETE FROM invoices WHERE property_id = :pid;
DELETE FROM expenses WHERE property_id = :pid;
DELETE FROM host_expenses WHERE property_id = :pid;
DELETE FROM master_leases WHERE property_id = :pid;
DELETE FROM viewing_lead_properties WHERE property_id = :pid;

-- --- Hợp đồng thuê ---
DELETE FROM tenant_invoices WHERE tenant_contract_id IN (SELECT id FROM tenant_contracts WHERE property_id = :pid);
DELETE FROM tenant_contracts WHERE property_id = :pid;

-- 2) Thiết bị (property_id, room_id, manifest_id)
DELETE FROM handover_equipments WHERE property_id = :pid;
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
