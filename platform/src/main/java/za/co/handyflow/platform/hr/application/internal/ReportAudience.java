package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.hr.dto.SavedReportDtos.Colleague;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Who in a company may open the HR reports, and what each person holds. Always limited to the one company and to active users. */
@Component
@RequiredArgsConstructor
public class ReportAudience {

    private final JdbcTemplate jdbc;

    /** Active users who hold the right to open the HR reports, by name. */
    public List<Colleague> readers(TenantId tenantId) {
        return jdbc.query("""
                SELECT DISTINCT u.id, u.first_name || ' ' || u.last_name AS name
                  FROM users u JOIN user_roles ur ON ur.user_id = u.id
                  JOIN role_permissions rp ON rp.role_id = ur.role_id JOIN permissions p ON p.id = rp.permission_id
                 WHERE u.tenant_id = ? AND u.status = 'ACTIVE' AND p.name = 'HR_REPORT_READ'
                 ORDER BY name""", (rs, n) -> new Colleague(UUID.fromString(rs.getString("id")), rs.getString("name")), tenantId.getValue());
    }

    /** The permissions one active user holds in this company; empty when the user is not active here. */
    public Set<String> held(TenantId tenantId, UUID userId) {
        return new HashSet<>(jdbc.query("""
                SELECT DISTINCT p.name FROM users u JOIN user_roles ur ON ur.user_id = u.id
                  JOIN role_permissions rp ON rp.role_id = ur.role_id JOIN permissions p ON p.id = rp.permission_id
                 WHERE u.tenant_id = ? AND u.status = 'ACTIVE' AND u.id = ?""", (rs, n) -> rs.getString(1), tenantId.getValue(), userId));
    }

    public Map<UUID, String> names(TenantId tenantId, Collection<UUID> ids) {
        Map<UUID, String> out = new HashMap<>();
        for (UUID id : ids) {
            jdbc.query("SELECT first_name || ' ' || last_name FROM users WHERE tenant_id = ? AND id = ?",
                    rs -> { out.put(id, rs.getString(1)); }, tenantId.getValue(), id);
        }
        return out;
    }
}
