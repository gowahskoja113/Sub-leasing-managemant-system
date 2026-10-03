-- =============================================================================
-- CLEANUP data seed bảo vệ — chạy trước khi seed lại capstone-defense-demo-seed.sql.
-- Nhận diện 6 nhà seed qua mã KH điện PE05150000110..115 / nước 15015000110..115
-- (và các nhà demo#… của bản seed cũ). Xoá nhà + mọi dòng tham chiếu tới nó (phòng, thiết bị,
-- HĐ, hoá đơn, thanh toán, công tơ, bảo trì, ... kể cả dòng app tự sinh thêm).
-- Không đụng account / zone / catalog.
-- =============================================================================

BEGIN;

-- Xoá dòng thoả p_where trong p_table, trước đó xoá đệ quy các dòng ở bảng con có FK trỏ tới.
CREATE OR REPLACE FUNCTION pg_temp.demo_purge(p_table regclass, p_where text, p_depth int DEFAULT 0)
RETURNS void LANGUAGE plpgsql AS $f$
DECLARE
  fk record;
BEGIN
  IF p_depth > 10 THEN
    RAISE EXCEPTION 'FK lồng quá sâu tại bảng %', p_table;
  END IF;

  FOR fk IN
    SELECT c.conrelid::regclass AS child, ac.attname AS child_col, ap.attname AS parent_col
    FROM pg_constraint c
    JOIN pg_attribute ac ON ac.attrelid = c.conrelid  AND ac.attnum = c.conkey[1]
    JOIN pg_attribute ap ON ap.attrelid = c.confrelid AND ap.attnum = c.confkey[1]
    WHERE c.contype = 'f'
      AND c.confrelid = p_table
      AND c.conrelid <> p_table
      AND array_length(c.conkey, 1) = 1
  LOOP
    PERFORM pg_temp.demo_purge(
      fk.child,
      format('%I IN (SELECT %I FROM %s WHERE %s)', fk.child_col, fk.parent_col, p_table, p_where),
      p_depth + 1);
  END LOOP;

  EXECUTE format('DELETE FROM %s WHERE %s', p_table, p_where);
END $f$;

CREATE TEMP TABLE seed_props ON COMMIT DROP AS
SELECT id FROM properties
WHERE property_code LIKE 'demo#%'
   OR upper(trim(electricity_customer_code)) IN
        ('PE05150000110', 'PE05150000111', 'PE05150000112', 'PE05150000113', 'PE05150000114', 'PE05150000115')
   OR trim(water_customer_code) IN
        ('15015000110', '15015000111', '15015000112', '15015000113', '15015000114', '15015000115');

SELECT 'Sẽ xoá ' || count(*) || ' nhà seed' FROM seed_props;

SELECT pg_temp.demo_purge('properties', $$id IN (SELECT id FROM seed_props)$$);

SELECT 'Còn lại nhà seed: ' || count(*) FROM properties
WHERE property_code LIKE 'demo#%'
   OR upper(trim(electricity_customer_code)) LIKE 'PE0515000011_';

COMMIT;
