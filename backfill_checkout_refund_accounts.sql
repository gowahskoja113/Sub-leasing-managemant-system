-- BACKFILL SCRIPT CHO CHECKOUT REQUESTS
-- Tách 3 trường từ cột note có dạng: "TK hoàn cọc: {bank} — {account} — {holder}"

-- 1. Xem trước dữ liệu sẽ bị ảnh hưởng (DUMP SELECT)
-- Chạy đoạn này để xem kết quả cắt chuỗi có hợp lệ không trước khi UPDATE
SELECT 
    id, 
    note,
    split_part(substring(note from 'TK hoàn cọc: (.*)'), ' — ', 1) as parsed_bank_name,
    split_part(substring(note from 'TK hoàn cọc: (.*)'), ' — ', 2) as parsed_bank_account,
    split_part(substring(note from 'TK hoàn cọc: (.*)'), ' — ', 3) as parsed_account_holder
FROM checkout_requests
WHERE note LIKE '%TK hoàn cọc:%'
  AND refund_bank_account IS NULL
  -- Điều kiện: số tài khoản phải toàn chữ số (8-20 ký tự)
  AND split_part(substring(note from 'TK hoàn cọc: (.*)'), ' — ', 2) ~ '^[0-9]{8,20}$';

-- 2. UPDATE thực tế (Chỉ chạy khi đã verify SELECT trả về đúng)
/*
UPDATE checkout_requests
SET refund_bank_name = split_part(substring(note from 'TK hoàn cọc: (.*)'), ' — ', 1),
    refund_bank_account = split_part(substring(note from 'TK hoàn cọc: (.*)'), ' — ', 2),
    refund_account_holder = split_part(substring(note from 'TK hoàn cọc: (.*)'), ' — ', 3)
WHERE note LIKE '%TK hoàn cọc:%'
  AND refund_bank_account IS NULL
  AND split_part(substring(note from 'TK hoàn cọc: (.*)'), ' — ', 2) ~ '^[0-9]{8,20}$';
*/
