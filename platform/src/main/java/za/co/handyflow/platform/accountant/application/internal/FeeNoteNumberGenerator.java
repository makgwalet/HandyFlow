package za.co.handyflow.platform.accountant.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.TenantId;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class FeeNoteNumberGenerator {

    private final JdbcTemplate jdbc;

    /** Generates: FN-2026-00001 */
    public String next(TenantId tenantId) {
        int year = LocalDate.now().getYear();
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM acc_fee_notes WHERE tenant_id = ? AND EXTRACT(YEAR FROM created_at) = ?",
                Integer.class, tenantId.getValue(), year);
        int seq = (count != null ? count : 0) + 1;
        return String.format("FN-%d-%05d", year, seq);
    }
}
