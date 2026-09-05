package com.sep490.slms2026.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sep490.slms2026.util.PropertyCodeHelper;

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
        dropColumnIfExists("properties", "managed_by");
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
        dropMaintenanceRequestsStatusCheck();
        dropMaintenanceImagesTypeCheck();
        migrateMaintenanceStatusesToSimplifiedFlow();
        ensureMaintenanceRedesignColumns();
        migrateMaintenanceStatusesToRedesignFlow();
        ensureMaintenanceRequestsStatusConstraint();
        ensureMaintenanceTimelineStatusConstraints();
        ensureMaintenanceImagesTypeConstraint();
        ensureOutstandingDamageTables();
        addColumnIfNotExists("checkout_damage_items", "maintenance_request_id", "BIGINT");
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
        // Dual OTP xác nhận HĐ (= 2 chữ ký điện tử OTP)
        addColumnIfNotExists("tenant_contracts", "tenant_otp_verified_at", "TIMESTAMP");
        addColumnIfNotExists("tenant_contracts", "manager_otp_verified_at", "TIMESTAMP");
        addColumnIfNotExists("tenant_contracts", "confirm_requested_at", "TIMESTAMP");
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
        ensureBillingConfigTable();
        ensurePricingConfigTable();
        ensureUtilityBillsUtilityTypeColumn();
        addColumnIfNotExists("tenant_contracts", "last_escalation_year", "INTEGER");
        addColumnIfNotExists("tenant_contracts", "last_issue_reminder_date", "DATE");
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE tenant_contracts ALTER COLUMN rent_escalation_type TYPE VARCHAR(30)");
        } catch (Exception e) {
            log.debug("rent_escalation_type widen: {}", e.getMessage());
        }
        ensureTenantContractsRentEscalationTypeConstraint();
        addColumnIfNotExists("utility_bills", "reading_deadline", "DATE");
        // Nguyên căn: tách phần khách trả vs công ty chịu khi đón khách giữa kỳ
        addColumnIfNotExists("utility_bills", "billed_to_tenant_quantity", "NUMERIC(19, 4)");
        addColumnIfNotExists("utility_bills", "company_born_quantity", "NUMERIC(19, 4)");
        roundLegacyMeterReadingsToIntegers();
        ensureZoneManagerTables();
        ensureRentalPriceModel();
        ensureCashCollectAndProxyPayTables();
        ensureNotificationDedupeKey();
        ensureInvoiceDisputesTable();
        ensurePropertyCodeColumn();
        ensureMaintenanceAdminReviewColumns();
        ensureMaintenanceAppointmentColumns();
        ensureUtilityInvoiceTenantViewedAtColumn();
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
     * DB constraint otp_verifications_purpose_check phải khớp enum OtpPurpose.
     * Thiếu giá trị → INSERT OTP 500 (đã từng chết luồng kích hoạt tenant 27/07/2026).
     */
    private void ensureOtpVerificationsPurposeConstraint() {
        jdbcTemplate.execute(
                "ALTER TABLE otp_verifications DROP CONSTRAINT IF EXISTS otp_verifications_purpose_check");
        jdbcTemplate.execute("""
                ALTER TABLE otp_verifications ADD CONSTRAINT otp_verifications_purpose_check
                    CHECK (purpose IN (
                        'CONTRACT_CONFIRM',
                        'CONTRACT_CONFIRM_TENANT',
                        'CONTRACT_CONFIRM_MANAGER',
                        'TENANT_ACTIVATION'
                    ))
                """);
        log.info("Ensured otp_verifications_purpose_check includes dual contract-confirm purposes");
    }

    private void ensureMaintenanceSimplifiedFlowColumns() {
        addColumnIfNotExists("maintenance_requests", "reject_reason", "TEXT");
        addColumnIfNotExists("maintenance_requests", "reject_image_urls", "TEXT");
        addColumnIfNotExists("maintenance_requests", "tenant_contract_id", "BIGINT REFERENCES tenant_contracts(id)");
    }

    private void ensureCostAgreementStatusConstraint() {
        Boolean tableExists = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1 FROM information_schema.tables
                    WHERE table_schema = 'public' AND table_name = 'maintenance_requests'
                )
                """,
                Boolean.class);
        if (!Boolean.TRUE.equals(tableExists)) {
            return;
        }
        // Entity redesign bỏ cột này — DB mới (Hibernate ddl-auto) cần add lại trước khi gắn CHECK.
        addColumnIfNotExists("maintenance_requests", "cost_agreement_status", "VARCHAR(50)");
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE maintenance_requests DROP CONSTRAINT IF EXISTS maintenance_requests_cost_agreement_status_check");
            jdbcTemplate.execute("""
                    ALTER TABLE maintenance_requests ADD CONSTRAINT maintenance_requests_cost_agreement_status_check
                        CHECK (cost_agreement_status IS NULL OR cost_agreement_status IN (
                            'NOT_APPLICABLE',
                            'PENDING',
                            'AGREED',
                            'DISPUTED',
                            'WAIVED'
                        ))
                    """);
            log.info("Ensured maintenance_requests_cost_agreement_status_check includes WAIVED");
        } catch (Exception e) {
            log.warn("Could not ensure maintenance_requests_cost_agreement_status_check: {}", e.getMessage());
        }
    }

    /** Gỡ CHECK status cũ trước backfill — deploy có thể còn constraint thủ công chặn OPEN/IN_REPAIR. */
    private void dropMaintenanceRequestsStatusCheck() {
        dropMaintenanceStatusCheckIfExists("maintenance_requests", "maintenance_requests_status_check");
        dropMaintenanceStatusCheckIfExists("maintenance_timelines", "maintenance_timelines_new_status_check");
        dropMaintenanceStatusCheckIfExists("maintenance_timelines", "maintenance_timelines_old_status_check");
        dropMaintenanceStatusCheckIfExists("maintenance_history", "maintenance_history_new_status_check");
        dropMaintenanceStatusCheckIfExists("maintenance_history", "maintenance_history_old_status_check");
    }

    private void dropMaintenanceImagesTypeCheck() {
        dropMaintenanceStatusCheckIfExists("maintenance_images", "maintenance_images_type_check");
    }

    /** Enum redesign thêm FAULT_EVIDENCE, SELF_REPAIR — CHECK cũ trên deploy chỉ có BEFORE/AFTER/INVOICE/REJECT. */
    private void ensureMaintenanceImagesTypeConstraint() {
        Boolean tableExists = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1 FROM information_schema.tables
                    WHERE table_schema = 'public' AND table_name = 'maintenance_images'
                )
                """,
                Boolean.class);
        if (!Boolean.TRUE.equals(tableExists)) {
            return;
        }
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE maintenance_images DROP CONSTRAINT IF EXISTS maintenance_images_type_check");
            jdbcTemplate.execute("""
                    ALTER TABLE maintenance_images ADD CONSTRAINT maintenance_images_type_check
                        CHECK (type IN (
                            'BEFORE',
                            'FAULT_EVIDENCE',
                            'SELF_REPAIR',
                            'AFTER',
                            'INVOICE'
                        ))
                    """);
            log.info("Ensured maintenance_images_type_check for redesign photo types");
        } catch (Exception e) {
            log.warn("Could not ensure maintenance_images_type_check: {}", e.getMessage());
        }
    }

    private void dropMaintenanceStatusCheckIfExists(String table, String constraintName) {
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
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " DROP CONSTRAINT IF EXISTS " + constraintName);
            log.info("Dropped {} on {} (if present) before maintenance status backfill", constraintName, table);
        } catch (Exception e) {
            log.warn("Could not drop {} on {}: {}", constraintName, table, e.getMessage());
        }
    }

    private static final String MAINTENANCE_REDESIGN_STATUS_IN =
            "'OPEN','REPAIR_SCHEDULED','IN_REPAIR','TENANT_FAULT','PENDING_TENANT_REPAIR','OUTSTANDING_DAMAGE','CLOSED','CANCELLED'";

    /** Đồng bộ CHECK status theo enum redesign — gọi sau migrateMaintenanceStatusesToRedesignFlow(). */
    private void ensureMaintenanceRequestsStatusConstraint() {
        ensureMaintenanceStatusCheck("maintenance_requests", "status",
                "maintenance_requests_status_check", false);
    }

    /** Timeline/history cũng có CHECK status từ Hibernate — bị bỏ sót khi redesign. */
    private void ensureMaintenanceTimelineStatusConstraints() {
        ensureMaintenanceStatusCheck("maintenance_timelines", "old_status",
                "maintenance_timelines_old_status_check", true);
        ensureMaintenanceStatusCheck("maintenance_timelines", "new_status",
                "maintenance_timelines_new_status_check", false);
        ensureMaintenanceStatusCheck("maintenance_history", "old_status",
                "maintenance_history_old_status_check", true);
        ensureMaintenanceStatusCheck("maintenance_history", "new_status",
                "maintenance_history_new_status_check", true);
    }

    private void ensureMaintenanceStatusCheck(String table, String column, String constraintName, boolean allowNull) {
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
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " DROP CONSTRAINT IF EXISTS " + constraintName);
            String nullGuard = allowNull ? column + " IS NULL OR " : "";
            jdbcTemplate.execute(String.format("""
                    ALTER TABLE %s ADD CONSTRAINT %s
                        CHECK (%s%s IN (%s))
                    """, table, constraintName, nullGuard, column, MAINTENANCE_REDESIGN_STATUS_IN));
            log.info("Ensured {} on {}.{}", constraintName, table, column);
        } catch (Exception e) {
            log.warn("Could not ensure {} on {}.{}: {}", constraintName, table, column, e.getMessage());
        }
    }

    private int migrateMaintenanceStatusColumnSimplified(String table, String column) {
        int updated = 0;
        updated += jdbcTemplate.update(
                "UPDATE %s SET %s = 'APPROVED' WHERE %s IN ('ACKNOWLEDGED','SCHEDULED','IN_PROGRESS','ON_HOLD','REOPENED')"
                        .formatted(table, column, column));
        updated += jdbcTemplate.update(
                "UPDATE %s SET %s = 'WAITING_TENANT_CONFIRM' WHERE %s IN ('DONE','PENDING_APPROVAL')"
                        .formatted(table, column, column));
        updated += jdbcTemplate.update(
                "UPDATE %s SET %s = 'CLOSED' WHERE %s IN ('CONFIRMED','RESOLVED')"
                        .formatted(table, column, column));
        return updated;
    }

    private int migrateMaintenanceStatusColumnRedesign(String table, String column) {
        int updated = 0;
        updated += jdbcTemplate.update(
                "UPDATE %s SET %s = 'OPEN' WHERE %s = 'PENDING'".formatted(table, column, column));
        updated += jdbcTemplate.update(
                "UPDATE %s SET %s = 'IN_REPAIR' WHERE %s IN ('APPROVED','WAITING_TENANT_CONFIRM','REJECTED')"
                        .formatted(table, column, column));
        return updated;
    }

    /** Map legacy maintenance statuses sang flow rút gọn (bước trung gian). */
    private void migrateMaintenanceStatusesToSimplifiedFlow() {
        try {
            int updated = 0;
            updated += migrateMaintenanceStatusColumnSimplified("maintenance_requests", "status");
            updated += migrateMaintenanceStatusColumnSimplified("maintenance_timelines", "old_status");
            updated += migrateMaintenanceStatusColumnSimplified("maintenance_timelines", "new_status");
            updated += migrateMaintenanceStatusColumnSimplified("maintenance_history", "old_status");
            updated += migrateMaintenanceStatusColumnSimplified("maintenance_history", "new_status");
            if (updated > 0) {
                log.info("Migrated {} maintenance status columns to simplified statuses", updated);
            }
        } catch (Exception e) {
            log.warn("Could not migrate maintenance statuses: {}", e.getMessage());
        }
    }

    /** Cột mới cho redesign maintenance 2026-09. */
    private void ensureMaintenanceRedesignColumns() {
        addColumnIfNotExists("maintenance_requests", "flow_type", "VARCHAR(50)");
        addColumnIfNotExists("maintenance_requests", "invoice_image_urls", "TEXT");
        addColumnIfNotExists("maintenance_requests", "invoice_vendor", "VARCHAR(255)");
        addColumnIfNotExists("maintenance_requests", "invoice_number", "VARCHAR(100)");
        addColumnIfNotExists("maintenance_requests", "invoice_date", "DATE");
        addColumnIfNotExists("maintenance_requests", "invoice_amount", "DECIMAL(15,2)");
        addColumnIfNotExists("maintenance_requests", "repair_description", "TEXT");
        addColumnIfNotExists("maintenance_requests", "previous_request_id", "BIGINT");
        addColumnIfNotExists("maintenance_requests", "damage_cause", "VARCHAR(50)");
        addColumnIfNotExists("maintenance_requests", "fault_reason", "TEXT");
        addColumnIfNotExists("maintenance_requests", "fault_resolution_path", "VARCHAR(50)");
        addColumnIfNotExists("maintenance_requests", "self_repair_deadline", "DATE");
        addColumnIfNotExists("maintenance_requests", "estimated_damage_amount", "DECIMAL(15,2)");
    }

    /** Cột admin duyệt báo lỗi do khách (luồng report-fault 2026-09). */
    private void ensureMaintenanceAdminReviewColumns() {
        addColumnIfNotExists("maintenance_requests", "admin_reviewed_at", "TIMESTAMP");
        addColumnIfNotExists("maintenance_requests", "admin_reviewed_by", "UUID");
        addColumnIfNotExists("maintenance_requests", "admin_approved", "BOOLEAN");
        addColumnIfNotExists("maintenance_requests", "admin_review_note", "TEXT");
    }

    /** Lịch hẹn xem / sửa bảo trì (2026-09-05). */
    private void ensureMaintenanceAppointmentColumns() {
        addColumnIfNotExists("maintenance_requests", "visit_appointment_at", "TIMESTAMP");
        addColumnIfNotExists("maintenance_requests", "visit_arrival_confirmed_at", "TIMESTAMP");
        addColumnIfNotExists("maintenance_requests", "repair_appointment_at", "TIMESTAMP");
        addColumnIfNotExists("maintenance_requests", "repair_started_at", "TIMESTAMP");
    }

    /** Map status cũ → redesign (OPEN / IN_REPAIR / CLOSED / CANCELLED). */
    private void migrateMaintenanceStatusesToRedesignFlow() {
        try {
            int updated = 0;
            updated += migrateMaintenanceStatusColumnRedesign("maintenance_requests", "status");
            updated += migrateMaintenanceStatusColumnRedesign("maintenance_timelines", "old_status");
            updated += migrateMaintenanceStatusColumnRedesign("maintenance_timelines", "new_status");
            updated += migrateMaintenanceStatusColumnRedesign("maintenance_history", "old_status");
            updated += migrateMaintenanceStatusColumnRedesign("maintenance_history", "new_status");
            updated += jdbcTemplate.update(
                    "UPDATE maintenance_requests SET flow_type = 'NORMAL_WEAR' WHERE flow_type IS NULL AND status IN ('OPEN','IN_REPAIR','CLOSED')");
            if (updated > 0) {
                log.info("Migrated {} maintenance status columns to redesign statuses", updated);
            }
            int rejectPhotos = jdbcTemplate.update(
                    "UPDATE maintenance_images SET type = 'FAULT_EVIDENCE' WHERE type = 'REJECT'");
            if (rejectPhotos > 0) {
                log.info("Migrated {} maintenance_images REJECT → FAULT_EVIDENCE", rejectPhotos);
            }
        } catch (Exception e) {
            log.warn("Could not migrate maintenance redesign statuses: {}", e.getMessage());
        }
    }

    private void ensureOutstandingDamageTables() {
        createTableIfNotExists(
                "outstanding_damage_records",
                """
                id BIGSERIAL PRIMARY KEY,
                maintenance_request_id BIGINT NOT NULL REFERENCES maintenance_requests(id),
                tenant_contract_id BIGINT NOT NULL REFERENCES tenant_contracts(id),
                equipment_id BIGINT,
                label VARCHAR(255) NOT NULL,
                estimated_amount DECIMAL(15,2) NOT NULL,
                note TEXT,
                resolved_at_checkout BOOLEAN NOT NULL DEFAULT FALSE,
                checkout_damage_item_id BIGINT,
                created_at TIMESTAMP NOT NULL DEFAULT NOW()
                """);
        createTableIfNotExists(
                "outstanding_damage_photos",
                """
                record_id BIGINT NOT NULL REFERENCES outstanding_damage_records(id) ON DELETE CASCADE,
                photo_url VARCHAR(500) NOT NULL
                """);
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

    private void ensureNotificationDedupeKey() {
        addColumnIfNotExists("notifications", "screen", "VARCHAR(255)");
        addColumnIfNotExists("notifications", "params_json", "TEXT");
        addColumnIfNotExists("notifications", "dedupe_key", "VARCHAR(255)");
        try {
            jdbcTemplate.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS uq_notifications_user_dedupe
                    ON notifications (user_id, dedupe_key)
                    WHERE dedupe_key IS NOT NULL
                    """);
        } catch (Exception e) {
            log.warn("Could not create uq_notifications_user_dedupe: {}", e.getMessage());
        }
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

    /** Enum RentEscalationType thêm ANNUAL_CALENDAR — constraint Hibernate cũ chỉ có NONE/PERCENT/SCHEDULE. */
    private void ensureTenantContractsRentEscalationTypeConstraint() {
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE tenant_contracts DROP CONSTRAINT IF EXISTS tenant_contracts_rent_escalation_type_check");
            jdbcTemplate.execute("""
                    ALTER TABLE tenant_contracts ADD CONSTRAINT tenant_contracts_rent_escalation_type_check
                        CHECK (rent_escalation_type IN (
                            'NONE', 'PERCENT', 'SCHEDULE', 'ANNUAL_CALENDAR'
                        ))
                    """);
            log.info("Ensured tenant_contracts_rent_escalation_type_check includes ANNUAL_CALENDAR");
        } catch (Exception e) {
            log.warn("Could not migrate tenant_contracts_rent_escalation_type_check: {}", e.getMessage());
        }
    }

    private void ensureBillingConfigTable() {
        createTableIfNotExists(
                "billing_config",
                """
                id BIGINT PRIMARY KEY,
                reminder_lead_days INTEGER NOT NULL DEFAULT 3,
                grace_days INTEGER NOT NULL DEFAULT 2,
                meter_reminder_lead_days INTEGER NOT NULL DEFAULT 1,
                updated_at TIMESTAMP,
                updated_by UUID
                """);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM billing_config WHERE id = 1", Integer.class);
        if (count == null || count == 0) {
            jdbcTemplate.update("""
                    INSERT INTO billing_config
                        (id, reminder_lead_days, grace_days, meter_reminder_lead_days, updated_at)
                    VALUES (1, 3, 2, 1, NOW())
                    """);
            log.info("Seeded billing_config singleton (reminder=3, grace=2, meterReminder=1)");
        }
    }

    private void ensurePricingConfigTable() {
        createTableIfNotExists(
                "pricing_config",
                """
                id BIGINT PRIMARY KEY,
                mode VARCHAR(20) NOT NULL DEFAULT 'FORWARD',
                p_desired NUMERIC(19, 2),
                roi_expected NUMERIC(19, 4),
                o_operation NUMERIC(19, 2) NOT NULL DEFAULT 2000000,
                manager_salaries_json TEXT,
                annual_increase_pct NUMERIC(19, 4) NOT NULL DEFAULT 5,
                escalation_grace_months INTEGER NOT NULL DEFAULT 6,
                new_year_price_lead_months INTEGER NOT NULL DEFAULT 2,
                v_rate_pct NUMERIC(19, 4) NOT NULL DEFAULT 10,
                handover_buffer_months INTEGER NOT NULL DEFAULT 1,
                updated_at TIMESTAMP,
                updated_by UUID
                """);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pricing_config WHERE id = 1", Integer.class);
        if (count == null || count == 0) {
            jdbcTemplate.update("""
                    INSERT INTO pricing_config
                        (id, mode, p_desired, roi_expected, o_operation, manager_salaries_json,
                         annual_increase_pct, escalation_grace_months, new_year_price_lead_months,
                         v_rate_pct, handover_buffer_months, updated_at)
                    VALUES (1, 'FORWARD', 10000000, 0, 2000000, '{}',
                            5, 6, 2, 10, 1, NOW())
                    """);
            log.info("Seeded pricing_config singleton");
        }
    }

    /**
     * Gộp hoá đơn điện/nước admin-chốt trên bảng utility_bills (không tạo bảng mới).
     * Dòng cũ = ELECTRIC. unit_price nới scale để trả đơn giá chưa làm tròn.
     */
    private void ensureUtilityBillsUtilityTypeColumn() {
        Boolean tableUtilityExists = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1 FROM information_schema.tables
                    WHERE table_schema = 'public'
                      AND table_name = 'utility_bills'
                )
                """,
                Boolean.class);
        
        if (!Boolean.TRUE.equals(tableUtilityExists)) {
            // Check if evn_bills exists, then rename it
            Boolean tableEvnExists = jdbcTemplate.queryForObject(
                    """
                    SELECT EXISTS (
                        SELECT 1 FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_name = 'evn_bills'
                    )
                    """,
                    Boolean.class);
            if (Boolean.TRUE.equals(tableEvnExists)) {
                jdbcTemplate.execute("ALTER TABLE evn_bills RENAME TO utility_bills");
                log.info("Renamed table evn_bills to utility_bills");
            } else {
                return; // Let Hibernate create it
            }
        }
        
        // Also rename column total_kwh to total_quantity
        try {
            jdbcTemplate.execute("ALTER TABLE utility_bills RENAME COLUMN total_kwh TO total_quantity");
            log.info("Renamed column total_kwh to total_quantity in utility_bills");
        } catch (Exception e) {
            // Ignore if column already renamed or doesn't exist
        }
        addColumnIfNotExists("utility_bills", "type", "VARCHAR(20) NOT NULL DEFAULT 'ELECTRIC'");
        try {
            jdbcTemplate.update("UPDATE utility_bills SET type = 'ELECTRIC' WHERE type IS NULL OR TRIM(type) = ''");
        } catch (Exception e) {
            log.warn("Could not backfill utility_bills.type: {}", e.getMessage());
        }
        try {
            jdbcTemplate.execute("ALTER TABLE utility_bills ALTER COLUMN unit_price TYPE NUMERIC(19, 8)");
            log.info("Widened utility_bills.unit_price to NUMERIC(19,8)");
        } catch (Exception e) {
            log.warn("Could not alter utility_bills.unit_price scale: {}", e.getMessage());
        }
    }
    private void ensureZoneManagerTables() {
        createTableIfNotExists(
                "zone_managers",
                """
                zone_id UUID PRIMARY KEY,
                manager_id UUID NOT NULL REFERENCES "User"(id),
                assigned_by UUID,
                assigned_at TIMESTAMP
                """
        );
        createTableIfNotExists(
                "zone_manager_handovers",
                """
                id BIGSERIAL PRIMARY KEY,
                zone_id UUID NOT NULL,
                from_manager_id UUID,
                to_manager_id UUID,
                changed_by UUID NOT NULL,
                changed_at TIMESTAMP NOT NULL,
                affected_properties INTEGER,
                affected_contracts INTEGER
                """
        );
    }

    private void ensureRentalPriceModel() {
        addColumnIfNotExists("rooms", "applied_price", "NUMERIC(19, 2)");
        addColumnIfNotExists("properties", "applied_price", "NUMERIC(19, 2)");
        addColumnIfNotExists("tenant_contracts", "base_rent_amount", "NUMERIC(19, 2)");
        addColumnIfNotExists("tenant_contracts", "rent_escalation_type", "VARCHAR(20) DEFAULT 'NONE'");
        addColumnIfNotExists("tenant_contracts", "rent_escalation_percent", "NUMERIC(19, 4)");
        addColumnIfNotExists("tenant_contracts", "rent_schedule_json", "TEXT");
        addColumnIfNotExists("tenant_contracts", "rent_escalation_last_from_month", "INTEGER");
        try {
            jdbcTemplate.update("UPDATE rooms SET applied_price = price WHERE applied_price IS NULL AND price IS NOT NULL");
            jdbcTemplate.update("UPDATE properties SET applied_price = price WHERE applied_price IS NULL AND price IS NOT NULL");
            jdbcTemplate.update("UPDATE tenant_contracts SET base_rent_amount = rent_amount WHERE base_rent_amount IS NULL");
            jdbcTemplate.update("UPDATE tenant_contracts SET rent_escalation_type = 'NONE' WHERE rent_escalation_type IS NULL");
            jdbcTemplate.update("""
                    UPDATE rooms r SET applied_price = c.rent_amount
                    FROM tenant_contracts c
                    WHERE c.room_id = r.id AND c.status IN ('ACTIVE', 'EXPIRED')
                    """);
            jdbcTemplate.update("""
                    UPDATE properties p SET applied_price = c.rent_amount
                    FROM tenant_contracts c
                    WHERE c.property_id = p.id AND c.room_id IS NULL AND c.status IN ('ACTIVE', 'EXPIRED')
                    """);
        } catch (Exception e) {
            log.warn("Could not backfill listed/applied prices: {}", e.getMessage());
        }
        createTableIfNotExists(
                "room_price_history",
                """
                id BIGSERIAL PRIMARY KEY,
                property_id BIGINT NOT NULL,
                room_id BIGINT,
                change_type VARCHAR(30) NOT NULL,
                old_price NUMERIC(19, 2),
                new_price NUMERIC(19, 2) NOT NULL,
                contract_id BIGINT,
                reason TEXT,
                changed_by UUID,
                changed_by_name VARCHAR(255),
                changed_at TIMESTAMP NOT NULL DEFAULT NOW()
                """);
        try {
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_room_price_history_property ON room_price_history(property_id, changed_at DESC)");
        } catch (Exception e) {
            log.warn("Could not create room_price_history index: {}", e.getMessage());
        }
    }

    private void ensureCashCollectAndProxyPayTables() {
        createTableIfNotExists(
                "tenant_invoice_payos_orders",
                """
                id BIGSERIAL PRIMARY KEY,
                invoice_id BIGINT NOT NULL REFERENCES tenant_invoices(id),
                order_code BIGINT NOT NULL UNIQUE,
                checkout_url VARCHAR(1024),
                qr_code TEXT,
                amount NUMERIC(19, 2) NOT NULL,
                created_by UUID,
                purpose VARCHAR(30),
                created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                expired_at TIMESTAMP,
                status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                payer_name VARCHAR(255),
                payer_phone VARCHAR(20),
                unlocked_by_admin UUID
                """);
        try {
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_payos_orders_invoice ON tenant_invoice_payos_orders(invoice_id)");
        } catch (Exception e) {
            log.debug("idx_payos_orders_invoice: {}", e.getMessage());
        }
        backfillPayosOrdersFromInvoices();

        createTableIfNotExists(
                "invoice_unlock_passcodes",
                """
                id BIGSERIAL PRIMARY KEY,
                code VARCHAR(16) NOT NULL,
                invoice_id BIGINT NOT NULL REFERENCES tenant_invoices(id),
                purpose VARCHAR(30) NOT NULL,
                created_by UUID NOT NULL,
                note TEXT,
                expires_at TIMESTAMP NOT NULL,
                used_at TIMESTAMP,
                used_by UUID,
                created_at TIMESTAMP NOT NULL DEFAULT NOW()
                """);
        createTableIfNotExists(
                "invoice_unlock_tokens",
                """
                id BIGSERIAL PRIMARY KEY,
                token UUID NOT NULL UNIQUE,
                manager_id UUID NOT NULL,
                invoice_id BIGINT NOT NULL REFERENCES tenant_invoices(id),
                purpose VARCHAR(30) NOT NULL,
                passcode_id BIGINT NOT NULL,
                unlocked_by_admin UUID NOT NULL,
                expires_at TIMESTAMP NOT NULL,
                used_at TIMESTAMP,
                created_at TIMESTAMP NOT NULL DEFAULT NOW()
                """);
        createTableIfNotExists(
                "invoice_unlock_fail_counters",
                """
                manager_id UUID NOT NULL,
                invoice_id BIGINT NOT NULL,
                fail_count INT NOT NULL DEFAULT 0,
                locked_until TIMESTAMP,
                PRIMARY KEY (manager_id, invoice_id)
                """);
        createTableIfNotExists(
                "invoice_unlock_logs",
                """
                id BIGSERIAL PRIMARY KEY,
                manager_id UUID NOT NULL,
                invoice_id BIGINT NOT NULL,
                purpose VARCHAR(30) NOT NULL,
                unlocked_by_admin UUID NOT NULL,
                passcode_id BIGINT,
                success BOOLEAN NOT NULL DEFAULT TRUE,
                payment_result VARCHAR(50),
                created_at TIMESTAMP NOT NULL DEFAULT NOW()
                """);

        addColumnIfNotExists("tenant_payments", "collection_mode", "VARCHAR(30)");
        addColumnIfNotExists("tenant_payments", "remitted_by", "UUID");
        addColumnIfNotExists("tenant_payments", "remit_method", "VARCHAR(50)");
        addColumnIfNotExists("tenant_payments", "payer_name", "VARCHAR(255)");
        addColumnIfNotExists("tenant_payments", "payer_phone", "VARCHAR(20)");
        addColumnIfNotExists("tenant_payments", "facilitated_by", "UUID");
        addColumnIfNotExists("tenant_payments", "unlocked_by_admin", "UUID");
        addColumnIfNotExists("tenant_payments", "payment_note", "TEXT");
    }

    private void ensureInvoiceDisputesTable() {
        createTableIfNotExists(
                "invoice_disputes",
                """
                id BIGSERIAL PRIMARY KEY,
                invoice_id BIGINT NOT NULL REFERENCES utility_invoices(id),
                tenant_invoice_id BIGINT NOT NULL REFERENCES tenant_invoices(id),
                tenant_contract_id BIGINT NOT NULL REFERENCES tenant_contracts(id),
                status VARCHAR(20) NOT NULL,
                reason VARCHAR(30) NOT NULL,
                note VARCHAR(500) NOT NULL,
                created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                resolved_at TIMESTAMP,
                resolved_by UUID,
                resolution_note VARCHAR(1000),
                replacement_invoice_id BIGINT REFERENCES utility_invoices(id)
                """);
        createTableIfNotExists(
                "invoice_dispute_photos",
                """
                dispute_id BIGINT NOT NULL REFERENCES invoice_disputes(id) ON DELETE CASCADE,
                photo_url VARCHAR(1024) NOT NULL
                """);
        try {
            jdbcTemplate.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS ux_invoice_disputes_active
                    ON invoice_disputes (invoice_id)
                    WHERE status <> 'WITHDRAWN'
                    """);
        } catch (Exception e) {
            log.warn("Could not create ux_invoice_disputes_active: {}", e.getMessage());
        }
    }

    private void backfillPayosOrdersFromInvoices() {
        try {
            int n = jdbcTemplate.update("""
                    INSERT INTO tenant_invoice_payos_orders
                        (invoice_id, order_code, checkout_url, qr_code, amount, purpose, created_at, status)
                    SELECT i.id, i.payos_order_code, i.payos_checkout_url, i.payos_qr_code,
                           i.grand_total, 'SELF', COALESCE(i.created_at, NOW()), 'ACTIVE'
                    FROM tenant_invoices i
                    WHERE i.payos_order_code IS NOT NULL
                      AND NOT EXISTS (
                          SELECT 1 FROM tenant_invoice_payos_orders o
                          WHERE o.order_code = i.payos_order_code
                      )
                    """);
            if (n > 0) {
                log.info("Backfilled {} payos orders from tenant_invoices", n);
            }
        } catch (Exception e) {
            log.warn("Could not backfill tenant_invoice_payos_orders: {}", e.getMessage());
        }
    }

    private void ensurePropertyCodeColumn() {
        addColumnIfNotExists("properties", "property_code", "VARCHAR(32)");
        backfillPropertyCodes();
        try {
            jdbcTemplate.execute("ALTER TABLE properties ALTER COLUMN property_code SET NOT NULL");
        } catch (Exception e) {
            log.warn("Could not set NOT NULL on properties.property_code: {}", e.getMessage());
        }
        try {
            jdbcTemplate.execute(
                    "CREATE UNIQUE INDEX IF NOT EXISTS uq_properties_property_code ON properties (property_code)");
        } catch (Exception e) {
            log.warn("Could not create unique index on properties.property_code: {}", e.getMessage());
        }
    }

    private void backfillPropertyCodes() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, property_name, property_code FROM properties ORDER BY id");
            Set<String> used = new HashSet<>();
            int maxMtx = 0;
            for (Map<String, Object> row : rows) {
                Object rawCode = row.get("property_code");
                if (rawCode != null && !rawCode.toString().isBlank()) {
                    String normalized = PropertyCodeHelper.normalize(rawCode.toString());
                    used.add(normalized);
                    maxMtx = Math.max(maxMtx, PropertyCodeHelper.parseMtxNumber(normalized));
                }
            }

            int updated = 0;
            for (Map<String, Object> row : rows) {
                Object rawCode = row.get("property_code");
                if (rawCode != null && !rawCode.toString().isBlank()) {
                    continue;
                }
                Long id = ((Number) row.get("id")).longValue();
                String propertyName = row.get("property_name") != null
                        ? row.get("property_name").toString() : "";
                String base = PropertyCodeHelper.extractFromPropertyName(propertyName);
                String code;
                if (base == null) {
                    do {
                        code = PropertyCodeHelper.formatMtxCode(++maxMtx);
                    } while (used.contains(code));
                } else {
                    code = base;
                    int suffix = 2;
                    while (used.contains(code)) {
                        String resolved = PropertyCodeHelper.withCollisionSuffix(base, suffix++);
                        if (suffix > 999) {
                            do {
                                code = PropertyCodeHelper.formatMtxCode(++maxMtx);
                            } while (used.contains(code));
                            break;
                        }
                        code = resolved;
                    }
                }
                jdbcTemplate.update("UPDATE properties SET property_code = ? WHERE id = ?", code, id);
                if (base != null && !code.equals(base)) {
                    log.warn("Property {} backfill: collision on '{}', assigned '{}'", id, base, code);
                } else if (base == null) {
                    log.warn("Property {} backfill: no code in name, assigned '{}'", id, code);
                }
                used.add(code);
                updated++;
            }
            if (updated > 0) {
                log.info("Backfilled property_code for {} properties", updated);
            }
        } catch (Exception e) {
            log.warn("Could not backfill properties.property_code: {}", e.getMessage());
        }
    }

    private void ensureUtilityInvoiceTenantViewedAtColumn() {
        addColumnIfNotExists("utility_invoices", "tenant_viewed_at", "TIMESTAMP");
    }

    /**
     * FE từ 27/08 chỉ ghi phần nguyên đồng hồ; dữ liệu cũ còn phần lẻ gây CONSUMPTION_MISMATCH
     * khi kỳ hoá đơn đầu lấy prevReading lẻ trừ newReading nguyên. Làm tròn HALF_UP một lần.
     */
    private void roundLegacyMeterReadingsToIntegers() {
        try {
            int tcElec = jdbcTemplate.update("""
                    UPDATE tenant_contracts
                    SET initial_electric_reading = ROUND(initial_electric_reading)
                    WHERE initial_electric_reading IS NOT NULL
                      AND initial_electric_reading <> ROUND(initial_electric_reading)
                    """);
            int tcWater = jdbcTemplate.update("""
                    UPDATE tenant_contracts
                    SET initial_water_reading = ROUND(initial_water_reading)
                    WHERE initial_water_reading IS NOT NULL
                      AND initial_water_reading <> ROUND(initial_water_reading)
                    """);
            int uiPrev = jdbcTemplate.update("""
                    UPDATE utility_invoices
                    SET prev_reading = ROUND(prev_reading)
                    WHERE prev_reading <> ROUND(prev_reading)
                    """);
            int uiNew = jdbcTemplate.update("""
                    UPDATE utility_invoices
                    SET new_reading = ROUND(new_reading)
                    WHERE new_reading <> ROUND(new_reading)
                    """);
            int uiCons = jdbcTemplate.update("""
                    UPDATE utility_invoices
                    SET consumption = ROUND(consumption)
                    WHERE consumption <> ROUND(consumption)
                    """);
            int mr = jdbcTemplate.update("""
                    UPDATE meter_readings
                    SET reading = ROUND(reading)
                    WHERE reading <> ROUND(reading)
                    """);
            int total = tcElec + tcWater + uiPrev + uiNew + uiCons + mr;
            if (total > 0) {
                log.info("Rounded legacy meter readings to integers: {} rows (tcElec={}, tcWater={}, uiPrev={}, uiNew={}, uiCons={}, mr={})",
                        total, tcElec, tcWater, uiPrev, uiNew, uiCons, mr);
            }
        } catch (Exception e) {
            log.warn("Could not round legacy meter readings: {}", e.getMessage());
        }
    }
}

