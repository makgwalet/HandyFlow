package za.co.handyflow.platform.shared;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Tells each connection, as it is taken from the pool, which company it works for (app.tenant_id) and whether it is a cross-company job
 * (app.rls_bypass), so the database's row-level security (V866) can hold the line even if a query forgets its tenant filter.
 * <p>
 * Both settings are written at every checkout, even when empty, so a connection can never carry the previous user's company.
 * Only used when handyflow.rls.session-tenant is true (see TenantSessionConfig).
 */
public class TenantSessionDataSource extends DelegatingDataSource {

    static final String TELL = "SELECT set_config('app.tenant_id', ?, false), set_config('app.rls_bypass', ?, false)";

    public TenantSessionDataSource(DataSource target) { super(target); }

    @Override public Connection getConnection() throws SQLException { return told(super.getConnection()); }

    @Override public Connection getConnection(String username, String password) throws SQLException { return told(super.getConnection(username, password)); }

    private static Connection told(Connection c) throws SQLException {
        try {
            tell(c, TenantSession.current());
            return c;
        } catch (SQLException | RuntimeException e) {
            // A connection that could not be told must not be used: it might still carry another company's id.
            try { c.close(); } catch (SQLException ignored) { /* the original failure is the one to report */ }
            throw e;
        }
    }

    static void tell(Connection c, TenantSession.State s) throws SQLException {
        if (s.tenantId().isEmpty() && !s.bypass()) UnscopedCallers.note(new Throwable().getStackTrace());
        try (PreparedStatement ps = c.prepareStatement(TELL)) {
            ps.setString(1, s.tenantId());
            ps.setString(2, s.bypass() ? "on" : "off");
            ps.execute();
        }
    }
}
