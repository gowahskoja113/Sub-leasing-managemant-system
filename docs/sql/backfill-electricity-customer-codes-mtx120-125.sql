-- Production: gắn mã khách hàng điện cho nhà MTX#120–#125
-- Nguồn: SLMS2026_import_khoi_tao_nha_120_125.xlsx
-- Chuẩn hoá: chỉ chữ-số lowercase (UtilityCustomerCodeHelper.normalize)
--
-- #120–#122 → pb05000222239
-- #123–#125 → pb06060046628
--
-- Lưu ý: nếu còn unique index cũ trên electricity_customer_code thì DROP trước
-- (nhiều nhà được phép chung một mã KH điện).

ALTER TABLE properties ADD COLUMN IF NOT EXISTS electricity_customer_code VARCHAR(64);
ALTER TABLE properties ADD COLUMN IF NOT EXISTS water_customer_code VARCHAR(64);

DROP INDEX IF EXISTS uq_properties_electricity_customer_code;
DROP INDEX IF EXISTS uq_properties_water_customer_code;

-- Khớp theo property_code (ưu tiên) hoặc token đầu property_name
UPDATE properties
SET electricity_customer_code = 'pb05000222239'
WHERE lower(property_code) IN ('mtx#120', 'mtx#121', 'mtx#122')
   OR lower(split_part(property_name, ' ', 1)) IN ('mtx#120', 'mtx#121', 'mtx#122');

UPDATE properties
SET electricity_customer_code = 'pb06060046628'
WHERE lower(property_code) IN ('mtx#123', 'mtx#124', 'mtx#125')
   OR lower(split_part(property_name, ' ', 1)) IN ('mtx#123', 'mtx#124', 'mtx#125');

-- Kiểm tra
SELECT id, property_code, property_name, electricity_customer_code, water_customer_code
FROM properties
WHERE lower(property_code) LIKE 'mtx#12%'
   OR lower(property_name) LIKE 'mtx#12%'
ORDER BY property_code NULLS LAST, id;
