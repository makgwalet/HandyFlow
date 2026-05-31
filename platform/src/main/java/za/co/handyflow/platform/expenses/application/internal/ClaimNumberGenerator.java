package za.co.handyflow.platform.expenses.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class ClaimNumberGenerator {

    private final JdbcTemplate jdbc;

    // Format: EXP-2026-00001
    public String next(TenantId tenantId) {
        int year = LocalDate.now().getYear();
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM expense_claims WHERE tenant_id = ? AND EXTRACT(YEAR FROM created_at) = ?",
                Integer.class, tenantId.getValue(), year);
        int seq = (count != null ? count : 0) + 1;
        return String.format("EXP-%d-%05d", year, seq);
    }
}