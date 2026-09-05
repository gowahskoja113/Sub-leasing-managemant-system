-- Backfill zone_managers từ property.operation_manager_id
-- Chỉ ghi khu vực đang có đúng 1 quản lý trên mọi nhà đã gán.
-- Khu vực lẫn nhiều quản lý bỏ qua — host tự chốt.

INSERT INTO zone_managers (zone_id, manager_id, assigned_at)
SELECT p.zone_id, MIN(p.operation_manager_id), NOW()
FROM properties p
WHERE p.operation_manager_id IS NOT NULL
GROUP BY p.zone_id
HAVING COUNT(DISTINCT p.operation_manager_id) = 1
ON CONFLICT (zone_id) DO NOTHING;
