package za.co.handyflow.platform.admin.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.admin.domain.model.AdminAuditLog;
import za.co.handyflow.platform.admin.domain.repository.AdminAuditLogRepository;
import za.co.handyflow.platform.admin.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final AdminAuditLogRepository auditRepo;
    private final JdbcTemplate jdbc;

    // ── Dashboard ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboard() {
        // Platform counts
        long totalTenants   = count("SELECT COUNT(*) FROM tenants ");
        long pilotTenants   = count("SELECT COUNT(*) FROM subscriptions WHERE status = 'PILOT'");
        long activeTenants  = count("SELECT COUNT(*) FROM subscriptions WHERE status = 'ACTIVE'");
        long suspendedTenants = count("SELECT COUNT(*) FROM subscriptions WHERE status = 'SUSPENDED'");

        // MRR — sum of module prices for all ACTIVE module subscriptions
        BigDecimal mrr = queryDecimal("""
            SELECT COALESCE(SUM(mc.monthly_price), 0)
            FROM tenant_modules tm
            JOIN module_catalogue mc ON mc.key = tm.module_key
            WHERE tm.status = 'ACTIVE'
            """);

        // New signups this week
        long newThisWeek = count("""
            SELECT COUNT(*) FROM tenants
            WHERE created_at >= NOW() - INTERVAL '7 days'
            """);

        // Churn this month — cancelled this month
        long churnThisMonth = count("""
            SELECT COUNT(*) FROM subscriptions
            WHERE status = 'CANCELLED'
            AND cancelled_at >= date_trunc('month', NOW())
            """);

        // Trial conversions this month — PILOT → ACTIVE this month
        long conversionsThisMonth = count("""
            SELECT COUNT(*) FROM tenant_modules
            WHERE status = 'ACTIVE'
            AND activated_at >= date_trunc('month', NOW())
            """);

        // Overdue accounts
        long overdueAccounts = count("SELECT COUNT(*) FROM subscriptions WHERE status = 'PAST_DUE'");

        // Pilots expiring soon
        long pilotsExpiring7d = count("""
            SELECT COUNT(*) FROM tenant_modules
            WHERE status = 'TRIAL'
            AND trial_ends_at BETWEEN NOW() AND NOW() + INTERVAL '7 days'
            """);
        long pilotsExpiring14d = count("""
            SELECT COUNT(*) FROM tenant_modules
            WHERE status = 'TRIAL'
            AND trial_ends_at BETWEEN NOW() AND NOW() + INTERVAL '14 days'
            """);
        long pilotsExpiredNoConversion = count("""
            SELECT COUNT(DISTINCT tenant_id) FROM tenant_modules
            WHERE status = 'TRIAL'
            AND trial_ends_at < NOW()
            """);

        // MRR by module
        List<Map<String, Object>> mrrByModule = jdbc.queryForList("""
            SELECT mc.key, mc.name, mc.monthly_price,
                   COUNT(tm.id) AS active_count,
                   COUNT(tm.id) * mc.monthly_price AS module_mrr
            FROM module_catalogue mc
            LEFT JOIN tenant_modules tm ON tm.module_key = mc.key AND tm.status = 'ACTIVE'
            GROUP BY mc.key, mc.name, mc.monthly_price
            ORDER BY module_mrr DESC
            """);

        // Top 10 tenants by MRR
        List<Map<String, Object>> top10 = jdbc.queryForList("""
            SELECT t.id, t.name, t.slug,
                   COALESCE(SUM(mc.monthly_price), 0) AS tenant_mrr,
                   COUNT(tm.id) AS module_count
            FROM tenants t
            LEFT JOIN tenant_modules tm ON tm.tenant_id = t.id AND tm.status = 'ACTIVE'
            LEFT JOIN module_catalogue mc ON mc.key = tm.module_key
            WHERE 1=1
            GROUP BY t.id, t.name, t.slug
            ORDER BY tenant_mrr DESC
            LIMIT 10
            """);

        return new AdminDashboardResponse(
                totalTenants, pilotTenants, activeTenants, suspendedTenants,
                mrr, mrr.multiply(BigDecimal.valueOf(12)),  // ARR projection
                newThisWeek, churnThisMonth, conversionsThisMonth, overdueAccounts,
                pilotsExpiring7d, pilotsExpiring14d, pilotsExpiredNoConversion,
                mrrByModule, top10
        );
    }

    // ── Tenant list ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTenants(String search, String status,
                                                  String sortBy, int page, int size) {
        StringBuilder sql = new StringBuilder("""
            SELECT t.id, t.name, t.slug, t.created_at,
                   s.status AS subscription_status,
                   s.pilot_ends_at,
                   COALESCE(SUM(mc.monthly_price), 0) AS mrr,
                   COUNT(DISTINCT tm.id) AS module_count,
                   COUNT(DISTINCT u.id) AS user_count
            FROM tenants t
            LEFT JOIN subscriptions s ON s.tenant_id = t.id
            LEFT JOIN tenant_modules tm ON tm.tenant_id = t.id AND tm.status = 'ACTIVE'
            LEFT JOIN module_catalogue mc ON mc.key = tm.module_key
            LEFT JOIN users u ON u.tenant_id = t.id 
            WHERE 1=1
            """);

        List<Object> params = new ArrayList<>();
        if (search != null && !search.isBlank()) {
            sql.append(" AND (t.name ILIKE ? OR t.slug ILIKE ?)");
            params.add("%" + search + "%");
            params.add("%" + search + "%");
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND s.status = ?");
            params.add(status);
        }

        sql.append(" GROUP BY t.id, t.name, t.slug, t.created_at, s.status, s.pilot_ends_at");

        String orderBy = switch (sortBy != null ? sortBy : "created_at") {
            case "mrr"         -> " ORDER BY mrr DESC";
            case "name"        -> " ORDER BY t.name ASC";
            case "modules"     -> " ORDER BY module_count DESC";
            default            -> " ORDER BY t.created_at DESC";
        };
        sql.append(orderBy);
        sql.append(" LIMIT ? OFFSET ?");
        params.add(size);
        params.add(page * size);

        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    // ── Tenant detail ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getTenantDetail(String slugOrId) {
        Map<String, Object> tenant = jdbc.queryForMap("""
            SELECT t.id, t.name, t.slug, t.email, t.phone,
                   t.created_at,
                   s.status AS subscription_status,
                   s.pilot_ends_at,
                   s.past_due_since,
                   s.grace_period_days
            FROM tenants t
            LEFT JOIN subscriptions s ON s.tenant_id = t.id
            WHERE (t.slug = ? OR t.id::text = ?)
                        """, slugOrId, slugOrId);

        UUID tenantId = (UUID) tenant.get("id");

        // Active modules
        List<Map<String, Object>> modules = jdbc.queryForList("""
            SELECT tm.module_key, tm.status, tm.trial_ends_at,
                   tm.activated_at, mc.name, mc.monthly_price
            FROM tenant_modules tm
            JOIN module_catalogue mc ON mc.key = tm.module_key
            WHERE tm.tenant_id = ?
            ORDER BY mc.sort_order
            """, tenantId);

        // Users
        List<Map<String, Object>> users = jdbc.queryForList("""
            SELECT u.id, u.first_name, u.last_name, u.email,
                   u.status, u.created_at
            FROM users u
            WHERE u.tenant_id = ? 
            ORDER BY u.created_at
            """, tenantId);

        // MRR for this tenant
        BigDecimal tenantMrr = queryDecimal(
                "SELECT COALESCE(SUM(mc.monthly_price), 0) FROM tenant_modules tm JOIN module_catalogue mc ON mc.key = tm.module_key WHERE tm.tenant_id = ? AND tm.status = 'ACTIVE'",
                tenantId);

        // Recent audit events for this tenant
        List<Map<String, Object>> auditEvents = jdbc.queryForList("""
            SELECT action, admin_email, details, created_at
            FROM admin_audit_log
            WHERE target_id = ?
            ORDER BY created_at DESC LIMIT 20
            """, tenantId.toString());

        tenant.put("modules", modules);
        tenant.put("users", users);
        tenant.put("mrr", tenantMrr);
        tenant.put("auditEvents", auditEvents);
        return tenant;
    }

    // ── Tenant actions ────────────────────────────────────────────────────────

    @Transactional
    public void extendPilot(UUID adminId, String adminEmail, String tenantSlug,
                             int days, String ipAddress) {
        int updated = jdbc.update("""
            UPDATE tenant_modules
            SET trial_ends_at = COALESCE(trial_ends_at, NOW()) + (? || ' days')::INTERVAL,
                updated_at = NOW()
            WHERE tenant_id = (SELECT id FROM tenants WHERE slug = ?)
            AND status = 'TRIAL'
            """, String.valueOf(days), tenantSlug);

        if (updated == 0) throw new HandyFlowException(
                "No trial modules found for tenant: " + tenantSlug,
                HttpStatus.NOT_FOUND, "NOT_FOUND");

        audit(adminId, adminEmail, "EXTEND_PILOT", "TENANT", tenantSlug, tenantSlug,
                "{\"days\":" + days + "}", ipAddress);
        log.info("Admin {} extended pilot for {} by {} days", adminEmail, tenantSlug, days);
    }

    @Transactional
    public void suspendTenant(UUID adminId, String adminEmail, String tenantSlug,
                               String reason, String ipAddress) {
        jdbc.update("""
            UPDATE subscriptions SET status = 'SUSPENDED', updated_at = NOW()
            WHERE tenant_id = (SELECT id FROM tenants WHERE slug = ?)
            """, tenantSlug);

        audit(adminId, adminEmail, "SUSPEND_TENANT", "TENANT", tenantSlug, tenantSlug,
                "{\"reason\":\"" + reason + "\"}", ipAddress);
        log.info("Admin {} suspended tenant: {}", adminEmail, tenantSlug);
    }

    @Transactional
    public void reactivateTenant(UUID adminId, String adminEmail, String tenantSlug,
                                  String ipAddress) {
        jdbc.update("""
            UPDATE subscriptions SET status = 'ACTIVE', updated_at = NOW()
            WHERE tenant_id = (SELECT id FROM tenants WHERE slug = ?)
            """, tenantSlug);

        audit(adminId, adminEmail, "REACTIVATE_TENANT", "TENANT", tenantSlug, tenantSlug,
                null, ipAddress);
        log.info("Admin {} reactivated tenant: {}", adminEmail, tenantSlug);
    }

    @Transactional
    public void forceActivateModule(UUID adminId, String adminEmail, String tenantSlug,
                                     String moduleKey, String ipAddress) {
        int updated = jdbc.update("""
            UPDATE tenant_modules
            SET status = 'ACTIVE', updated_at = NOW()
            WHERE tenant_id = (SELECT id FROM tenants WHERE slug = ?)
            AND module_key = ?
            """, tenantSlug, moduleKey);

        if (updated == 0) {
            // Module not yet subscribed — insert it
            jdbc.update("""
                INSERT INTO tenant_modules (tenant_id, module_key, status, activated_at, created_at, updated_at)
                SELECT id, ?, 'ACTIVE', NOW(), NOW(), NOW() FROM tenants WHERE slug = ?
                ON CONFLICT (tenant_id, module_key) DO UPDATE SET status = 'ACTIVE', updated_at = NOW()
                """, moduleKey, tenantSlug);
        }

        audit(adminId, adminEmail, "FORCE_ACTIVATE_MODULE", "MODULE",
                moduleKey, tenantSlug + "/" + moduleKey,
                "{\"module\":\"" + moduleKey + "\"}", ipAddress);
    }

    @Transactional
    public void forceDeactivateModule(UUID adminId, String adminEmail, String tenantSlug,
                                       String moduleKey, String ipAddress) {
        jdbc.update("""
            UPDATE tenant_modules
            SET status = 'CANCELLED', cancelled_at = NOW(), updated_at = NOW()
            WHERE tenant_id = (SELECT id FROM tenants WHERE slug = ?)
            AND module_key = ?
            """, tenantSlug, moduleKey);

        audit(adminId, adminEmail, "FORCE_DEACTIVATE_MODULE", "MODULE",
                moduleKey, tenantSlug + "/" + moduleKey,
                "{\"module\":\"" + moduleKey + "\"}", ipAddress);
    }

    // ── Incident inbox (uses Desk module INTERNAL channel) ────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getIncidents(String status) {
        StringBuilder sql = new StringBuilder("""
            SELECT dt.id, dt.ticket_number, dt.subject, dt.priority,
                   dt.status, dt.created_at, dt.sla_breached,
                   t.name AS tenant_name, t.slug AS tenant_slug
            FROM desk_tickets dt
            JOIN tenants t ON t.id = dt.tenant_id
            WHERE dt.channel = 'INTERNAL'
            AND dt.deleted_at IS NULL
            """);
        List<Object> params = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND dt.status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY dt.priority DESC, dt.created_at ASC");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    // ── Pilot expiry alerts ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getExpiringPilots(int days) {
        return jdbc.queryForList("""
            SELECT t.id, t.name, t.slug, t.email,
                   MIN(tm.trial_ends_at) AS earliest_expiry,
                   COUNT(tm.id) AS trial_module_count
            FROM tenant_modules tm
            JOIN tenants t ON t.id = tm.tenant_id
            WHERE tm.status = 'TRIAL'
            AND tm.trial_ends_at BETWEEN NOW() AND NOW() + (? || ' days')::INTERVAL
                        GROUP BY t.id, t.name, t.slug, t.email
            ORDER BY earliest_expiry ASC
            """, String.valueOf(days));
    }

    // ── MRR breakdown ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getMrrBreakdown() {
        return jdbc.queryForList("""
            SELECT mc.key, mc.name, mc.category, mc.monthly_price,
                   COUNT(CASE WHEN tm.status = 'ACTIVE' THEN 1 END) AS active_count,
                   COUNT(CASE WHEN tm.status = 'TRIAL' THEN 1 END) AS trial_count,
                   COUNT(CASE WHEN tm.status = 'ACTIVE' THEN 1 END) * mc.monthly_price AS module_mrr
            FROM module_catalogue mc
            LEFT JOIN tenant_modules tm ON tm.module_key = mc.key
            GROUP BY mc.key, mc.name, mc.category, mc.monthly_price, mc.sort_order
            ORDER BY module_mrr DESC, mc.sort_order
            """);
    }

    // ── Overdue accounts ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getOverdueAccounts() {
        return jdbc.queryForList("""
            SELECT t.id, t.name, t.slug, t.email,
                   s.past_due_since,
                   EXTRACT(DAY FROM NOW() - s.past_due_since) AS days_overdue,
                   s.grace_period_days,
                   COALESCE(SUM(mc.monthly_price), 0) AS amount_owed
            FROM subscriptions s
            JOIN tenants t ON t.id = s.tenant_id
            LEFT JOIN tenant_modules tm ON tm.tenant_id = t.id AND tm.status = 'ACTIVE'
            LEFT JOIN module_catalogue mc ON mc.key = tm.module_key
            WHERE s.status IN ('PAST_DUE','SUSPENDED')
                        GROUP BY t.id, t.name, t.slug, t.email, s.past_due_since, s.grace_period_days
            ORDER BY days_overdue DESC
            """);
    }

    // ── Module pricing management ─────────────────────────────────────────────

    @Transactional
    public void updateModulePrice(UUID adminId, String adminEmail, String moduleKey,
                                   BigDecimal newPrice, String ipAddress) {
        BigDecimal oldPrice = queryDecimal(
                "SELECT monthly_price FROM module_catalogue WHERE key = ?", moduleKey);
        jdbc.update("UPDATE module_catalogue SET monthly_price = ? WHERE key = ?",
                newPrice, moduleKey);
        audit(adminId, adminEmail, "UPDATE_MODULE_PRICE", "MODULE",
                moduleKey, moduleKey,
                "{\"oldPrice\":" + oldPrice + ",\"newPrice\":" + newPrice + "}",
                ipAddress);
    }

    // ── Audit log ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAuditLog(String targetId, int page, int size) {
        if (targetId != null) {
            return jdbc.queryForList("""
                SELECT * FROM admin_audit_log
                WHERE target_id = ?
                ORDER BY created_at DESC LIMIT ? OFFSET ?
                """, targetId, size, page * size);
        }
        return jdbc.queryForList("""
            SELECT * FROM admin_audit_log
            ORDER BY created_at DESC LIMIT ? OFFSET ?
            """, size, page * size);
    }

    // ── Module adoption report ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getModuleAdoptionReport() {
        return jdbc.queryForList("""
            SELECT mc.key, mc.name, mc.category, mc.monthly_price,
                   COUNT(CASE WHEN tm.status = 'ACTIVE' THEN 1 END) AS active,
                   COUNT(CASE WHEN tm.status = 'TRIAL'  THEN 1 END) AS trial,
                   COUNT(CASE WHEN tm.status = 'CANCELLED' THEN 1 END) AS cancelled,
                   ROUND(
                       100.0 * COUNT(CASE WHEN tm.status = 'ACTIVE' THEN 1 END)
                       / NULLIF(COUNT(CASE WHEN tm.status IN ('ACTIVE','CANCELLED') THEN 1 END), 0)
                   , 1) AS conversion_rate_pct
            FROM module_catalogue mc
            LEFT JOIN tenant_modules tm ON tm.module_key = mc.key
            GROUP BY mc.key, mc.name, mc.category, mc.monthly_price, mc.sort_order
            ORDER BY active DESC, mc.sort_order
            """);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void audit(UUID adminId, String adminEmail, String action,
                        String targetType, String targetId, String targetName,
                        String details, String ip) {
        auditRepo.save(AdminAuditLog.create(adminId, adminEmail, action,
                targetType, targetId, targetName, details, ip));
    }

    private long count(String sql, Object... args) {
        try {
            Long result = jdbc.queryForObject(sql, Long.class, args);
            return result != null ? result : 0L;
        } catch (Exception e) { return 0L; }
    }

    private BigDecimal queryDecimal(String sql, Object... args) {
        try {
            BigDecimal result = jdbc.queryForObject(sql, BigDecimal.class, args);
            return result != null ? result : BigDecimal.ZERO;
        } catch (Exception e) { return BigDecimal.ZERO; }
    }
}





