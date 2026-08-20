-- Phòng kẹt DRAFT sau khi host duyệt + tự gán quản lý khu vực (nhà đã ACTIVE).
-- Cột xóa mềm là is_deleted (không phải deleted).

-- Nhà chia phòng: đã có giá duyệt, nhà đang khai thác.
UPDATE rooms r
SET status = 'AVAILABLE'
FROM properties p
WHERE r.property_id = p.id
  AND p.status = 'ACTIVE'
  AND r.status = 'DRAFT'
  AND r.price IS NOT NULL
  AND r.is_deleted = false;

-- Nhà nguyên căn: giá nằm ở properties, phòng DRAFT vẫn phải AVAILABLE khi nhà ACTIVE.
UPDATE rooms r
SET status = 'AVAILABLE'
FROM properties p
WHERE r.property_id = p.id
  AND p.status = 'ACTIVE'
  AND COALESCE(p.whole_house, false) = true
  AND r.status = 'DRAFT'
  AND r.is_deleted = false;
