-- Sửa tenant_invoices ĐIỆN/NƯỚC đã lỡ ghi billing_month/billing_year theo THÁNG PHÁT HÀNH
-- (billing_period dạng dải ngày "dd/MM/yyyy – dd/MM/yyyy") → về tháng của utility_bills tương ứng.
-- Điện/nước TRẢ SAU: phát hành 03/10/2026 là tiền tháng 9/2026.
-- Chạy: psql ... -f scripts/fix-utility-tenant-invoice-billing-month-2026-10.sql

BEGIN;

-- 1) Xem trước các dòng sẽ sửa
SELECT ti.id, ti.code, ti.invoice_type, ti.status, ti.billing_period,
       ti.billing_month AS old_month, ti.billing_year AS old_year,
       ub.month AS new_month, ub.year AS new_year, ui.property_id
FROM tenant_invoices ti
JOIN utility_invoices ui ON ui.id = ti.utility_invoice_id
JOIN LATERAL (
    SELECT b.month, b.year
    FROM utility_bills b
    WHERE b.property_id = ui.property_id
      AND b.type = ui.utility_type
      AND b.billing_period = ui.billing_period
      AND b.status = 'PUBLISHED'
    ORDER BY b.created_at DESC
    LIMIT 1
) ub ON TRUE
WHERE ti.invoice_type IN ('ELECTRICITY', 'WATER')
  AND (ti.billing_month IS DISTINCT FROM ub.month OR ti.billing_year IS DISTINCT FROM ub.year)
ORDER BY ti.id;

-- 2) Cập nhật
UPDATE tenant_invoices ti
SET billing_month = ub.month,
    billing_year  = ub.year
FROM utility_invoices ui
JOIN LATERAL (
    SELECT b.month, b.year
    FROM utility_bills b
    WHERE b.property_id = ui.property_id
      AND b.type = ui.utility_type
      AND b.billing_period = ui.billing_period
      AND b.status = 'PUBLISHED'
    ORDER BY b.created_at DESC
    LIMIT 1
) ub ON TRUE
WHERE ui.id = ti.utility_invoice_id
  AND ti.invoice_type IN ('ELECTRICITY', 'WATER')
  AND (ti.billing_month IS DISTINCT FROM ub.month OR ti.billing_year IS DISTINCT FROM ub.year);

-- 3) Hoá đơn dải ngày không tìm được utility_bills → lấy tháng của mốc đầu dải ngày
UPDATE tenant_invoices ti
SET billing_month = CAST(split_part(split_part(trim(ti.billing_period), ' ', 1), '/', 2) AS INT),
    billing_year  = CAST(split_part(split_part(trim(ti.billing_period), ' ', 1), '/', 3) AS INT)
WHERE ti.invoice_type IN ('ELECTRICITY', 'WATER')
  AND ti.billing_period ~ '^\s*\d{1,2}/\d{1,2}/\d{4}\s'
  AND NOT EXISTS (
      SELECT 1
      FROM utility_invoices ui
      JOIN utility_bills b ON b.property_id = ui.property_id
                          AND b.type = ui.utility_type
                          AND b.billing_period = ui.billing_period
                          AND b.status = 'PUBLISHED'
      WHERE ui.id = ti.utility_invoice_id
  )
  AND (ti.billing_month IS DISTINCT FROM CAST(split_part(split_part(trim(ti.billing_period), ' ', 1), '/', 2) AS INT)
       OR ti.billing_year IS DISTINCT FROM CAST(split_part(split_part(trim(ti.billing_period), ' ', 1), '/', 3) AS INT));

-- 4) Kiểm tra lại
SELECT ti.id, ti.code, ti.invoice_type, ti.billing_period, ti.billing_month, ti.billing_year
FROM tenant_invoices ti
WHERE ti.invoice_type IN ('ELECTRICITY', 'WATER')
  AND ti.billing_period ~ '^\s*\d{1,2}/\d{1,2}/\d{4}'
ORDER BY ti.id;

COMMIT;
