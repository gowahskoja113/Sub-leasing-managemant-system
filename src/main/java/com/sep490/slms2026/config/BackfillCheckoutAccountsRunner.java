package com.sep490.slms2026.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class BackfillCheckoutAccountsRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        log.info("Starting backfill for checkout refund accounts...");
        
        String selectSql = """
            SELECT 
                id, 
                note,
                split_part(substring(note from 'TK hoàn cọc: (.*)'), ' — ', 1) as parsed_bank_name,
                split_part(substring(note from 'TK hoàn cọc: (.*)'), ' — ', 2) as parsed_bank_account,
                split_part(substring(note from 'TK hoàn cọc: (.*)'), ' — ', 3) as parsed_account_holder
            FROM checkout_requests
            WHERE note LIKE '%TK hoàn cọc:%'
              AND refund_bank_account IS NULL
              AND split_part(substring(note from 'TK hoàn cọc: (.*)'), ' — ', 2) ~ '^[0-9]{8,20}$'
        """;
        
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(selectSql);
        
        if (rows.isEmpty()) {
            log.info("No checkout requests need backfilling for refund accounts.");
            return;
        }
        
        log.info("Dumping affected rows for checkout refund accounts backfill:");
        for (Map<String, Object> row : rows) {
            log.info("ID: {}, Bank: {}, Account: {}, Holder: {}", 
                     row.get("id"), row.get("parsed_bank_name"), 
                     row.get("parsed_bank_account"), row.get("parsed_account_holder"));
        }
        
        String updateSql = """
            UPDATE checkout_requests
            SET refund_bank_name = split_part(substring(note from 'TK hoàn cọc: (.*)'), ' — ', 1),
                refund_bank_account = split_part(substring(note from 'TK hoàn cọc: (.*)'), ' — ', 2),
                refund_account_holder = split_part(substring(note from 'TK hoàn cọc: (.*)'), ' — ', 3)
            WHERE note LIKE '%TK hoàn cọc:%'
              AND refund_bank_account IS NULL
              AND split_part(substring(note from 'TK hoàn cọc: (.*)'), ' — ', 2) ~ '^[0-9]{8,20}$'
        """;
        
        int updated = jdbcTemplate.update(updateSql);
        log.info("Successfully backfilled {} checkout requests.", updated);
    }
}
