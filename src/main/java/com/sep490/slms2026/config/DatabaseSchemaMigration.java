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
        backfillOrphanRenovationLines();
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
                        INSERT INTO renovation_sessions (property_id, session_number, start_date, end_date, created_at)
                        VALUES (?, 1, ?, ?, NOW()) RETURNING id
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
}
