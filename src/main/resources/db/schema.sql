-- =============================================================================
-- SLMS2026 — PostgreSQL schema (fresh install)
-- Khớp JPA entities + DatabaseSchemaMigration (ddl-auto=update vẫn chạy được sau script này)
--
-- Cách dùng:
--   CREATE DATABASE slms2026;
--   \c slms2026
--   \i schema.sql
--   \i seed.sql
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- -----------------------------------------------------------------------------
-- Identity / zone
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS "User" (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    password        VARCHAR(255) NOT NULL,
    username        VARCHAR(255) NOT NULL UNIQUE,
    phone_number    VARCHAR(255) NOT NULL UNIQUE,
    email           VARCHAR(255),
    avatar_url      VARCHAR(255),
    full_name       VARCHAR(255) NOT NULL,
    create_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    update_at       TIMESTAMP,
    role            VARCHAR(50),
    status          VARCHAR(50) DEFAULT 'INACTIVE',
    push_token      VARCHAR(255),
    is_first_login  BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS admin (
    user_id   UUID PRIMARY KEY REFERENCES "User"(id),
    start_at  TIMESTAMP
);

CREATE TABLE IF NOT EXISTS owner (
    user_id UUID PRIMARY KEY REFERENCES "User"(id)
);

CREATE TABLE IF NOT EXISTS tenant (
    user_id              UUID PRIMARY KEY REFERENCES "User"(id),
    cccd                 VARCHAR(255),
    date_of_birth        DATE,
    cccd_issue_date      DATE,
    cccd_issue_place     VARCHAR(255),
    permanent_address    VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS operation_management (
    user_id   UUID PRIMARY KEY REFERENCES "User"(id),
    start_at  TIMESTAMP
);

CREATE TABLE IF NOT EXISTS zone (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    level       INTEGER NOT NULL,
    parent_id   UUID REFERENCES zone(id)
);

CREATE TABLE IF NOT EXISTS manager_zones (
    manager_id UUID NOT NULL REFERENCES operation_management(user_id),
    zone_id    UUID NOT NULL REFERENCES zone(id),
    PRIMARY KEY (manager_id, zone_id)
);

CREATE TABLE IF NOT EXISTS zone_managers (
    zone_id     UUID PRIMARY KEY,
    manager_id  UUID NOT NULL REFERENCES "User"(id),
    assigned_by UUID,
    assigned_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS zone_manager_handovers (
    id                   BIGSERIAL PRIMARY KEY,
    zone_id              UUID NOT NULL,
    from_manager_id      UUID,
    to_manager_id        UUID,
    changed_by           UUID NOT NULL,
    changed_at           TIMESTAMP NOT NULL,
    affected_properties  INTEGER,
    affected_contracts   INTEGER
);

CREATE TABLE IF NOT EXISTS user_push_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    UUID NOT NULL,
    token      VARCHAR(512) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_user_push_tokens_user_id ON user_push_tokens(user_id);

-- -----------------------------------------------------------------------------
-- Master catalogs
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS equipment_catalog (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    active      BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS renovation_categories (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(255) NOT NULL UNIQUE,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    active      BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS billing_config (
    id                        BIGINT PRIMARY KEY,
    reminder_lead_days        INTEGER NOT NULL DEFAULT 3,
    grace_days                INTEGER NOT NULL DEFAULT 2,
    meter_reminder_lead_days  INTEGER NOT NULL DEFAULT 1,
    updated_at                TIMESTAMP,
    updated_by                UUID
);

-- -----------------------------------------------------------------------------
-- Property / room
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS properties (
    id                              BIGSERIAL PRIMARY KEY,
    property_name                   VARCHAR(255) NOT NULL,
    address                         VARCHAR(255) NOT NULL,
    zone_id                         UUID NOT NULL REFERENCES zone(id),
    area_size                       DOUBLE PRECISION,
    length_m                        DOUBLE PRECISION,
    width_m                         DOUBLE PRECISION,
    total_floor                     INTEGER,
    is_whole_house                  BOOLEAN,
    has_renovation                  BOOLEAN,
    total_rooms                     INTEGER,
    status                          VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    previous_status                 VARCHAR(50),
    created_by                      BIGINT,
    operation_manager_id            UUID,
    managed_by                      UUID,
    descriptions                    VARCHAR(255) NOT NULL,
    price                           NUMERIC(19, 2),
    applied_price                   NUMERIC(19, 2),
    latitude                        DOUBLE PRECISION,
    longitude                       DOUBLE PRECISION,
    electricity_unit_price          NUMERIC(19, 2),
    water_unit_price                NUMERIC(19, 2),
    deposit_months                  INTEGER,
    service_fee                     NUMERIC(19, 2),
    renovation_start_date           DATE,
    renovation_end_date             DATE,
    renovation_completed            BOOLEAN NOT NULL DEFAULT FALSE,
    submitted_to_host_at            TIMESTAMP,
    manager_accepted_at             TIMESTAMP,
    host_contingency_percent        NUMERIC(19, 2),
    CONSTRAINT properties_status_check CHECK (status IN (
        'DRAFT', 'PENDING', 'UNDER_RENOVATION', 'PENDING_EQUIPMENT_INSTALLATION',
        'RENOVATION_COMPLETED', 'PENDING_HOST_REVIEW', 'PENDING_OPERATION_MANAGER',
        'ACTIVE', 'DISABLED', 'MAINTENANCE', 'INACTIVE', 'RENTED'
    ))
);

CREATE TABLE IF NOT EXISTS property_images (
    property_id BIGINT NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    image_url   VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS rooms (
    id                      BIGSERIAL PRIMARY KEY,
    property_id             BIGINT NOT NULL REFERENCES properties(id),
    room_number             VARCHAR(255) NOT NULL,
    floor                   INTEGER,
    price                   NUMERIC(19, 2),
    applied_price           NUMERIC(19, 2),
    deposit                 NUMERIC(19, 2),
    area                    DOUBLE PRECISION NOT NULL,
    length_m                DOUBLE PRECISION,
    width_m                 DOUBLE PRECISION,
    max_occupants           INTEGER,
    structure_description   TEXT,
    image_urls              TEXT,
    room_type               VARCHAR(50) NOT NULL,
    status                  VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    electric_meter_code     VARCHAR(255),
    water_meter_code        VARCHAR(255),
    elec_integer_digits     INTEGER DEFAULT 5,
    elec_decimal_digits     INTEGER DEFAULT 1,
    water_integer_digits    INTEGER DEFAULT 5,
    water_decimal_digits    INTEGER DEFAULT 3,
    is_deleted              BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS inbound_contracts (
    id                 BIGSERIAL PRIMARY KEY,
    property_id        BIGINT NOT NULL UNIQUE REFERENCES properties(id),
    contract_code      VARCHAR(255) NOT NULL UNIQUE,
    owner_name         VARCHAR(255) NOT NULL,
    total_rent_amount  NUMERIC(19, 2) NOT NULL,
    start_date         DATE NOT NULL,
    end_date           DATE NOT NULL,
    contract_scan_url  VARCHAR(255),
    status             VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS master_leases (
    id            BIGSERIAL PRIMARY KEY,
    property_id   BIGINT NOT NULL REFERENCES properties(id),
    owner_name    VARCHAR(255),
    owner_phone   VARCHAR(255),
    monthly_rent  NUMERIC(19, 2),
    deposit       NUMERIC(19, 2),
    payment_day   INTEGER,
    start_date    DATE,
    end_date      DATE,
    escalation_pct DOUBLE PRECISION,
    status        VARCHAR(50),
    created_at    TIMESTAMP,
    is_deleted    BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS renovation_sessions (
    id              BIGSERIAL PRIMARY KEY,
    property_id     BIGINT NOT NULL REFERENCES properties(id),
    session_number  INTEGER NOT NULL,
    start_date      DATE,
    end_date        DATE,
    status          VARCHAR(30) NOT NULL DEFAULT 'IN_PROGRESS',
    disabled_at     TIMESTAMP,
    created_at      TIMESTAMP DEFAULT NOW(),
    UNIQUE (property_id, session_number)
);

CREATE TABLE IF NOT EXISTS renovation_lines (
    id           BIGSERIAL PRIMARY KEY,
    property_id  BIGINT NOT NULL REFERENCES properties(id),
    category_id  BIGINT NOT NULL REFERENCES renovation_categories(id),
    session_id   BIGINT REFERENCES renovation_sessions(id),
    cost         NUMERIC(19, 2) NOT NULL,
    note         TEXT
);

CREATE TABLE IF NOT EXISTS equipment_manifests (
    id           BIGSERIAL PRIMARY KEY,
    property_id  BIGINT NOT NULL REFERENCES properties(id),
    catalog_id   BIGINT NOT NULL REFERENCES equipment_catalog(id),
    quantity     INTEGER NOT NULL,
    status       VARCHAR(50) NOT NULL,
    source       VARCHAR(50) NOT NULL DEFAULT 'INITIAL_HANDOVER',
    price        NUMERIC(19, 2),
    CONSTRAINT equipment_manifests_status_check CHECK (status IN (
        'NEW', 'GOOD', 'DAMAGED', 'MAINTENANCE', 'BROKEN', 'DISPOSED'
    ))
);

CREATE TABLE IF NOT EXISTS handover_equipments (
    id           BIGSERIAL PRIMARY KEY,
    property_id  BIGINT NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    catalog_id   BIGINT NOT NULL REFERENCES equipment_catalog(id),
    description  TEXT,
    room_number  VARCHAR(50),
    house_area   VARCHAR(50),
    status       VARCHAR(50) NOT NULL,
    quantity     INTEGER NOT NULL,
    note         TEXT,
    CONSTRAINT handover_equipments_status_check CHECK (status IN (
        'NEW', 'GOOD', 'DAMAGED', 'MAINTENANCE', 'BROKEN', 'DISPOSED'
    ))
);

CREATE TABLE IF NOT EXISTS equipments (
    id                      BIGSERIAL PRIMARY KEY,
    property_id             BIGINT NOT NULL REFERENCES properties(id),
    room_id                 BIGINT REFERENCES rooms(id),
    catalog_id              BIGINT NOT NULL REFERENCES equipment_catalog(id),
    manifest_id             BIGINT REFERENCES equipment_manifests(id),
    renovation_session_id   BIGINT REFERENCES renovation_sessions(id),
    operational_status      VARCHAR(30) DEFAULT 'ACTIVE',
    disabled_at             TIMESTAMP,
    disabled_reason         VARCHAR(255),
    disabled_by_contract_id BIGINT,
    house_area              VARCHAR(50),
    source                  VARCHAR(50) NOT NULL,
    status                  VARCHAR(50) NOT NULL,
    price                   NUMERIC(19, 2),
    note                    TEXT,
    equipment_name          VARCHAR(255),
    category                VARCHAR(255),
    installation_date       DATE,
    warranty_expired_date   DATE,
    maintenance_count       INTEGER NOT NULL DEFAULT 0,
    last_maintenance_date   TIMESTAMP,
    warranty_months         INTEGER,
    warranty_start_date     DATE,
    warranty_end_date       DATE,
    penalty_fee             NUMERIC(19, 2),
    recommend_replacement   BOOLEAN NOT NULL DEFAULT FALSE,
    qr_code                 VARCHAR(64) UNIQUE,
    CONSTRAINT equipments_status_check CHECK (status IN (
        'NEW', 'GOOD', 'DAMAGED', 'MAINTENANCE', 'BROKEN', 'DISPOSED'
    ))
);

CREATE TABLE IF NOT EXISTS depreciation_results (
    id                           BIGSERIAL PRIMARY KEY,
    inbound_contract_id          BIGINT NOT NULL REFERENCES inbound_contracts(id),
    room_id                      BIGINT REFERENCES rooms(id),
    total_renovation_cost        NUMERIC(19, 2) NOT NULL,
    total_equipment_cost         NUMERIC(19, 2) NOT NULL,
    total_rent_amount            NUMERIC(19, 2) NOT NULL,
    total_investment             NUMERIC(19, 2) NOT NULL,
    contract_months              INTEGER NOT NULL,
    monthly_depreciation         NUMERIC(19, 2) NOT NULL,
    suggested_min_price          NUMERIC(19, 2) NOT NULL,
    suggested_price_with_profit  NUMERIC(19, 2) NOT NULL,
    room_floor                   NUMERIC(19, 2),
    effective_m2                 DOUBLE PRECISION,
    weight                       DOUBLE PRECISION,
    calculated_at                TIMESTAMP NOT NULL
);

-- -----------------------------------------------------------------------------
-- Tenant contracts
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS tenant_contracts (
    id                                  BIGSERIAL PRIMARY KEY,
    tenant_user_id                      UUID REFERENCES tenant(user_id),
    property_id                         BIGINT NOT NULL REFERENCES properties(id),
    room_id                             BIGINT REFERENCES rooms(id),
    contract_code                       VARCHAR(255) NOT NULL UNIQUE,
    rent_amount                         NUMERIC(19, 2) NOT NULL,
    base_rent_amount                    NUMERIC(19, 2),
    rent_escalation_type                VARCHAR(20) DEFAULT 'NONE',
    rent_escalation_percent             NUMERIC(19, 4),
    rent_schedule_json                  TEXT,
    rent_escalation_last_from_month     INTEGER,
    deposit                             NUMERIC(19, 2) NOT NULL,
    move_in_date                        DATE NOT NULL,
    start_date                          DATE NOT NULL,
    end_date                            DATE,
    equipment_snapshot                  TEXT,
    deposit_months                      INTEGER,
    initial_electric_reading            NUMERIC(19, 2),
    initial_water_reading               NUMERIC(19, 2),
    electric_meter_image_url            VARCHAR(255),
    water_meter_image_url               VARCHAR(255),
    electric_meter_captured_at          TIMESTAMP,
    water_meter_captured_at             TIMESTAMP,
    room_condition_note                 TEXT,
    payment_status                      VARCHAR(50),
    payos_order_code                    BIGINT,
    onboard_qr_amount                   NUMERIC(19, 2),
    onboard_qr_deposit_amount           NUMERIC(19, 2),
    onboard_qr_first_rent_amount        NUMERIC(19, 2),
    paid_at                             TIMESTAMP,
    deposit_paid_at                     TIMESTAMP,
    deposit_method                      VARCHAR(50),
    activated_at                        TIMESTAMP,
    deposit_cash_tenant_confirmed_at    TIMESTAMP,
    deposit_cash_manager_confirmed_at   TIMESTAMP,
    document_url                        VARCHAR(1024),
    document_generated_at               TIMESTAMP,
    status                              VARCHAR(50) NOT NULL,
    price_approval_status               VARCHAR(50),
    price_reject_reason                 TEXT,
    handover_acknowledged_at            TIMESTAMP,
    assigned_manager_id                 UUID REFERENCES "User"(id),
    draft_contract_file_url             VARCHAR(512),
    expected_reception_date             DATE,
    draft_tenant_name                   VARCHAR(255),
    draft_tenant_phone                  VARCHAR(255),
    draft_tenant_cccd                   VARCHAR(255),
    draft_tenant_dob                    DATE,
    draft_tenant_cccd_issue_date        DATE,
    draft_tenant_cccd_issue_place       VARCHAR(255),
    draft_tenant_address                VARCHAR(255),
    terminated_at                       TIMESTAMP,
    termination_type                    VARCHAR(50),
    termination_reason                  TEXT,
    termination_note                    TEXT,
    termination_proposed                BOOLEAN DEFAULT FALSE,
    CONSTRAINT tenant_contracts_status_check CHECK (status IN (
        'DRAFT', 'PENDING', 'ACTIVE', 'EXPIRED', 'TERMINATED'
    ))
);

CREATE TABLE IF NOT EXISTS tenant_contract_condition_photos (
    tenant_contract_id BIGINT NOT NULL REFERENCES tenant_contracts(id) ON DELETE CASCADE,
    image_url          VARCHAR(255) NOT NULL,
    captured_at        TIMESTAMP
);

CREATE TABLE IF NOT EXISTS household_members (
    id                 BIGSERIAL PRIMARY KEY,
    tenant_contract_id BIGINT NOT NULL REFERENCES tenant_contracts(id) ON DELETE CASCADE,
    full_name          VARCHAR(255) NOT NULL,
    relation           VARCHAR(255),
    phone              VARCHAR(255),
    date_of_birth      DATE,
    cccd               VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS tenant_contract_equipments (
    id                   BIGSERIAL PRIMARY KEY,
    tenant_contract_id   BIGINT NOT NULL REFERENCES tenant_contracts(id) ON DELETE CASCADE,
    equipment_id         BIGINT NOT NULL REFERENCES equipments(id),
    condition_at_signing VARCHAR(50),
    quantity             INTEGER NOT NULL DEFAULT 1,
    UNIQUE (tenant_contract_id, equipment_id),
    CONSTRAINT tenant_contract_equipments_condition_at_signing_check CHECK (
        condition_at_signing IS NULL OR condition_at_signing IN (
            'NEW', 'GOOD', 'DAMAGED', 'MAINTENANCE', 'BROKEN', 'DISPOSED'
        )
    )
);

CREATE TABLE IF NOT EXISTS room_price_history (
    id              BIGSERIAL PRIMARY KEY,
    property_id     BIGINT NOT NULL,
    room_id         BIGINT,
    change_type     VARCHAR(30) NOT NULL,
    old_price       NUMERIC(19, 2),
    new_price       NUMERIC(19, 2) NOT NULL,
    contract_id     BIGINT,
    reason          TEXT,
    changed_by      UUID,
    changed_by_name VARCHAR(255),
    changed_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_room_price_history_property ON room_price_history(property_id, changed_at DESC);

-- -----------------------------------------------------------------------------
-- Billing / utilities
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS invoices (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   UUID REFERENCES tenant(user_id),
    room_id     BIGINT REFERENCES rooms(id),
    property_id BIGINT REFERENCES properties(id),
    amount      NUMERIC(19, 2),
    due_date    DATE,
    status      VARCHAR(50),
    month       VARCHAR(255),
    created_at  TIMESTAMP,
    is_deleted  BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS tenant_invoices (
    id                    BIGSERIAL PRIMARY KEY,
    code                  VARCHAR(255) NOT NULL UNIQUE,
    tenant_user_id        UUID,
    tenant_contract_id    BIGINT NOT NULL REFERENCES tenant_contracts(id),
    utility_invoice_id    BIGINT UNIQUE,
    invoice_type          VARCHAR(50) NOT NULL,
    cycle_type            VARCHAR(16),
    property_name         VARCHAR(255) NOT NULL,
    room_number           VARCHAR(255),
    billing_month         INTEGER,
    billing_year          INTEGER,
    billing_period        VARCHAR(255),
    note                  TEXT,
    total_amount          NUMERIC(19, 2) NOT NULL,
    late_fee              NUMERIC(19, 2) DEFAULT 0,
    grand_total           NUMERIC(19, 2) NOT NULL,
    status                VARCHAR(50) NOT NULL,
    due_date              DATE,
    last_reminder_date    DATE,
    created_at            TIMESTAMP NOT NULL,
    paid_at               TIMESTAMP,
    payment_method        VARCHAR(255),
    transaction_id        VARCHAR(255),
    kwh_used              NUMERIC(19, 4),
    electricity_rate      NUMERIC(19, 4),
    m3_used               NUMERIC(19, 4),
    water_rate            NUMERIC(19, 4),
    payos_order_code      BIGINT,
    payos_checkout_url    VARCHAR(1024),
    payos_qr_code         TEXT,
    auto_issued           BOOLEAN
);

CREATE TABLE IF NOT EXISTS tenant_payments (
    id                 BIGSERIAL PRIMARY KEY,
    tenant_invoice_id  BIGINT NOT NULL UNIQUE REFERENCES tenant_invoices(id),
    tenant_user_id     UUID,
    invoice_code       VARCHAR(255) NOT NULL,
    invoice_type       VARCHAR(50) NOT NULL,
    amount             NUMERIC(19, 2) NOT NULL,
    method             VARCHAR(255) NOT NULL,
    paid_at            TIMESTAMP NOT NULL,
    transaction_id     VARCHAR(255),
    property_name      VARCHAR(255),
    room_number        VARCHAR(255),
    collection_mode    VARCHAR(30),
    remitted_by        UUID,
    remit_method       VARCHAR(50),
    payer_name         VARCHAR(255),
    payer_phone        VARCHAR(20),
    facilitated_by     UUID,
    unlocked_by_admin  UUID,
    payment_note       TEXT
);

CREATE TABLE IF NOT EXISTS tenant_invoice_payos_orders (
    id                BIGSERIAL PRIMARY KEY,
    invoice_id        BIGINT NOT NULL REFERENCES tenant_invoices(id),
    order_code        BIGINT NOT NULL UNIQUE,
    checkout_url      VARCHAR(1024),
    qr_code           TEXT,
    amount            NUMERIC(19, 2) NOT NULL,
    created_by        UUID,
    purpose           VARCHAR(30),
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    expired_at        TIMESTAMP,
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    payer_name        VARCHAR(255),
    payer_phone       VARCHAR(20),
    unlocked_by_admin UUID
);

CREATE TABLE IF NOT EXISTS invoice_unlock_passcodes (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(16) NOT NULL,
    invoice_id  BIGINT NOT NULL REFERENCES tenant_invoices(id),
    purpose     VARCHAR(30) NOT NULL,
    created_by  UUID NOT NULL,
    note        TEXT,
    expires_at  TIMESTAMP NOT NULL,
    used_at     TIMESTAMP,
    used_by     UUID,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS invoice_unlock_tokens (
    id                BIGSERIAL PRIMARY KEY,
    token             UUID NOT NULL UNIQUE,
    manager_id        UUID NOT NULL,
    invoice_id        BIGINT NOT NULL REFERENCES tenant_invoices(id),
    purpose           VARCHAR(30) NOT NULL,
    passcode_id       BIGINT NOT NULL,
    unlocked_by_admin UUID NOT NULL,
    expires_at        TIMESTAMP NOT NULL,
    used_at           TIMESTAMP,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS invoice_unlock_fail_counters (
    manager_id    UUID NOT NULL,
    invoice_id    BIGINT NOT NULL,
    fail_count    INT NOT NULL DEFAULT 0,
    locked_until  TIMESTAMP,
    PRIMARY KEY (manager_id, invoice_id)
);

CREATE TABLE IF NOT EXISTS invoice_unlock_logs (
    id                BIGSERIAL PRIMARY KEY,
    manager_id        UUID NOT NULL,
    invoice_id        BIGINT NOT NULL,
    purpose           VARCHAR(30) NOT NULL,
    unlocked_by_admin UUID NOT NULL,
    passcode_id       BIGINT,
    success           BOOLEAN NOT NULL DEFAULT TRUE,
    payment_result    VARCHAR(50),
    created_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS tenant_payment_claims (
    id                 BIGSERIAL PRIMARY KEY,
    tenant_invoice_id  BIGINT NOT NULL REFERENCES tenant_invoices(id),
    tenant_user_id     UUID NOT NULL,
    amount             NUMERIC(19, 2) NOT NULL,
    method             VARCHAR(50) NOT NULL,
    transfer_content   TEXT,
    status             VARCHAR(30) NOT NULL DEFAULT 'PENDING_VERIFY',
    reject_reason      TEXT,
    created_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    verified_at        TIMESTAMP,
    verified_by        UUID
);

CREATE TABLE IF NOT EXISTS tenant_pending_charges (
    id                      BIGSERIAL PRIMARY KEY,
    tenant_contract_id      BIGINT NOT NULL REFERENCES tenant_contracts(id),
    invoice_id              BIGINT REFERENCES tenant_invoices(id),
    amount                  NUMERIC(19, 2) NOT NULL,
    category                VARCHAR(50) NOT NULL,
    note                    TEXT,
    maintenance_request_id  BIGINT,
    status                  VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS utility_bills (
    id              BIGSERIAL PRIMARY KEY,
    property_id     BIGINT NOT NULL REFERENCES properties(id),
    type            VARCHAR(20) NOT NULL DEFAULT 'ELECTRIC',
    billing_period  VARCHAR(255) NOT NULL,
    month           INTEGER NOT NULL,
    year            INTEGER NOT NULL,
    total_quantity  INTEGER NOT NULL,
    total_amount    NUMERIC(19, 2) NOT NULL,
    unit_price      NUMERIC(19, 8) NOT NULL,
    image_url       VARCHAR(255),
    status          VARCHAR(50) NOT NULL,
    created_by      UUID,
    created_at      TIMESTAMP,
    reading_deadline DATE
);

CREATE TABLE IF NOT EXISTS utility_invoices (
    id                  BIGSERIAL PRIMARY KEY,
    property_id         BIGINT NOT NULL REFERENCES properties(id),
    room_id             BIGINT REFERENCES rooms(id),
    tenant_contract_id  BIGINT REFERENCES tenant_contracts(id),
    utility_type        VARCHAR(50) NOT NULL,
    billing_period      VARCHAR(255) NOT NULL,
    prev_reading        NUMERIC(19, 4) NOT NULL,
    new_reading         NUMERIC(19, 4) NOT NULL,
    consumption         NUMERIC(19, 4) NOT NULL,
    unit_price          NUMERIC(19, 4) NOT NULL,
    amount              NUMERIC(19, 2) NOT NULL,
    meter_image_url     VARCHAR(255),
    status              VARCHAR(50) NOT NULL,
    sent_at             TIMESTAMP,
    created_by          UUID,
    created_at          TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS meter_readings (
    id            BIGSERIAL PRIMARY KEY,
    property_id   BIGINT NOT NULL REFERENCES properties(id),
    room_id       BIGINT REFERENCES rooms(id),
    utility_type  VARCHAR(50) NOT NULL,
    period        VARCHAR(255) NOT NULL,
    reading       NUMERIC(19, 4) NOT NULL,
    image_url     VARCHAR(255),
    recorded_at   TIMESTAMP NOT NULL,
    recorded_by   UUID
);

CREATE TABLE IF NOT EXISTS monthly_readings (
    id              BIGSERIAL PRIMARY KEY,
    property_id     BIGINT NOT NULL REFERENCES properties(id),
    room_id         BIGINT REFERENCES rooms(id),
    utility_type    VARCHAR(50) NOT NULL,
    billing_month   VARCHAR(255) NOT NULL,
    reading_start   INTEGER NOT NULL,
    reading_end     INTEGER NOT NULL,
    units_used      INTEGER NOT NULL,
    unit_price      NUMERIC(19, 2) NOT NULL,
    amount_charged  NUMERIC(19, 2) NOT NULL,
    recorded_date   DATE NOT NULL,
    UNIQUE (property_id, room_id, utility_type, billing_month)
);

CREATE TABLE IF NOT EXISTS expenses (
    id           BIGSERIAL PRIMARY KEY,
    property_id  BIGINT NOT NULL REFERENCES properties(id),
    category     VARCHAR(50),
    amount       NUMERIC(19, 2),
    month        VARCHAR(255),
    note         TEXT,
    created_by   VARCHAR(255),
    created_at   TIMESTAMP,
    is_deleted   BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS host_expenses (
    id           BIGSERIAL PRIMARY KEY,
    property_id  BIGINT NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    category     VARCHAR(50) NOT NULL,
    amount       NUMERIC(19, 2) NOT NULL,
    month        VARCHAR(7) NOT NULL,
    note         TEXT,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- Maintenance
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS maintenance_requests (
    id                    BIGSERIAL PRIMARY KEY,
    request_code          VARCHAR(255) UNIQUE,
    tenant_id             UUID NOT NULL REFERENCES tenant(user_id),
    property_id           BIGINT NOT NULL REFERENCES properties(id),
    room_id               BIGINT REFERENCES rooms(id),
    tenant_contract_id    BIGINT REFERENCES tenant_contracts(id),
    equipment_id          BIGINT,
    title                 VARCHAR(255),
    description           TEXT,
    assigned_manager_id   UUID REFERENCES "User"(id),
    scheduled_date        TIMESTAMP,
    category              VARCHAR(255),
    priority              VARCHAR(255),
    status                VARCHAR(50),
    created_at            TIMESTAMP,
    updated_at            TIMESTAMP,
    acknowledged_at       TIMESTAMP,
    scheduled_slots       TEXT,
    confirmed_slot        VARCHAR(255),
    on_hold_reason        TEXT,
    approval_status       VARCHAR(50),
    done_at               TIMESTAMP,
    tenant_confirmed_at   TIMESTAMP,
    resolved_at           TIMESTAMP,
    reopen_count          INTEGER,
    technician_id         VARCHAR(255),
    cost_paid_by          VARCHAR(50),
    cause                 VARCHAR(50),
    repair_cost           NUMERIC(19, 2),
    cost_agreement_status VARCHAR(50),
    cost_dispute_reason   TEXT,
    resolution_note       TEXT,
    reject_reason         TEXT,
    reject_image_urls     TEXT,
    before_image_urls     TEXT,
    after_image_urls      TEXT,
    is_deleted            BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT maintenance_requests_cost_agreement_status_check CHECK (
        cost_agreement_status IS NULL OR cost_agreement_status IN (
            'NOT_APPLICABLE', 'PENDING', 'AGREED', 'DISPUTED', 'WAIVED'
        )
    )
);

CREATE TABLE IF NOT EXISTS maintenance_images (
    id                      BIGSERIAL PRIMARY KEY,
    maintenance_request_id  BIGINT NOT NULL REFERENCES maintenance_requests(id) ON DELETE CASCADE,
    image_url               VARCHAR(1024) NOT NULL,
    type                    VARCHAR(20) NOT NULL,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS maintenance_history (
    id                      BIGSERIAL PRIMARY KEY,
    maintenance_request_id  BIGINT NOT NULL REFERENCES maintenance_requests(id) ON DELETE CASCADE,
    old_status              VARCHAR(50),
    new_status              VARCHAR(50),
    note                    TEXT,
    changed_by              UUID NOT NULL REFERENCES "User"(id),
    changed_at              TIMESTAMP
);

CREATE TABLE IF NOT EXISTS maintenance_timelines (
    id                      BIGSERIAL PRIMARY KEY,
    maintenance_request_id  BIGINT NOT NULL REFERENCES maintenance_requests(id) ON DELETE CASCADE,
    old_status              VARCHAR(50),
    new_status              VARCHAR(50) NOT NULL,
    note                    TEXT,
    changed_by              UUID,
    changed_by_name         VARCHAR(255),
    changed_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS equipment_maintenance_histories (
    id                      BIGSERIAL PRIMARY KEY,
    equipment_id            BIGINT NOT NULL REFERENCES equipments(id),
    maintenance_request_id  BIGINT NOT NULL REFERENCES maintenance_requests(id),
    maintenance_date        TIMESTAMP,
    repair_cost             BIGINT,
    note                    TEXT
);

-- -----------------------------------------------------------------------------
-- Checkout
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS checkout_requests (
    id                      BIGSERIAL PRIMARY KEY,
    tenant_user_id          UUID NOT NULL,
    tenant_contract_id      BIGINT NOT NULL REFERENCES tenant_contracts(id),
    expected_move_out_date  DATE NOT NULL,
    reason                  TEXT NOT NULL,
    note                    TEXT,
    status                  VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    reviewed_at             TIMESTAMP,
    reviewed_by             UUID,
    manager_note            TEXT,
    reject_reason           TEXT,
    completed_at            TIMESTAMP,
    dispute_count           INTEGER DEFAULT 0,
    dispute_reason          TEXT,
    disputed_at             TIMESTAMP,
    CONSTRAINT checkout_requests_status_check CHECK (status IN (
        'PENDING', 'APPROVED', 'INSPECTING', 'WAITING_TENANT',
        'DISPUTED', 'SETTLING', 'REJECTED', 'COMPLETED', 'CANCELLED'
    ))
);

CREATE TABLE IF NOT EXISTS checkout_request_dispute_photos (
    checkout_request_id BIGINT NOT NULL REFERENCES checkout_requests(id) ON DELETE CASCADE,
    photo_url           VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS checkout_inspections (
    id                         BIGSERIAL PRIMARY KEY,
    checkout_request_id        BIGINT NOT NULL REFERENCES checkout_requests(id),
    room_condition_note        TEXT,
    electricity_final_reading  INTEGER,
    electric_meter_image_url   VARCHAR(255),
    water_final_reading        INTEGER,
    water_meter_image_url      VARCHAR(255),
    created_at                 TIMESTAMP NOT NULL,
    updated_at                 TIMESTAMP
);

CREATE TABLE IF NOT EXISTS checkout_inspection_photos (
    inspection_id BIGINT NOT NULL REFERENCES checkout_inspections(id) ON DELETE CASCADE,
    photo_url     VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS checkout_damage_items (
    id                      BIGSERIAL PRIMARY KEY,
    checkout_inspection_id  BIGINT NOT NULL REFERENCES checkout_inspections(id) ON DELETE CASCADE,
    equipment_id            BIGINT,
    label                   VARCHAR(255) NOT NULL,
    amount                  NUMERIC(19, 2) NOT NULL,
    note                    TEXT
);

CREATE TABLE IF NOT EXISTS checkout_damage_item_photos (
    damage_item_id BIGINT NOT NULL REFERENCES checkout_damage_items(id) ON DELETE CASCADE,
    photo_url      VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS checkout_settlements (
    id                       BIGSERIAL PRIMARY KEY,
    checkout_request_id      BIGINT NOT NULL REFERENCES checkout_requests(id),
    deposit_amount           NUMERIC(19, 2) NOT NULL,
    unpaid_total             NUMERIC(19, 2) NOT NULL,
    damage_total             NUMERIC(19, 2) NOT NULL,
    adjustment_total         NUMERIC(19, 2) NOT NULL,
    refund_amount            NUMERIC(19, 2) NOT NULL,
    extra_charge_amount      NUMERIC(19, 2) NOT NULL,
    extra_charge_invoice_id  BIGINT,
    refund_method            VARCHAR(255),
    refund_proof_url         VARCHAR(255),
    refund_paid_at           TIMESTAMP,
    refund_note              TEXT,
    created_at               TIMESTAMP NOT NULL,
    updated_at               TIMESTAMP
);

CREATE TABLE IF NOT EXISTS checkout_settlement_invoices (
    id                     BIGSERIAL PRIMARY KEY,
    checkout_settlement_id BIGINT NOT NULL REFERENCES checkout_settlements(id) ON DELETE CASCADE,
    invoice_id             BIGINT,
    invoice_code           VARCHAR(255),
    invoice_type           VARCHAR(255),
    amount                 NUMERIC(19, 2) NOT NULL
);

CREATE TABLE IF NOT EXISTS checkout_settlement_adjustments (
    id                     BIGSERIAL PRIMARY KEY,
    checkout_settlement_id BIGINT NOT NULL REFERENCES checkout_settlements(id) ON DELETE CASCADE,
    label                  VARCHAR(255) NOT NULL,
    amount                 NUMERIC(19, 2) NOT NULL
);

-- -----------------------------------------------------------------------------
-- OTP / meter override / viewing / notifications
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS otp_verifications (
    id            BIGSERIAL PRIMARY KEY,
    phone_number  VARCHAR(255) NOT NULL,
    code          VARCHAR(6) NOT NULL,
    purpose       VARCHAR(50) NOT NULL,
    reference_id  BIGINT,
    expires_at    TIMESTAMP NOT NULL,
    verified      BOOLEAN NOT NULL DEFAULT FALSE,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT otp_verifications_purpose_check CHECK (
        purpose IN ('CONTRACT_CONFIRM', 'TENANT_ACTIVATION')
    )
);

CREATE TABLE IF NOT EXISTS meter_override_tokens (
    id          BIGSERIAL PRIMARY KEY,
    token       UUID NOT NULL UNIQUE,
    manager_id  UUID NOT NULL,
    contract_id BIGINT REFERENCES tenant_contracts(id),
    meter_kind  VARCHAR(20) NOT NULL,
    expires_at  TIMESTAMP NOT NULL,
    used_at     TIMESTAMP,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS meter_override_logs (
    id            BIGSERIAL PRIMARY KEY,
    manager_id    UUID NOT NULL,
    contract_id   BIGINT NOT NULL,
    meter_kind    VARCHAR(20) NOT NULL,
    entered_value NUMERIC(19, 4),
    reason        TEXT,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS meter_override_fail_counters (
    manager_id   UUID PRIMARY KEY,
    fail_count   INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMP
);

CREATE TABLE IF NOT EXISTS meter_override_passcodes (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(16) NOT NULL,
    created_by UUID NOT NULL,
    note       TEXT,
    expires_at TIMESTAMP NOT NULL,
    used_at    TIMESTAMP,
    used_by    UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_meter_override_passcodes_code ON meter_override_passcodes(code);

CREATE TABLE IF NOT EXISTS property_viewing_leads (
    id                    BIGSERIAL PRIMARY KEY,
    customer_name         VARCHAR(255) NOT NULL,
    customer_phone        VARCHAR(20) NOT NULL,
    note                  TEXT,
    status                VARCHAR(30) NOT NULL DEFAULT 'NEW',
    assigned_manager_id   UUID,
    created_by            UUID,
    linked_user_id        UUID,
    preferred_viewing_at  TIMESTAMP,
    scheduled_at          TIMESTAMP,
    created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS viewing_lead_properties (
    id            BIGSERIAL PRIMARY KEY,
    lead_id       BIGINT NOT NULL REFERENCES property_viewing_leads(id) ON DELETE CASCADE,
    property_id   BIGINT NOT NULL REFERENCES properties(id),
    room_id       BIGINT REFERENCES rooms(id),
    interest_type VARCHAR(20) NOT NULL,
    note          TEXT
);

CREATE TABLE IF NOT EXISTS notifications (
    id          BIGSERIAL PRIMARY KEY,
    user_id     UUID NOT NULL,
    title       VARCHAR(255),
    content     TEXT,
    type        VARCHAR(50),
    screen      VARCHAR(255),
    params_json TEXT,
    dedupe_key  VARCHAR(255),
    is_read     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_notifications_user_dedupe
    ON notifications (user_id, dedupe_key)
    WHERE dedupe_key IS NOT NULL;

CREATE TABLE IF NOT EXISTS host_notifications (
    id         BIGSERIAL PRIMARY KEY,
    user_id    UUID NOT NULL,
    dedupe_key VARCHAR(255) NOT NULL,
    type       VARCHAR(50) NOT NULL,
    title      VARCHAR(255) NOT NULL,
    message    TEXT NOT NULL,
    priority   VARCHAR(20),
    is_read    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, dedupe_key)
);
