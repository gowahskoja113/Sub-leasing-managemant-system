package com.sep490.slms2026.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Hibernate ddl-auto=update thêm cột mới nhưng không xóa cột cũ.
 * Migration này drop các cột legacy sau khi đổi schema inbound/khấu hao.
 */
@Slf4j
@Component
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
}
