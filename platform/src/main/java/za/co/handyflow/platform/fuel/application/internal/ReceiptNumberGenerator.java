// fuel/application/internal/ReceiptNumberGenerator.java

package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class ReceiptNumberGenerator {

    private final JdbcTemplate jdbcTemplate;

    // WHY sequential? Mine sites file receipts numerically.
    // Format: FDR-2026-00001
    public String generate() {
        int year = LocalDate.now().getYear();

        // Count completed deliveries this year to get sequence
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fuel_deliveries " +
                        "WHERE receipt_number IS NOT NULL " +
                        "AND EXTRACT(YEAR FROM receipt_generated_at) = ?",
                Integer.class, year
        );

        int seq = (count != null ? count : 0) + 1;
        return String.format("FDR-%d-%05d", year, seq);
    }
}