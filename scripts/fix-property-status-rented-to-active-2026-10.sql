-- Nhà chỉ ở RENTED do seed demo cũ gán (luồng thật không bao giờ gán PropertyStatus.RENTED;
-- nhà đang kinh doanh luôn là ACTIVE) → đưa về ACTIVE để khớp luồng thật và cải tạo lại được.
-- Chỉ đổi status của properties; rooms.status = 'RENTED' là hợp lệ, KHÔNG đụng.
-- Chạy: psql ... -f scripts/fix-property-status-rented-to-active-2026-10.sql

BEGIN;

SELECT id, property_code, property_name, is_whole_house, status
FROM properties
WHERE status = 'RENTED'
ORDER BY id;

UPDATE properties
SET status = 'ACTIVE'
WHERE status = 'RENTED';

COMMIT;
