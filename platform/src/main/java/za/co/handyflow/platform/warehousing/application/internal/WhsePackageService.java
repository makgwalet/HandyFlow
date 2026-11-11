package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.warehousing.application.internal.WhsePackageRules.Enforcement;
import za.co.handyflow.platform.warehousing.application.internal.WhsePackageRules.Limit;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** A tenant's Warehousing package, its usage against it, and the checks that refuse new records once a limit is enforced. */
@Service
@RequiredArgsConstructor
public class WhsePackageService {

    private final JdbcTemplate jdbc;

    public record PackageDef(String code, String name, BigDecimal monthlyPrice, Integer maxWarehouses, Integer maxClientAccounts,
                             Integer maxMonthlyTransactions, Integer maxWebhooks, boolean proposed) {}

    public record Meter(String key, String label, long used, Integer limit, boolean over) {}

    public record Usage(boolean assigned, PackageDef pkg, Enforcement enforcement, List<Meter> meters, java.time.LocalDate periodStart, java.time.LocalDate periodEnd) {}

    private static PackageDef row(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new PackageDef(rs.getString("code"), rs.getString("name"), rs.getBigDecimal("monthly_price"), (Integer) rs.getObject("max_warehouses"),
                (Integer) rs.getObject("max_client_accounts"), (Integer) rs.getObject("max_monthly_transactions"), (Integer) rs.getObject("max_webhooks"), rs.getBoolean("proposed"));
    }

    private static final String SELECT = "SELECT code, name, monthly_price, max_warehouses, max_client_accounts, max_monthly_transactions, max_webhooks, proposed FROM whse_subscription_tiers";

    @Transactional(readOnly = true)
    public List<PackageDef> packages() {
        return jdbc.query(SELECT + " ORDER BY sort_order", WhsePackageService::row);
    }

    private record Assigned(PackageDef pkg, Enforcement mode) {}

    private Assigned assigned(TenantId tenantId) {
        List<Assigned> r = jdbc.query("SELECT p.code, p.name, p.monthly_price, p.max_warehouses, p.max_client_accounts, p.max_monthly_transactions, p.max_webhooks, p.proposed, t.enforcement "
                + "FROM whse_tenant_subscription t JOIN whse_subscription_tiers p ON p.code = t.package_code WHERE t.tenant_id = ?",
                (rs, i) -> new Assigned(row(rs, i), WhsePackageRules.parseEnforcement(rs.getString("enforcement"))), tenantId.getValue());
        return r.isEmpty() ? null : r.get(0);
    }

