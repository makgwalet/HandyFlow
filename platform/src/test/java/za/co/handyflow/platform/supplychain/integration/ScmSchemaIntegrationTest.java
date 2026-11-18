package za.co.handyflow.platform.supplychain.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.handyflow.platform.supplychain.integration.ScmIntegrationSupport.insert;
import static za.co.handyflow.platform.supplychain.integration.ScmIntegrationSupport.row;

/**
 * The database's own refusal of overlapping budgets (V761), against a real Postgres with every migration applied.
 * Needs Docker; skipped, not failed, without it. No Spring context: only Flyway and JDBC.
 */
@Testcontainers(disabledWithoutDocker = true)
class ScmSchemaIntegrationTest {

    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    static JdbcTemplate jdbc;
    static final UUID TENANT = UUID.randomUUID();

    @BeforeAll
    static void migrate() {
        var ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(ds);
    }

    @BeforeEach
    void clean() { jdbc.update("DELETE FROM sc_budgets"); }

    private static Map<String, Object> budget(UUID tenant, String scope, String from, String to, String status) {
        return row("id", UUID.randomUUID(), "tenant_id", tenant, "name", "B", "scope_type", "DEPARTMENT", "scope_value", scope,
                "period_start", java.sql.Date.valueOf(from), "period_end", java.sql.Date.valueOf(to), "amount", java.math.BigDecimal.valueOf(1000),
                "control_mode", "HARD_STOP", "status", status, "currency", "ZAR");
    }

    private static boolean refused(Runnable r) {
        try { r.run(); return false; } catch (RuntimeException e) { return String.valueOf(e.getMessage()).contains("already covers part of"); }
    }

    @Test void overlappingActiveBudgetsForTheSameScopeAreRefused() {
        insert(jdbc, "sc_budgets", budget(TENANT, "Workshop", "2026-01-01", "2026-12-31", "ACTIVE"));
        assertThat(refused(() -> insert(jdbc, "sc_budgets", budget(TENANT, "workshop", "2026-06-01", "2027-05-31", "ACTIVE")))).as("case-insensitive scope, overlapping dates").isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sc_budgets", Integer.class)).isEqualTo(1);
    }

    @Test void adjacentClosedAndOtherTenantBudgetsAreAllowed() {
        insert(jdbc, "sc_budgets", budget(TENANT, "Workshop", "2026-01-01", "2026-12-31", "ACTIVE"));
        insert(jdbc, "sc_budgets", budget(TENANT, "Workshop", "2027-01-01", "2027-12-31", "ACTIVE"));             // starts the day after
        insert(jdbc, "sc_budgets", budget(TENANT, "Workshop", "2026-03-01", "2026-04-01", "CLOSED"));             // closed budgets do not count
        insert(jdbc, "sc_budgets", budget(UUID.randomUUID(), "Workshop", "2026-01-01", "2026-12-31", "ACTIVE")); // another tenant
        insert(jdbc, "sc_budgets", budget(TENANT, "Stores", "2026-01-01", "2026-12-31", "ACTIVE"));              // another scope value
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sc_budgets", Integer.class)).isEqualTo(5);
    }

    @Test void reopeningOrMovingABudgetIntoAnOverlapIsRefusedToo() {
        insert(jdbc, "sc_budgets", budget(TENANT, "Workshop", "2026-01-01", "2026-06-30", "ACTIVE"));
        var closed = budget(TENANT, "Workshop", "2026-03-01", "2026-09-30", "CLOSED");
        insert(jdbc, "sc_budgets", closed);
        assertThat(refused(() -> jdbc.update("UPDATE sc_budgets SET status = 'ACTIVE' WHERE id = ?", closed.get("id")))).isTrue();
    }

    @Test void twoRequestsRacingForTheSameScopeLeaveExactlyOne() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var gate = new CyclicBarrier(2);
        AtomicInteger inserted = new AtomicInteger();
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            var b = budget(TENANT, "Racing", "2026-01-01", "2026-12-31", "ACTIVE");
            fs.add(pool.submit(() -> {
                try { gate.await(); insert(jdbc, "sc_budgets", b); inserted.incrementAndGet(); }
                catch (RuntimeException e) { assertThat(e.getMessage()).contains("already covers part of"); }
                catch (Exception e) { throw new RuntimeException(e); }
            }));
        }
        for (var f : fs) f.get(30, TimeUnit.SECONDS);
        pool.shutdownNow();
        assertThat(inserted.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sc_budgets WHERE scope_value = 'Racing'", Integer.class)).isEqualTo(1);
    }
}
