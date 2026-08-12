package com.sep490.slms2026.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class DatabaseSchemaMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        addColumnIfNotExists(
                "equipment_manifests",
                "source",
                "VARCHAR(50) NOT NULL DEFAULT 'INITIAL_HANDOVER'");
        dropColumnIfExists("depreciation_results", "base_rent");
        dropColumnIfExists("depreciation_results", "original_deposit");
        dropColumnIfExists("depreciation_results", "monthly_operating_cost");
        dropColumnIfExists("inbound_contracts", "base_rent_price");
        dropColumnIfExists("inbound_contracts", "deposit_amount");
        dropColumnIfExists("properties", "deposit");
        dropColumnIfExists("equipments", "name");
        dropColumnIfExists("equipments", "purchase_price");
        dropTableIfExists("renovations");
        renameColumnIfExists("properties", "floor_count", "total_floor");
        dropColumnIfExists("properties", "rooms_per_floor");
        dropNotNullIfExists("properties", "created_by");
        alterColumnToUuidIfBigint("properties", "operation_manager_id");
        alterColumnToUuidIfBigint("properties", "managed_by");
        migrateRenovationSessions();
        ensureEquipmentCatalogSchema();
        ensureRoomsSoftDeleteColumn();
        ensurePropertyPreviousStatusColumn();
        ensurePropertyStatusConstraint();
        addColumnIfNotExists("tenant_contracts", "document_url", "VARCHAR(1024)");
        addColumnIfNotExists("tenant_contracts", "document_generated_at", "TIMESTAMP");
        ensureHandoverEquipmentTable();
        ensureHandoverEquipmentStatusConstraint();
        addColumnIfNotExists("equipments", "warranty_months", "INTEGER");
        addColumnIfNotExists("equipments", "warranty_start_date", "DATE");
        addColumnIfNotExists("equipments", "warranty_end_date", "DATE");
        migrateEquipmentOperationalFields();
        ensureHostPortalTables();
        addColumnIfNotExists("depreciation_results", "room_floor", "NUMERIC(19, 2)");
        addColumnIfNotExists("depreciation_results", "effective_m2", "DOUBLE PRECISION");
        addColumnIfNotExists("depreciation_results", "weight", "DOUBLE PRECISION");
        ensureEquipmentRecommendReplacementColumn();
        addColumnIfNotExists("tenant_contracts", "handover_acknowledged_at", "TIMESTAMP");
        addColumnIfNotExists("equipments", "qr_code", "VARCHAR(64)");
        ensureNotificationsTable();
        alterColumnToUuidIfBigint("notifications", "user_id");
        ensureTenantPaymentClaimsTable();
        addColumnIfNotExists("tenant_invoices", "note", "TEXT");
        backfillEquipmentQrCodes();
        ensureMaintenanceTables();
        ensureMaintenanceSimplifiedFlowColumns();
        ensureCostAgreementStatusConstraint();
        ensureMaintenanceImagesPhotoHistory();
        migrateMaintenanceStatusesToSimplifiedFlow();
        ensureTenantPendingChargesTable();
        ensureViewingLeadTables();
        ensureEquipmentsMaintenanceCountColumn();
        ensureTenantContractEquipmentsTable();
        addColumnIfNotExists("tenant_contracts", "deposit_cash_tenant_confirmed_at", "TIMESTAMP");
        addColumnIfNotExists("tenant_contracts", "deposit_cash_manager_confirmed_at", "TIMESTAMP");
        addColumnIfNotExists("tenant_contracts", "terminated_at", "TIMESTAMP");
        addColumnIfNotExists("tenant_contracts", "termination_type", "VARCHAR(50)");
        addColumnIfNotExists("tenant_contracts", "termination_reason", "TEXT");
        addColumnIfNotExists("tenant_contracts", "termination_note", "TEXT");
        ensureCheckoutRequestsTable();
        ensureCheckoutRequestsStatusConstraint();
        ensureTenantContractsStatusConstraint();
        addColumnIfNotExists("tenant_contracts", "electric_meter_captured_at", "TIMESTAMP");
        addColumnIfNotExists("tenant_contracts", "water_meter_captured_at", "TIMESTAMP");
        addColumnIfNotExists("tenant_contract_condition_photos", "captured_at", "TIMESTAMP");
        ensureOtpVerificationsPurposeConstraint();
        dropUniqueConstraintOnUserFullName();
        // Mentor feedback 07/08: onboard invoice + handover tracking + meter override
        addColumnIfNotExists("tenant_contracts", "deposit_paid_at", "TIMESTAMP");
        addColumnIfNotExists("tenant_contracts", "deposit_method", "VARCHAR(50)");
        addColumnIfNotExists("tenant_contracts", "activated_at", "TIMESTAMP");
        // Snapshot số tiền QR onboard — hoá đơn webhook khớp số đã quét
        addColumnIfNotExists("tenant_contracts", "onboard_qr_amount", "NUMERIC(19, 2)");
        addColumnIfNotExists("tenant_contracts", "onboard_qr_deposit_amount", "NUMERIC(19, 2)");
        addColumnIfNotExists("tenant_contracts", "onboard_qr_first_rent_amount", "NUMERIC(19, 2)");
        addColumnIfNotExists("properties", "manager_accepted_at", "TIMESTAMP");
        dropNotNullIfExists("tenant_invoices", "tenant_user_id");
        dropNotNullIfExists("tenant_payments", "tenant_user_id");
        ensureMeterOverrideTables();
        // Onboarding: token xin trước khi HĐ tạo → contract_id được null
        dropNotNullIfExists("meter_override_tokens", "contract_id");
        ensureMeterOverridePasscodesTable();
        // OCR split config per room (default điện 5+1, nước 5+3)
        addColumnIfNotExists("rooms", "elec_integer_digits", "INTEGER DEFAULT 5");
        addColumnIfNotExists("rooms", "elec_decimal_digits", "INTEGER DEFAULT 1");
        addColumnIfNotExists("rooms", "water_integer_digits", "INTEGER DEFAULT 5");
        addColumnIfNotExists("rooms", "water_decimal_digits", "INTEGER DEFAULT 3");
        // Multi-device Expo push tokens (1 account → nhiều máy)
        ensureUserPushTokensTable();
    }

    /**
     * full_name không được unique: nhiều khách trùng họ tên là bình thường;
     * 1 account xác định bằng phone/username. Constraint Hibernate cũ chặn confirm HĐ thứ 2+.
     */
    private void dropUniqueConstraintOnUserFullName() {
        try {
            List<String> names = jdbcTemplate.query(
                    """
                    SELECT c.conname
                    FROM pg_constraint c
                    JOIN pg_class t ON c.conrelid = t.oid
                    JOIN pg_namespace n ON t.relnamespace = n.oid
                    JOIN pg_attribute a ON a.attrelid = t.oid
                        AND a.attnum = ANY (c.conkey)
                        AND NOT a.attisdropped
                    WHERE n.nspname = 'public'
                      AND t.relname = 'User'
                      AND c.contype = 'u'
                      AND a.attname = 'full_name'
                    """,
                    (rs, rowNum) -> rs.getString(1));
            for (String name : names) {
                jdbcTemplate.execute("ALTER TABLE \"User\" DROP CONSTRAINT IF EXISTS \"" + name + "\"");
                log.info("Dropped unique constraint on User.full_name: {}", name);
            }
            // Unique index không gắn constraint (nếu có)
            List<String> indexes = jdbcTemplate.query(
                    """
                    SELECT i.relname
                    FROM pg_index x
                    JOIN pg_class t ON t.oid = x.indrelid
                    JOIN pg_class i ON i.oid = x.indexrelid
                    JOIN pg_namespace n ON n.oid = t.relnamespace
                    JOIN pg_attribute a ON a.attrelid = t.oid
                        AND a.attnum = ANY (x.indkey)
                        AND NOT a.attisdropped
                    WHERE n.nspname = 'public'
                      AND t.relname = 'User'
                      AND x.indisunique
                      AND NOT x.indisprimary
                      AND a.attname = 'full_name'
                      AND NOT EXISTS (
                          SELECT 1 FROM pg_constraint c WHERE c.conindid = x.indexrelid
                      )
                    """,
                    (rs, rowNum) -> rs.getString(1));
            for (String idx : indexes) {
                jdbcTemplate.execute("DROP INDEX IF EXISTS \"" + idx + "\"");
                log.info("Dropped unique index on User.full_name: {}", idx);
            }
        } catch (Exception e) {
            log.warn("Could not drop User.full_name unique constraint/index: {}", e.getMessage());
        }
    }

    /**
     * DB constraint otp_verifications_purpose_check chỉ cho phép 'CONTRACT_CONFIRM'.
     * Enum OtpPurpose đã thêm TENANT_ACTIVATION — cần recreate constraint.
     */
    private void ensureOtpVerificationsPurposeConstraint() {
        jdbcTemplate.execute(
                "ALTER TABLE otp_verifications DROP CONSTRAINT IF EXISTS otp_verifications_purpose_check");
        jdbcTemplate.execute("""
                ALTER TABLE otp_verifications ADD CONSTRAINT otp_verifications_purpose_check
                    CHECK (purpose IN ('CONTRACT_CONFIRM', 'TENANT_ACTIVATION'))
                """);
        log.info("Ensured otp_verifications_purpose_check includes TENANT_ACTIVATION");
    }

    private void ensureMaintenanceSimplifiedFlowColumns() {
        addColumnIfNotExists("maintenance_requests", "reject_reason", "TEXT");
        addColumnIfNotExists("maintenance_requests", "reject_image_urls", "TEXT");
        addColumnIfNotExists("maintenance_requests", "tenant_contract_id", "BIGINT REFERENCES tenant_contracts(id)");
    }

    private void ensureCostAgreementStatusConstraint() {
        jdbcTemplate.execute(
                "ALTER TABLE maintenance_requests DROP CONSTRAINT IF EXISTS maintenance_requests_cost_agreement_status_check");
        jdbcTemplate.execute("""
                ALTER TABLE maintenance_requests ADD CONSTRAINT maintenance_requests_cost_agreement_status_check
                    CHECK (cost_agreement_status::text = ANY (ARRAY[
                        'NOT_APPLICABLE',
                        'PENDING',
                        'AGREED',
                        'DISPUTED',
                        'WAIVED'
                    ]::text[]))
                """);
        log.info("Ensured maintenance_requests_cost_agreement_status_check includes WAIVED");
    }

    /** Map legacy maintenance statuses sang flow rút gọn. */
    private void migrateMaintenanceStatusesToSimplifiedFlow() {
        try {
            int updated = 0;
            updated += jdbcTemplate.update(
                    "UPDATE maintenance_requests SET status = 'APPROVED' WHERE status IN ('ACKNOWLEDGED','SCHEDULED','IN_PROGRESS','ON_HOLD','REOPENED')");
            updated += jdbcTemplate.update(
                    "UPDATE maintenance_requests SET status = 'WAITING_TENANT_CONFIRM' WHERE status IN ('DONE','PENDING_APPROVAL')");
            updated += jdbcTemplate.update(
                    "UPDATE maintenance_requests SET status = 'CLOSED' WHERE status IN ('CONFIRMED','RESOLVED')");
            if (updated > 0) {
                log.info("Migrated {} maintenance_requests rows to simplified statuses", updated);
            }
        } catch (Exception e) {
            log.warn("Could not migrate maintenance statuses: {}", e.getMessage());
        }
    }

    private void ensureCheckoutRequestsTable() {
        createTableIfNotExists(
                "checkout_requests",
                """
                id BIGSERIAL PRIMARY KEY,
                tenant_user_id UUID NOT NULL,
                tenant_contract_id BIGINT NOT NULL REFERENCES tenant_contracts(id),
                expected_move_out_date DATE NOT NULL,
                reason TEXT NOT NULL,
                note TEXT,
                status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
                created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                reviewed_at TIMESTAMP,
                reviewed_by UUID,
                manager_note TEXT,
                reject_reason TEXT,
                completed_at TIMESTAMP
                """);
        addColumnIfNotExists("checkout_requests", "reviewed_at", "TIMESTAMP");
        addColumnIfNotExists("checkout_requests", "reviewed_by", "UUID");
        addColumnIfNotExists("checkout_requests", "manager_note", "TEXT");
        addColumnIfNotExists("checkout_requests", "reject_reason", "TEXT");
        addColumnIfNotExists("checkout_requests", "completed_at", "TIMESTAMP");
    }

    private void ensureTenantContractEquipmentsTable() {
        createTableIfNotExists(
                "tenant_contract_equipments",
                """
                id BIGSERIAL PRIMARY KEY,
                tenant_contract_id BIGINT NOT NULL REFERENCES tenant_contracts(id) ON DELETE CASCADE,
                equipment_id BIGINT NOT NULL REFERENCES equipments(id),
                condition_at_signing VARCHAR(50),
                quantity INT NOT NULL DEFAULT 1,
                UNIQUE (tenant_contract_id, equipment_id)
                """);
    }

    private void ensureViewingLeadTables() {
        createTableIfNotExists(
                "property_viewing_leads",
                """
                id BIGSERIAL PRIMARY KEY,
                customer_name VARCHAR(255) NOT NULL,
                customer_phone VARCHAR(20) NOT NULL,
                note TEXT,
                status VARCHAR(30) NOT NULL DEFAULT 'NEW',
                assigned_manager_id UUID,
                created_by UUID,
                linked_user_id UUID,
                preferred_viewing_at TIMESTAMP,
                scheduled_at TIMESTAMP,
                created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                updated_at TIMESTAMP NOT NULL DEFAULT NOW()
                """);
        createTableIfNotExists(
                "viewing_lead_properties",
                """
                id BIGSERIAL PRIMARY KEY,
                lead_id BIGINT NOT NULL REFERENCES property_viewing_leads(id) ON DELETE CASCADE,
                property_id BIGINT NOT NULL REFERENCES properties(id),
                room_id BIGINT REFERENCES rooms(id),
                interest_type VARCHAR(20) NOT NULL,
                note TEXT
                """);
        addColumnIfNotExists("viewing_lead_properties", "interest_type", "VARCHAR(20)");
        jdbcTemplate.update(
                """
                UPDATE viewing_lead_properties
                SET interest_type = CASE WHEN room_id IS NULL THEN 'WHOLE_HOUSE' ELSE 'ROOM' END
                WHERE interest_type IS NULL
                """);
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE viewing_lead_properties ALTER COLUMN interest_type SET NOT NULL");
        } catch (Exception e) {
            log.warn("Could not enforce NOT NULL on viewing_lead_properties.interest_type: {}", e.getMessage());
        }
    }

    private void ensureEquipmentsMaintenanceCountColumn() {
        addColumnIfNotExists("equipments", "maintenance_count", "INTEGER NOT NULL DEFAULT 0");
    }


    private void ensureTenantPendingChargesTable() {
        createTableIfNotExists(
                "tenant_pending_charges",
                """
                id BIGSERIAL PRIMARY KEY,
                tenant_contract_id BIGINT NOT NULL REFERENCES tenant_contracts(id),
                amount NUMERIC(19, 2) NOT NULL,
                category VARCHAR(50) NOT NULL,
                note TEXT,
                status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
                created_at TIMESTAMP NOT NULL DEFAULT NOW()
                """);
        addColumnIfNotExists("tenant_pending_charges", "invoice_id", "BIGINT REFERENCES tenant_invoices(id)");
    }


    private void ensureMaintenanceTables() {
        createTableIfNotExists(
                "maintenance_timelines",
                """
                id BIGSERIAL PRIMARY KEY,
                maintenance_request_id BIGINT NOT NULL REFERENCES maintenance_requests(id) ON DELETE CASCADE,
                old_status VARCHAR(50),
                new_status VARCHAR(50) NOT NULL,
                note TEXT,
                changed_by UUID,
                changed_by_name VARCHAR(255),
                changed_at TIMESTAMP NOT NULL DEFAULT NOW()
                """);
        createTableIfNotExists(
                "maintenance_images",
                """
                id BIGSERIAL PRIMARY KEY,
                maintenance_request_id BIGINT NOT NULL REFERENCES maintenance_requests(id) ON DELETE CASCADE,
                image_url VARCHAR(1024) NOT NULL,
                type VARCHAR(20) NOT NULL DEFAULT 'BEFORE',
                created_at TIMESTAMP NOT NULL DEFAULT NOW()
                """);
    }

    /**
     * Bổ sung type/created_at cho maintenance_images và backfill từ 3 cột TEXT CSV
     * (before/after/reject_image_urls) — log ảnh append-only, không mất khi làm lại.
     */
    private void ensureMaintenanceImagesPhotoHistory() {
        addColumnIfNotExists("maintenance_images", "type", "VARCHAR(20) NOT NULL DEFAULT 'BEFORE'");
        addColumnIfNotExists("maintenance_images", "created_at", "TIMESTAMP NOT NULL DEFAULT NOW()");
        try {
            jdbcTemplate.execute("ALTER TABLE maintenance_images ALTER COLUMN type DROP DEFAULT");
        } catch (Exception e) {
            log.warn("Could not drop default on maintenance_images.type: {}", e.getMessage());
        }
        backfillMaintenanceImagesFromCsv();
    }

    private void backfillMaintenanceImagesFromCsv() {
        try {
            backfillMaintenanceImagesOfType(
                    "before_image_urls", "BEFORE", "COALESCE(created_at, NOW())");
            backfillMaintenanceImagesOfType(
                    "after_image_urls", "AFTER", "COALESCE(done_at, updated_at, created_at, NOW())");
            backfillMaintenanceImagesOfType(
                    "reject_image_urls", "REJECT", "COALESCE(updated_at, created_at, NOW())");
        } catch (Exception e) {
            log.warn("Could not backfill maintenance_images from CSV columns: {}", e.getMessage());
        }
    }

    private void backfillMaintenanceImagesOfType(String csvColumn, String type, String createdAtExpr) {
        int inserted = jdbcTemplate.update(
                """
                INSERT INTO maintenance_images (maintenance_request_id, image_url, type, created_at)
                SELECT mr.id, trim(u.url), ?, %s
                FROM maintenance_requests mr
                CROSS JOIN LATERAL unnest(string_to_array(mr.%s, ',')) AS u(url)
                WHERE mr.%s IS NOT NULL
                  AND trim(u.url) <> ''
                  AND NOT EXISTS (
                    SELECT 1 FROM maintenance_images mi
                    WHERE mi.maintenance_request_id = mr.id
                      AND mi.image_url = trim(u.url)
                      AND mi.type = ?
                  )
                """.formatted(createdAtExpr, csvColumn, csvColumn),
                type,
                type);
        if (inserted > 0) {
            log.info("Backfilled {} {} rows into maintenance_images from {}", inserted, type, csvColumn);
        }
    }

    private void backfillEquipmentQrCodes() {
        int updated = jdbcTemplate.update(
                "UPDATE equipments SET qr_code = 'EQ-' || id WHERE qr_code IS NULL");
        if (updated > 0) {
            log.info("Backfilled qr_code for {} equipment rows", updated);
        }
    }

    private void ensureNotificationsTable() {
        createTableIfNotExists(
                "notifications",
                """
                id BIGSERIAL PRIMARY KEY,
                user_id UUID NOT NULL,
                title VARCHAR(255),
                content TEXT,
                type VARCHAR(50),
                is_read BOOLEAN NOT NULL DEFAULT FALSE,
                created_at TIMESTAMP NOT NULL DEFAULT NOW()
                """);
    }

    /**
     * Multi-device push: backfill từ User.push_token nếu có (1 token/user → 1 row).
     */
    private void ensureUserPushTokensTable() {
        createTableIfNotExists(
                "user_push_tokens",
                """
                id BIGSERIAL PRIMARY KEY,
                user_id UUID NOT NULL,
                token VARCHAR(512) NOT NULL UNIQUE,
                created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                updated_at TIMESTAMP NOT NULL DEFAULT NOW()
                """);
        try {
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_user_push_tokens_user_id ON user_push_tokens(user_id)");
        } catch (Exception e) {
            log.debug("idx_user_push_tokens_user_id: {}", e.getMessage());
        }
        try {
            int n = jdbcTemplate.update(
                    """
                    INSERT INTO user_push_tokens (user_id, token, created_at, updated_at)
                    SELECT u.id, TRIM(u.push_token), NOW(), NOW()
                    FROM "User" u
                    WHERE u.push_token IS NOT NULL
                      AND TRIM(u.push_token) <> ''
                      AND NOT EXISTS (
                          SELECT 1 FROM user_push_tokens t WHERE t.token = TRIM(u.push_token)
                      )
                    """);
            if (n > 0) {
                log.info("Backfilled {} push tokens into user_push_tokens", n);
            }
        } catch (Exception e) {
            log.warn("Backfill user_push_tokens skipped: {}", e.getMessage());
        }
    }

    private void ensureTenantPaymentClaimsTable() {
        createTableIfNotExists(
                "tenant_payment_claims",
                """
                id BIGSERIAL PRIMARY KEY,
                tenant_invoice_id BIGINT NOT NULL REFERENCES tenant_invoices(id),
                tenant_user_id UUID NOT NULL,
                amount NUMERIC(19, 2) NOT NULL,
                method VARCHAR(50) NOT NULL,
                transfer_content TEXT,
                status VARCHAR(30) NOT NULL DEFAULT 'PENDING_VERIFY',
                reject_reason TEXT,
                created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                verified_at TIMESTAMP,
                verified_by UUID
                """);
    }

    private void ensureEquipmentRecommendReplacementColumn() {
        addColumnIfNotExists("equipments", "recommend_replacement", "BOOLEAN DEFAULT false");
        int updated = jdbcTemplate.update(
                "UPDATE equipments SET recommend_replacement = false WHERE recommend_replacement IS NULL");
        if (updated > 0) {
            log.info("Backfilled recommend_replacement=false for {} equipment rows", updated);
        }
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE equipments ALTER COLUMN recommend_replacement SET DEFAULT false");
            jdbcTemplate.execute(
                    "ALTER TABLE equipments ALTER COLUMN recommend_replacement SET NOT NULL");
        } catch (Exception e) {
            log.warn("Could not enforce NOT NULL on equipments.recommend_replacement: {}", e.getMessage());
        }
    }

    private void ensureHostPortalTables() {
        createTableIfNotExists(
                "host_notifications",
                """
                id BIGSERIAL PRIMARY KEY,
                user_id UUID NOT NULL,
                dedupe_key VARCHAR(255) NOT NULL,
                type VARCHAR(50) NOT NULL,
                title VARCHAR(255) NOT NULL,
                message TEXT NOT NULL,
                priority VARCHAR(20),
                is_read BOOLEAN NOT NULL DEFAULT FALSE,
                created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                UNIQUE (user_id, dedupe_key)
                """);
        createTableIfNotExists(
                "host_expenses",
                """
                id BIGSERIAL PRIMARY KEY,
                property_id BIGINT NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
                category VARCHAR(50) NOT NULL,
                amount NUMERIC(19, 2) NOT NULL,
                month VARCHAR(7) NOT NULL,
                note TEXT,
                created_at TIMESTAMP NOT NULL DEFAULT NOW()
                """);
    }

    private void migrateEquipmentOperationalFields() {
        addColumnIfNotExists("equipments", "renovation_session_id",
                "BIGINT REFERENCES renovation_sessions(id)");
        addColumnIfNotExists("equipments", "operational_status",
                "VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'");
        addColumnIfNotExists("equipments", "disabled_at", "TIMESTAMP");
        jdbcTemplate.execute(
                "UPDATE equipments SET operational_status = 'ACTIVE' WHERE operational_status IS NULL");
    }

    private void ensureHandoverEquipmentTable() {
        createTableIfNotExists(
                "handover_equipments",
                """
                id BIGSERIAL PRIMARY KEY,
                property_id BIGINT NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
                catalog_id BIGINT NOT NULL REFERENCES equipment_catalog(id),
                description TEXT,
                room_number VARCHAR(50),
                house_area VARCHAR(50),
                status VARCHAR(50) NOT NULL,
                quantity INT NOT NULL,
                note TEXT
                """);
    }

    /** Hibernate enum check cũ thiếu DAMAGED — cần recreate khi bổ sung enum. */
    private void ensureHandoverEquipmentStatusConstraint() {
        jdbcTemplate.execute(
                "ALTER TABLE handover_equipments DROP CONSTRAINT IF EXISTS handover_equipments_status_check");
        jdbcTemplate.execute("""
                ALTER TABLE handover_equipments ADD CONSTRAINT handover_equipments_status_check
                    CHECK (status IN (
                        'NEW',
                        'GOOD',
                        'DAMAGED',
                        'MAINTENANCE',
                        'BROKEN',
                        'DISPOSED'
                    ))
                """);
        // Cùng enum EquipmentStatus — cập nhật luôn các bảng liên quan nếu còn check cũ
        ensureEquipmentStatusCheck("equipments", "equipments_status_check");
        ensureEquipmentStatusCheck("equipment_manifests", "equipment_manifests_status_check");
        ensureEquipmentStatusCheckColumn(
                "tenant_contract_equipments",
                "condition_at_signing",
                "tenant_contract_equipments_condition_at_signing_check");
        log.info("Ensured EquipmentStatus check constraints include DAMAGED");
    }

    private void ensureEquipmentStatusCheck(String table, String constraintName) {
        ensureEquipmentStatusCheckColumn(table, "status", constraintName);
    }

    private void ensureEquipmentStatusCheckColumn(String table, String column, String constraintName) {
        Boolean tableExists = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1 FROM information_schema.tables
                    WHERE table_schema = 'public' AND table_name = ?
                )
                """,
                Boolean.class,
                table);
        if (!Boolean.TRUE.equals(tableExists)) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + table + " DROP CONSTRAINT IF EXISTS " + constraintName);
        jdbcTemplate.execute("""
                ALTER TABLE %s ADD CONSTRAINT %s
                    CHECK (%s IN (
                        'NEW',
                        'GOOD',
                        'DAMAGED',
                        'MAINTENANCE',
                        'BROKEN',
                        'DISPOSED'
                    ))
                """.formatted(table, constraintName, column));
    }

    private void ensurePropertyPreviousStatusColumn() {
        addColumnIfNotExists("properties", "previous_status", "VARCHAR(50)");
    }

    private void ensureRoomsSoftDeleteColumn() {
        addColumnIfNotExists("rooms", "is_deleted", "BOOLEAN NOT NULL DEFAULT FALSE");
    }

    private void ensurePropertyStatusConstraint() {
        jdbcTemplate.execute("ALTER TABLE properties DROP CONSTRAINT IF EXISTS properties_status_check");
        jdbcTemplate.execute("""
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
                        'INACTIVE',
                        'RENTED'
                    ))
                """);
        log.info("Ensured properties_status_check constraint includes RENOVATION_COMPLETED and RENTED");
    }

    private void ensureEquipmentCatalogSchema() {
        createTableIfNotExists(
                "equipment_catalog",
                """
                id BIGSERIAL PRIMARY KEY,
                name VARCHAR(255) NOT NULL UNIQUE,
                description TEXT,
                active BOOLEAN NOT NULL DEFAULT TRUE
                """);
        renameColumnIfExists("equipment_catalog", "is_active", "active");
        addColumnIfNotExists("equipment_catalog", "active", "BOOLEAN DEFAULT TRUE");
        jdbcTemplate.execute("UPDATE equipment_catalog SET active = TRUE WHERE active IS NULL");
    }

    private void migrateRenovationSessions() {
        createTableIfNotExists(
                "renovation_sessions",
                """
                id BIGSERIAL PRIMARY KEY,
                property_id BIGINT NOT NULL REFERENCES properties(id),
                session_number INT NOT NULL,
                start_date DATE,
                end_date DATE,
                created_at TIMESTAMP DEFAULT NOW(),
                UNIQUE (property_id, session_number)
                """);
        addColumnIfNotExists("renovation_lines", "session_id", "BIGINT REFERENCES renovation_sessions(id)");
        addColumnIfNotExists("renovation_sessions", "status", "VARCHAR(30) NOT NULL DEFAULT 'IN_PROGRESS'");
        addColumnIfNotExists("renovation_sessions", "disabled_at", "TIMESTAMP");
        backfillOrphanRenovationLines();
        backfillRenovationSessionStatus();
    }

    private void backfillRenovationSessionStatus() {
        List<Long> propertyIds = jdbcTemplate.queryForList(
                "SELECT DISTINCT property_id FROM renovation_sessions", Long.class);
        for (Long propertyId : propertyIds) {
            Integer maxClosed = jdbcTemplate.queryForObject(
                    """
                    SELECT MAX(session_number) FROM renovation_sessions
                    WHERE property_id = ? AND end_date IS NOT NULL
                    """,
                    Integer.class,
                    propertyId);
            if (maxClosed != null) {
                jdbcTemplate.update(
                        """
                        UPDATE renovation_sessions SET status = 'ACTIVE', disabled_at = NULL
                        WHERE property_id = ? AND session_number = ? AND (status IS NULL OR status = 'IN_PROGRESS')
                        """,
                        propertyId, maxClosed);
                jdbcTemplate.update(
                        """
                        UPDATE renovation_sessions SET status = 'DISABLED',
                               disabled_at = COALESCE(disabled_at, NOW())
                        WHERE property_id = ? AND session_number < ? AND end_date IS NOT NULL
                          AND status IS DISTINCT FROM 'DISABLED'
                        """,
                        propertyId, maxClosed);
            }
            jdbcTemplate.update(
                    """
                    UPDATE renovation_sessions SET status = 'IN_PROGRESS'
                    WHERE property_id = ? AND end_date IS NULL
                      AND (status IS NULL OR status = '')
                    """,
                    propertyId);
        }
    }

    private void backfillOrphanRenovationLines() {
        List<Long> propertyIds = jdbcTemplate.queryForList(
                "SELECT DISTINCT property_id FROM renovation_lines WHERE session_id IS NULL",
                Long.class);

        for (Long propertyId : propertyIds) {
            List<Long> existingSessionIds = jdbcTemplate.queryForList(
                    "SELECT id FROM renovation_sessions WHERE property_id = ? AND session_number = 1",
                    Long.class,
                    propertyId);

            Long sessionId;
            if (existingSessionIds.isEmpty()) {
                Map<String, Object> dates = jdbcTemplate.queryForMap(
                        "SELECT renovation_start_date, renovation_end_date FROM properties WHERE id = ?",
                        propertyId);
                sessionId = jdbcTemplate.queryForObject(
                        """
                        INSERT INTO renovation_sessions (property_id, session_number, start_date, end_date, status, created_at)
                        VALUES (?, 1, ?, ?, 'ACTIVE', NOW()) RETURNING id
                        """,
                        Long.class,
                        propertyId,
                        dates.get("renovation_start_date"),
                        dates.get("renovation_end_date"));
                log.info("Created default renovation session 1 for property {}", propertyId);
            } else {
                sessionId = existingSessionIds.get(0);
            }

            int updated = jdbcTemplate.update(
                    "UPDATE renovation_lines SET session_id = ? WHERE property_id = ? AND session_id IS NULL",
                    sessionId,
                    propertyId);
            if (updated > 0) {
                log.info("Assigned {} orphan renovation lines to session {} for property {}",
                        updated, sessionId, propertyId);
            }
        }
    }

    private void createTableIfNotExists(String table, String columnDefinitions) {
        Boolean exists = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1 FROM information_schema.tables
                    WHERE table_schema = 'public'
                      AND table_name = ?
                )
                """,
                Boolean.class,
                table);

        if (!Boolean.TRUE.equals(exists)) {
            jdbcTemplate.execute("CREATE TABLE " + table + " (" + columnDefinitions + ")");
            log.info("Created table {}", table);
        }
    }

    private void ensureMeterOverrideTables() {
        createTableIfNotExists(
                "meter_override_tokens",
                """
                id BIGSERIAL PRIMARY KEY,
                token UUID NOT NULL UNIQUE,
                manager_id UUID NOT NULL,
                contract_id BIGINT REFERENCES tenant_contracts(id),
                meter_kind VARCHAR(20) NOT NULL,
                expires_at TIMESTAMP NOT NULL,
                used_at TIMESTAMP,
                created_at TIMESTAMP NOT NULL DEFAULT NOW()
                """);
        createTableIfNotExists(
                "meter_override_logs",
                """
                id BIGSERIAL PRIMARY KEY,
                manager_id UUID NOT NULL,
                contract_id BIGINT NOT NULL,
                meter_kind VARCHAR(20) NOT NULL,
                entered_value NUMERIC(19, 4),
                reason TEXT,
                created_at TIMESTAMP NOT NULL DEFAULT NOW()
                """);
        createTableIfNotExists(
                "meter_override_fail_counters",
                """
                manager_id UUID PRIMARY KEY,
                fail_count INT NOT NULL DEFAULT 0,
                locked_until TIMESTAMP
                """);
    }

    /** Admin-generated one-time passcodes (OTP style). */
    private void ensureMeterOverridePasscodesTable() {
        createTableIfNotExists(
                "meter_override_passcodes",
                """
                id BIGSERIAL PRIMARY KEY,
                code VARCHAR(16) NOT NULL,
                created_by UUID NOT NULL,
                note TEXT,
                expires_at TIMESTAMP NOT NULL,
                used_at TIMESTAMP,
                used_by UUID,
                created_at TIMESTAMP NOT NULL DEFAULT NOW()
                """);
        // Index giúp tra cứu mã còn sống
        try {
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_meter_override_passcodes_code ON meter_override_passcodes(code)");
        } catch (Exception ignored) {
            // already exists / concurrent
        }
    }

    private void alterColumnToUuidIfBigint(String table, String column) {
        List<String> types = jdbcTemplate.queryForList(
                """
                SELECT data_type FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND column_name = ?
                """,
                String.class,
                table,
                column);

        if (types.isEmpty()) {
            return;
        }

        String dataType = types.get(0);
        if ("bigint".equals(dataType)) {
            jdbcTemplate.execute(
                    "ALTER TABLE " + table + " ALTER COLUMN " + column
                            + " TYPE uuid USING NULL");
            log.info("Converted {}.{} from bigint to uuid", table, column);
        }
    }

    private void dropNotNullIfExists(String table, String column) {
        Boolean exists = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = ?
                      AND column_name = ?
                      AND is_nullable = 'NO'
                )
                """,
                Boolean.class,
                table,
                column);

        if (Boolean.TRUE.equals(exists)) {
            jdbcTemplate.execute(
                    "ALTER TABLE " + table + " ALTER COLUMN " + column + " DROP NOT NULL");
            log.info("Dropped NOT NULL on {}.{}", table, column);
        }
    }

    private void renameColumnIfExists(String table, String oldColumn, String newColumn) {
        Boolean oldExists = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = ?
                      AND column_name = ?
                )
                """,
                Boolean.class,
                table,
                oldColumn);

        Boolean newExists = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = ?
                      AND column_name = ?
                )
                """,
                Boolean.class,
                table,
                newColumn);

        if (Boolean.TRUE.equals(oldExists) && !Boolean.TRUE.equals(newExists)) {
            jdbcTemplate.execute(
                    "ALTER TABLE " + table + " RENAME COLUMN " + oldColumn + " TO " + newColumn);
            log.info("Renamed column {}.{} to {}", table, oldColumn, newColumn);
        }
    }

    private void addColumnIfNotExists(String table, String column, String columnDefinition) {
        Boolean exists = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = ?
                      AND column_name = ?
                )
                """,
                Boolean.class,
                table,
                column);

        if (!Boolean.TRUE.equals(exists)) {
            jdbcTemplate.execute(
                    "ALTER TABLE " + table + " ADD COLUMN " + column + " " + columnDefinition);
            log.info("Added column {}.{}", table, column);
        }
    }

    private void dropColumnIfExists(String table, String column) {
        Boolean exists = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = ?
                      AND column_name = ?
                )
                """,
                Boolean.class,
                table,
                column);

        if (Boolean.TRUE.equals(exists)) {
            jdbcTemplate.execute("ALTER TABLE " + table + " DROP COLUMN " + column);
            log.info("Dropped legacy column {}.{}", table, column);
        }
    }

    private void dropTableIfExists(String table) {
        Boolean exists = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1 FROM information_schema.tables
                    WHERE table_schema = 'public'
                      AND table_name = ?
                )
                """,
                Boolean.class,
                table);

        if (Boolean.TRUE.equals(exists)) {
            jdbcTemplate.execute("DROP TABLE " + table + " CASCADE");
            log.info("Dropped legacy table {}", table);
        }
    }

    private void ensureCheckoutRequestsStatusConstraint() {
        try {
            jdbcTemplate.execute("ALTER TABLE checkout_requests DROP CONSTRAINT IF EXISTS checkout_requests_status_check");
            jdbcTemplate.execute("""
                    ALTER TABLE checkout_requests ADD CONSTRAINT checkout_requests_status_check CHECK (status IN (
                        'PENDING', 'APPROVED', 'INSPECTING', 'WAITING_TENANT',
                        'DISPUTED', 'SETTLING', 'REJECTED', 'COMPLETED', 'CANCELLED'
                    ))
                    """);
            log.info("Migrated checkout_requests_status_check constraint to include new statuses");
        } catch (Exception e) {
            log.warn("Could not migrate checkout_requests_status_check constraint: {}", e.getMessage());
        }
    }

    private void ensureTenantContractsStatusConstraint() {
        try {
            jdbcTemplate.execute("ALTER TABLE tenant_contracts DROP CONSTRAINT IF EXISTS tenant_contracts_status_check");
            jdbcTemplate.execute("""
                    ALTER TABLE tenant_contracts ADD CONSTRAINT tenant_contracts_status_check CHECK (status IN (
                        'DRAFT', 'PENDING', 'ACTIVE', 'EXPIRED', 'TERMINATED'
                    ))
                    """);
            log.info("Migrated tenant_contracts_status_check constraint");
        } catch (Exception e) {
            log.warn("Could not migrate tenant_contracts_status_check constraint: {}", e.getMessage());
        }
    }
}