    private long count(String sql, TenantId t) {
        Object[] args = new Object[(int) sql.chars().filter(c -> c == '?').count()];
        java.util.Arrays.fill(args, t.getValue());
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private long used(Limit l, TenantId t) {
        return switch (l) {
            case WAREHOUSES -> count("SELECT count(*) FROM whse_warehouses WHERE tenant_id = ? AND active AND deleted_at IS NULL", t);
            case CLIENT_ACCOUNTS -> count("SELECT count(*) FROM whse_clients WHERE tenant_id = ? AND deleted_at IS NULL", t);
            case WEBHOOKS -> count("SELECT count(*) FROM whse_webhooks WHERE tenant_id = ? AND deleted_at IS NULL", t);
            // Goods receipts plus customer orders recorded in the usage ledger during the tenant's current billing month.
            case MONTHLY_TRANSACTIONS -> transactionsThisPeriod(t);
        };
    }

    private static final java.time.ZoneId ZONE = java.time.ZoneId.of("Africa/Johannesburg");

    /** The billing month the tenant's Warehousing subscription is in now: it renews on the subscription's billing-anchor day. */
    public WhseUsagePeriod.Period currentPeriod(TenantId t) {
        List<Integer> anchor = jdbc.query("SELECT billing_anchor FROM tenant_modules WHERE tenant_id = ? AND module_key = 'warehousing'", (rs, i) -> (Integer) rs.getObject(1), t.getValue());
        return WhseUsagePeriod.containing(java.time.LocalDate.now(ZONE), anchor.isEmpty() ? null : anchor.get(0));
    }

    private long transactionsThisPeriod(TenantId t) {
        WhseUsagePeriod.Period p = currentPeriod(t);
        Long n = jdbc.queryForObject("SELECT count(*) FROM whse_usage_events WHERE tenant_id = ? AND meter = 'TRANSACTION' AND occurred_at >= ? AND occurred_at < ?",
                Long.class, t.getValue(), java.sql.Timestamp.from(p.start().atStartOfDay(ZONE).toInstant()), java.sql.Timestamp.from(p.endExclusive().atStartOfDay(ZONE).toInstant()));
        return n == null ? 0 : n;
    }

    /**
     * Records a goods receipt or customer order that has just been created, in the same transaction as the record. Recording the same record twice
     * adds nothing, so editing or re-processing never counts again. Recorded for every tenant, assigned a tier or not.
     */
    @Transactional
    public void recordTransaction(TenantId tenantId, String sourceType, UUID sourceId) {
        jdbc.update("INSERT INTO whse_usage_events (id, tenant_id, meter, source_type, source_id) VALUES (?,?,'TRANSACTION',?,?) ON CONFLICT (tenant_id, meter, source_type, source_id) DO NOTHING",
                UUID.randomUUID(), tenantId.getValue(), sourceType, sourceId);
    }

    private static Integer limitOf(PackageDef p, Limit l) {
        return switch (l) {
            case WAREHOUSES -> p.maxWarehouses();
            case CLIENT_ACCOUNTS -> p.maxClientAccounts();
            case MONTHLY_TRANSACTIONS -> p.maxMonthlyTransactions();
            case WEBHOOKS -> p.maxWebhooks();
        };
    }

    @Transactional(readOnly = true)
    public Usage usage(TenantId tenantId) {
        Assigned a = assigned(tenantId);
        List<Meter> meters = new java.util.ArrayList<>();
        for (Limit l : Limit.values()) {
            long u = used(l, tenantId);
            Integer max = a == null ? null : limitOf(a.pkg(), l);
            meters.add(new Meter(l.name(), l.label, u, max, a != null && a.mode() != Enforcement.OFF && WhsePackageRules.isOver(u, max)));
        }
        WhseUsagePeriod.Period period = currentPeriod(tenantId);
        return new Usage(a != null, a == null ? null : a.pkg(), a == null ? Enforcement.OFF : a.mode(), meters, period.start(), period.endExclusive().minusDays(1));
    }

    /** Call before adding a record that counts against a limit. No package, or enforcement not ENFORCE, never refuses. */
    @Transactional(readOnly = true)
    public void requireRoomFor(TenantId tenantId, Limit limit) {
        Assigned a = assigned(tenantId);
        if (a == null || a.mode() != Enforcement.ENFORCE) return;
        WhsePackageRules.checkAdd(limit, used(limit, tenantId), limitOf(a.pkg(), limit), a.mode(), a.pkg().name());
    }

    /** Platform administrators only: give a tenant a package, or clear it (packageCode null). */
    @Transactional
    public Usage assign(TenantId tenantId, String packageCode, String enforcement, UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this change cannot be recorded.");
        if (packageCode == null || packageCode.isBlank()) {
            jdbc.update("DELETE FROM whse_tenant_subscription WHERE tenant_id = ?", tenantId.getValue());
        } else {
            String code = packageCode.strip().toUpperCase();
            Integer known = jdbc.queryForObject("SELECT count(*) FROM whse_subscription_tiers WHERE code = ?", Integer.class, code);
            if (known == null || known == 0) throw new IllegalArgumentException("Unknown package " + code + ".");
            Enforcement mode = WhsePackageRules.parseEnforcement(enforcement);
            jdbc.update("INSERT INTO whse_tenant_subscription (tenant_id, package_code, enforcement, updated_by, updated_at) VALUES (?,?,?,?, now()) "
                    + "ON CONFLICT (tenant_id) DO UPDATE SET package_code = EXCLUDED.package_code, enforcement = EXCLUDED.enforcement, updated_by = EXCLUDED.updated_by, updated_at = now()",
                    tenantId.getValue(), code, mode.name(), actor);
        }
        jdbc.update("INSERT INTO audit_events (tenant_id, actor_type, actor_id, action, item_type, after_json) VALUES (?, 'STAFF', ?, 'WHSE_PACKAGE_ASSIGNED', 'WHSE_PACKAGE', jsonb_build_object('package', ?::text, 'enforcement', ?::text))",
                tenantId.getValue(), actor, packageCode == null ? "" : packageCode.strip().toUpperCase(), enforcement == null ? "" : enforcement);
        return usage(tenantId);
    }

    public record BillingRow(UUID tenantId, String tenantName, String moduleStatus, String tierCode, String enforcement, BigDecimal tierPrice, BigDecimal cataloguePrice,
                             String finding, String message) {}

    /** Platform administrators: every tenant with Warehousing or a tier, with what the platform bills against what the tier says. Rows with nothing to report are left out. */
    @Transactional(readOnly = true)
    public List<BillingRow> billingCheck() {
        BigDecimal catalogue = jdbc.query("SELECT monthly_price FROM module_catalogue WHERE key = 'warehousing'", (rs, i) -> rs.getBigDecimal(1)).stream().findFirst().orElse(null);
        List<BillingRow> out = new java.util.ArrayList<>();
        jdbc.query("SELECT t.id, t.name, m.status AS module_status, s.package_code, s.enforcement, p.monthly_price "
                + "FROM tenants t "
                + "LEFT JOIN tenant_modules m ON m.tenant_id = t.id AND m.module_key = 'warehousing' "
                + "LEFT JOIN whse_tenant_subscription s ON s.tenant_id = t.id "
                + "LEFT JOIN whse_subscription_tiers p ON p.code = s.package_code "
                + "WHERE m.tenant_id IS NOT NULL OR s.tenant_id IS NOT NULL ORDER BY t.name", rs -> {
            String status = rs.getString("module_status");
            boolean active = "ACTIVE".equals(status) || "TRIAL".equals(status);
            boolean tier = rs.getString("package_code") != null;
            BigDecimal price = rs.getBigDecimal("monthly_price");
            WhseTierBillingCheck.Finding f = WhseTierBillingCheck.classify(active, tier, price, catalogue);
            if (f == null) return;
            out.add(new BillingRow((UUID) rs.getObject("id"), rs.getString("name"), status, rs.getString("package_code"), rs.getString("enforcement"), price, catalogue, f.name(), f.message));
        });
        return out;
    }
}
