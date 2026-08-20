-- Người đón khách / onboard — ghi một lần, không bị ghi đè khi đổi quản lý khu vực.
ALTER TABLE tenant_contracts
    ADD COLUMN IF NOT EXISTS onboarded_by_manager_id UUID REFERENCES "User"(id),
    ADD COLUMN IF NOT EXISTS onboarded_at TIMESTAMP;
