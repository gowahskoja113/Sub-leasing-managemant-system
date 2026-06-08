-- Chạy thủ công nếu cần (PostgreSQL)
-- Xóa cột legacy sau khi đổi công thức khấu hao / inbound contract

ALTER TABLE depreciation_results DROP COLUMN IF EXISTS base_rent;
ALTER TABLE depreciation_results DROP COLUMN IF EXISTS original_deposit;
ALTER TABLE depreciation_results DROP COLUMN IF EXISTS monthly_operating_cost;

ALTER TABLE inbound_contracts DROP COLUMN IF EXISTS base_rent_price;
ALTER TABLE inbound_contracts DROP COLUMN IF EXISTS deposit_amount;
