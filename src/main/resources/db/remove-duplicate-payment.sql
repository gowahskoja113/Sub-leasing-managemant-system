-- Dọn dữ liệu: Bản ghi CLAIM-2 (id = 3) là dòng thừa.
-- Xoá bản ghi bị trùng khỏi DB để lịch sử thanh toán không bị lệch.

DELETE FROM tenant_payments WHERE transaction_id = 'CLAIM-2' AND id = 3;
