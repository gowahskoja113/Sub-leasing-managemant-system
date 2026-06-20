-- Chạy thủ công nếu cần (PostgreSQL)
-- Xóa cột legacy sau khi đổi công thức khấu hao / inbound contract

ALTER TABLE equipment_manifests
    ADD COLUMN IF NOT EXISTS source VARCHAR(50) NOT NULL DEFAULT 'INITIAL_HANDOVER';

ALTER TABLE depreciation_results DROP COLUMN IF EXISTS base_rent;
ALTER TABLE depreciation_results DROP COLUMN IF EXISTS original_deposit;
ALTER TABLE depreciation_results DROP COLUMN IF EXISTS monthly_operating_cost;

ALTER TABLE inbound_contracts DROP COLUMN IF EXISTS base_rent_price;
ALTER TABLE inbound_contracts DROP COLUMN IF EXISTS deposit_amount;

ALTER TABLE properties RENAME COLUMN floor_count TO total_floor;
ALTER TABLE properties DROP COLUMN IF EXISTS rooms_per_floor;

ALTER TABLE properties
    ADD COLUMN IF NOT EXISTS previous_status VARCHAR(50);

ALTER TABLE rooms
    ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE;

-- Cập nhật check constraint status sau khi bổ sung enum PropertyStatus mới
ALTER TABLE properties DROP CONSTRAINT IF EXISTS properties_status_check;
ALTER TABLE properties ADD CONSTRAINT properties_status_check
    CHECK (status IN (
        'DRAFT',
        'PENDING',
        'UNDER_RENOVATION',
        'PENDING_EQUIPMENT_INSTALLATION',
        'RENOVATION_COMPLETED',
        'PENDING_HOST_REVIEW',
        'PENDING_OPERATION_MANAGER',
        'ACTIVE',
        'DISABLED',
        'MAINTENANCE',
        'INACTIVE'
    ));
