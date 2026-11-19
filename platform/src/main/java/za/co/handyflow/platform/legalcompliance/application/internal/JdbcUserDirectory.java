package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Reads active users straight from the users table (same columns and status literal as the identity module's own TenantAdminRecipientsImpl).
 * Runs in its own read-only transaction so a failure here cannot poison the caller's transaction. A failure is NOT swallowed: launching a
 * campaign to an empty audience by accident would be worse than failing.
 */
@Component
@RequiredArgsConstructor
public class JdbcUserDirectory implements UserDirectory {

    private final JdbcTemplate jdbc;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<Person> activeUsers(TenantId tenantId, String department) {
        String dept = department == null || department.isBlank() ? null : department.trim();
        return jdbc.query("""
                SELECT u.id, u.first_name, u.last_name, u.email
                FROM users u
                WHERE u.tenant_id = ? AND u.status = 'ACTIVE' AND (?::text IS NULL OR LOWER(u.department) = LOWER(?::text))
                ORDER BY u.last_name, u.first_name
                """,
                (rs, i) -> new Person(UUID.fromString(rs.getString("id")), (rs.getString("first_name") + " " + rs.getString("last_name")).trim(), rs.getString("email")),
                tenantId.getValue(), dept, dept);
    }
}
