-- Phòng kẹt DRAFT sau khi host duyệt + tự gán quản lý khu vực (nhà đã ACTIVE).
-- Cột xóa mềm là is_deleted (không phải deleted).
--
-- Chỉ sửa khi nhà ACTIVE và đã có quản lý — không đụng nhà DRAFT / PENDING_OPERATION_MANAGER.

-- Xem trước danh sách bị ảnh hưởng
-- SELECT p.id, p.code, p.status AS property_status, r.id AS room_id, r.room_number, r.status
-- FROM rooms r
-- JOIN properties p ON p.id = r.property_id
-- WHERE r.status = 'DRAFT'
--   AND p.status = 'ACTIVE'
--   AND p.operation_manager_id IS NOT NULL
--   AND r.is_deleted = false
-- ORDER BY p.code, r.room_number;

-- Nhà chia phòng: đã có giá duyệt, nhà đang khai thác.
UPDATE rooms r
SET status = 'AVAILABLE'
FROM properties p
WHERE r.property_id = p.id
  AND p.status = 'ACTIVE'
  AND p.operation_manager_id IS NOT NULL
  AND r.status = 'DRAFT'
  AND r.price IS NOT NULL
  AND r.is_deleted = false;

-- Nhà nguyên căn: giá nằm ở properties, phòng DRAFT vẫn phải AVAILABLE khi nhà ACTIVE.
UPDATE rooms r
SET status = 'AVAILABLE'
FROM properties p
WHERE r.property_id = p.id
  AND p.status = 'ACTIVE'
  AND p.operation_manager_id IS NOT NULL
  AND COALESCE(p.whole_house, false) = true
  AND r.status = 'DRAFT'
  AND r.is_deleted = false;
