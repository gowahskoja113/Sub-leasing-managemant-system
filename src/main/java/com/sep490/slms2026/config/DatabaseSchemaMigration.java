package com.sep490.slms2026.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class DatabaseSchemaMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        dropColumnIfExists("depreciation_results", "base_rent");
        dropColumnIfExists("depreciation_results", "original_deposit");
        dropColumnIfExists("depreciation_results", "monthly_operating_cost");
        dropColumnIfExists("inbound_contracts", "base_rent_price");
        dropColumnIfExists("inbound_contracts", "deposit_amount");
        dropColumnIfExists("properties", "deposit");
        dropColumnIfExists("equipments", "name");
        dropColumnIfExists("equipments", "purchase_price");
        dropTableIfExists("renovations");
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
