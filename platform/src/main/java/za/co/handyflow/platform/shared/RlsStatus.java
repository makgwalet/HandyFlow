package za.co.handyflow.platform.shared;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/** Where row-level security stands, read from the database and from this running application. */
public record RlsStatus(boolean sessionTenantEnabled, boolean strict, boolean applicationRoleBypassesRls, int tablesWithTenantId, int tablesCovered, List<String> uncoveredTables,
                        List<UnscopedCallers.Caller> unscopedCallers) {

    static final String STRICT = "SELECT strict FROM hf_rls_config WHERE only_row";
    /** A superuser, or a role with BYPASSRLS, is never held back by row-level security: the policies would do nothing for the application. */
    static final String ROLE = "SELECT (rolsuper OR rolbypassrls) FROM pg_roles WHERE rolname = current_user";
    static final String COVERAGE = "SELECT table_name, (rls_enabled AND rls_forced AND has_policy) AS covered FROM hf_rls_coverage WHERE has_tenant_id ORDER BY table_name";

    public static RlsStatus read(JdbcTemplate jdbc, boolean sessionTenantEnabled) {
        List<Boolean> strict = jdbc.query(STRICT, (rs, i) -> rs.getBoolean(1));
        List<Boolean> bypass = jdbc.query(ROLE, (rs, i) -> rs.getBoolean(1));
        List<String[]> rows = jdbc.query(COVERAGE, (rs, i) -> new String[]{rs.getString(1), String.valueOf(rs.getBoolean(2))});
        List<String> uncovered = rows.stream().filter(r -> !Boolean.parseBoolean(r[1])).map(r -> r[0]).toList();
        return new RlsStatus(sessionTenantEnabled, !strict.isEmpty() && strict.get(0), bypass.isEmpty() || bypass.get(0), rows.size(), rows.size() - uncovered.size(), uncovered, UnscopedCallers.top(20));
    }
}
