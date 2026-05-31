package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.TenantId;

@Component
@RequiredArgsConstructor
public class EmployeeNumberGenerator {

    private final JdbcTemplate jdbc;

    // Format: EMP-00001
    public String next(TenantId tenantId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_employees WHERE tenant_id = ?",
                Integer.class, tenantId.getValue()
        );
        int seq = (count != null ? count : 0) + 1;
        return String.format("EMP-%05d", seq);
    }
}