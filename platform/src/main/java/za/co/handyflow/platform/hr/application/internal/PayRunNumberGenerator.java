package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class PayRunNumberGenerator {

    private final JdbcTemplate jdbc;

    // Format: PR-2026-05 (year-month)
    public String next(TenantId tenantId, LocalDate periodStart) {
        return String.format("PR-%d-%02d",
                periodStart.getYear(), periodStart.getMonthValue());
    }
}