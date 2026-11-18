package za.co.handyflow.platform.supplychain.integration;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.*;

/**
 * Seeds rows for the SCM integration tests without hand-writing every NOT NULL column: columns the test names are used as
 * given; any other NOT NULL column without a database default gets a neutral value for its type. Foreign keys are not
 * checked while seeding (the container's user is a superuser, so session_replication_role can be switched), which lets a
 * test create a purchase order without also creating a tenant, supplier and user. CHECK constraints still apply.
 */
final class ScmIntegrationSupport {
    private ScmIntegrationSupport() {}

    static void insert(JdbcTemplate jdbc, String table, Map<String, Object> given) {
        record Col(String name, String type) {}
        List<Col> required = new ArrayList<>();
        jdbc.query("SELECT column_name, data_type FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = ? AND is_nullable = 'NO' AND column_default IS NULL",
                (RowCallbackHandler) rs -> required.add(new Col(rs.getString(1), rs.getString(2))), table);

        Map<String, Object> row = new LinkedHashMap<>(given);
        for (Col c : required) row.computeIfAbsent(c.name(), n -> neutral(c.type()));

        String cols = String.join(", ", row.keySet());
        String marks = String.join(", ", Collections.nCopies(row.size(), "?"));
        jdbc.execute((java.sql.Connection con) -> {
            try (var st = con.createStatement()) { st.execute("SET session_replication_role = replica"); }
            try (var ps = con.prepareStatement("INSERT INTO " + table + " (" + cols + ") VALUES (" + marks + ")")) {
                int i = 1;
                for (Object v : row.values()) ps.setObject(i++, v);
                ps.executeUpdate();
            } finally {
                try (var st = con.createStatement()) { st.execute("SET session_replication_role = DEFAULT"); }
            }
            return null;
        });
    }

    private static Object neutral(String type) {
        return switch (type) {
            case "uuid" -> UUID.randomUUID();
            case "character varying", "text", "character" -> "x";
            case "integer", "smallint", "bigint" -> 0;
            case "numeric", "double precision", "real" -> BigDecimal.ZERO;
            case "boolean" -> false;
            case "date" -> Date.valueOf(LocalDate.of(2026, 11, 18));
            case "timestamp with time zone", "timestamp without time zone" -> new Timestamp(System.currentTimeMillis());
            default -> throw new IllegalStateException("Seeder does not know how to fill a column of type " + type + "; name it in the test");
        };
    }

    static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
