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

    private final AdminAuditLogRepository    auditRepo;
    private final JdbcTemplate               jdbc;
    private final AdminNotificationService   notificationService;
    private final AdminReportingService adminReportingService;

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

        // Churn this month — tenants whose subscription moved to CANCELLED this month.
        // WHY updated_at? subscriptions has no cancelled_at column; status change timestamp
        // is tracked via updated_at which Hibernate always sets on save.
        long churnThisMonth = count("""
            SELECT COUNT(*) FROM subscriptions
            WHERE status = 'CANCELLED'
            AND updated_at >= date_trunc('month', NOW())
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
        List<Map<String, Object>> mrrByModule = adminReportingService.getModuleMetrics();

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
        // FIX: must update BOTH subscriptions AND tenants.status.
        // FeatureGuard checks tenants.status — updating only subscriptions
        // had no effect on actual access control.
        jdbc.update("""
            UPDATE subscriptions SET status = 'SUSPENDED', updated_at = NOW()
            WHERE tenant_id = (SELECT id FROM tenants WHERE slug = ?)
            """, tenantSlug);
        jdbc.update("""
            UPDATE tenants SET status = 'SUSPENDED', updated_at = NOW()
            WHERE slug = ?
            """, tenantSlug);

        audit(adminId, adminEmail, "SUSPEND_TENANT", "TENANT", tenantSlug, tenantSlug,
                "{\"reason\":\"" + reason + "\"}", ipAddress);

        // Resolve tenant name for the notification (best-effort — slug is fallback)
        String tenantName = resolveTenantName(tenantSlug);
        UUID   tenantId   = resolveTenantId(tenantSlug);
        notificationService.notifyTenantSuspended(tenantId, tenantName, tenantSlug);

        log.info("Admin {} suspended tenant: {}", adminEmail, tenantSlug);
    }

    @Transactional
    public void reactivateTenant(UUID adminId, String adminEmail, String tenantSlug,
                                 String ipAddress) {
        // FIX: mirror of suspendTenant — restore both subscriptions AND tenants.status.
        jdbc.update("""
            UPDATE subscriptions SET status = 'ACTIVE', updated_at = NOW()
            WHERE tenant_id = (SELECT id FROM tenants WHERE slug = ?)
            """, tenantSlug);
        jdbc.update("""
            UPDATE tenants SET status = 'ACTIVE', updated_at = NOW()
            WHERE slug = ?
            """, tenantSlug);

        audit(adminId, adminEmail, "REACTIVATE_TENANT", "TENANT", tenantSlug, tenantSlug,
                null, ipAddress);

        String tenantName = resolveTenantName(tenantSlug);
        UUID   tenantId   = resolveTenantId(tenantSlug);
        notificationService.notifyTenantReactivated(tenantId, tenantName, tenantSlug);

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

        // FIX: tenant_modules alone was never enough — FeatureGuard's real
        // enforcement path (EntitlementService.isModuleActive()) checks the
        // tenant's Plan and module_subscriptions, neither of which this
        // method ever touched. Confirmed via a live 403 tonight: a module
        // could show ACTIVE/accessible here while blocking every real
        // request. moduleKey lowercased to match ModuleSubscription.
        // activate()'s own convention and FeatureGuard's lowercase callers.
         jdbc.update("""
            INSERT INTO module_subscriptions
                (id, tenant_id, module_key, status, price_cents, activated_at, created_at, updated_at, version)
            SELECT gen_random_uuid(), t.id, ?, 'ACTIVE',
                   COALESCE((SELECT monthly_price * 100 FROM module_catalogue WHERE key = ?), 0),
                   NOW(), NOW(), NOW(), 0
            FROM tenants t WHERE t.slug = ?
            ON CONFLICT (tenant_id, module_key) DO UPDATE SET status = 'ACTIVE', updated_at = NOW()
            """, moduleKey.toLowerCase(), moduleKey, tenantSlug);

        audit(adminId, adminEmail, "FORCE_ACTIVATE_MODULE", "MODULE",
                moduleKey, tenantSlug + "/" + moduleKey,
                "{\"module\":\"" + moduleKey + "\"}", ipAddress);
        log.info("Admin {} force-activated module {} for tenant {}", adminEmail, moduleKey, tenantSlug);
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
        log.info("Admin {} force-deactivated module {} for tenant {}", adminEmail, moduleKey, tenantSlug);
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
        // FIX: TEXT alphabetical DESC gives wrong ordering for mixed priority values.
        // URGENT > NORMAL > LOW > HIGH alphabetically — HIGH lands last, wrong.
        // Correct via explicit CASE-based integer ordering.
        sql.append(" ORDER BY CASE dt.priority WHEN 'URGENT' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'NORMAL' THEN 3 ELSE 4 END ASC, dt.created_at ASC");
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
        log.info("Admin {} updated price for {} from R{} to R{}", adminEmail, moduleKey, oldPrice, newPrice);
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

    // ── Incident management (Phase 5) ────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getIncidentDetail(UUID ticketId) {
        Map<String, Object> ticket;
        try {
            ticket = jdbc.queryForMap("""
                SELECT dt.id, dt.ticket_number, dt.subject, dt.description,
                       dt.priority, dt.status, dt.sla_breached,
                       dt.requester_name, dt.requester_email,
                       dt.created_at, dt.updated_at, dt.due_at,
                       dt.first_response_at, dt.resolved_at,
                       t.name AS tenant_name, t.slug AS tenant_slug
                FROM desk_tickets dt
                JOIN tenants t ON t.id = dt.tenant_id
                WHERE dt.id = ?
                  AND dt.channel = 'INTERNAL'
                  AND dt.deleted_at IS NULL
                """, ticketId);
        } catch (Exception e) {
            throw new HandyFlowException("Incident not found: " + ticketId,
                    HttpStatus.NOT_FOUND, "NOT_FOUND");
        }
        List<Map<String, Object>> comments = jdbc.queryForList("""
            SELECT id, author_name, author_type, is_internal, body, created_at
            FROM desk_comments
            WHERE ticket_id = ?
            ORDER BY created_at ASC
            """, ticketId);
        ticket.put("comments", comments);
        return ticket;
    }

    @Transactional
    public void replyToIncident(UUID ticketId, UUID adminId, String adminEmail,
                                String adminFullName, String body) {
        Map<String, Object> ticket;
        try {
            ticket = jdbc.queryForMap("""
                SELECT id, tenant_id, ticket_number, status, first_response_at
                FROM desk_tickets
                WHERE id = ? AND channel = 'INTERNAL' AND deleted_at IS NULL
                """, ticketId);
        } catch (Exception e) {
            throw new HandyFlowException("Incident not found",
                    HttpStatus.NOT_FOUND, "NOT_FOUND");
        }
        String status = (String) ticket.get("status");
        if ("CLOSED".equals(status) || "RESOLVED".equals(status))
            throw new HandyFlowException("Cannot reply to a " + status + " ticket",
                    HttpStatus.BAD_REQUEST, "TICKET_CLOSED");

        String displayName = (adminFullName != null && !adminFullName.isBlank())
                ? adminFullName : "HandyFlow Support";

        UUID commentId = UUID.randomUUID();
        UUID tenantId  = (UUID) ticket.get("tenant_id");
        jdbc.update("""
            INSERT INTO desk_comments
            (id, ticket_id, tenant_id, author_name, author_type, is_internal, body, created_at)
            VALUES (?, ?, ?, ?, 'TEAM', false, ?, NOW())
            """, commentId, ticketId, tenantId, displayName, body);

        // Record first response if not yet set; always update updated_at
        if (ticket.get("first_response_at") == null) {
            jdbc.update("""
                UPDATE desk_tickets SET first_response_at = NOW(), updated_at = NOW()
                WHERE id = ?
                """, ticketId);
        } else {
            jdbc.update("UPDATE desk_tickets SET updated_at = NOW() WHERE id = ?", ticketId);
        }

        audit(adminId, adminEmail, "INCIDENT_REPLY", "TICKET",
                ticketId.toString(), (String) ticket.get("ticket_number"),
                "{\"commentId\":\"" + commentId + "\"}", null);
        log.info("Admin {} replied to incident {}", adminEmail, ticket.get("ticket_number"));
    }

    @Transactional
    public void resolveIncident(UUID ticketId, UUID adminId, String adminEmail) {
        Map<String, Object> ticket;
        try {
            ticket = jdbc.queryForMap("""
                SELECT ticket_number, status FROM desk_tickets
                WHERE id = ? AND channel = 'INTERNAL' AND deleted_at IS NULL
                """, ticketId);
        } catch (Exception e) {
            throw new HandyFlowException("Incident not found",
                    HttpStatus.NOT_FOUND, "NOT_FOUND");
        }
        String currentStatus = (String) ticket.get("status");
        if ("RESOLVED".equals(currentStatus) || "CLOSED".equals(currentStatus))
            throw new HandyFlowException("Ticket is already " + currentStatus,
                    HttpStatus.BAD_REQUEST, "ALREADY_RESOLVED");

        jdbc.update("""
            UPDATE desk_tickets
            SET status = 'RESOLVED', resolved_at = NOW(), updated_at = NOW()
            WHERE id = ?
            """, ticketId);

        // System comment visible in tenant's ticket thread
        jdbc.update("""
            INSERT INTO desk_comments
            (id, ticket_id, tenant_id, author_name, author_type, is_internal, body, created_at)
            SELECT gen_random_uuid(), ?, tenant_id, 'HandyFlow Admin', 'SYSTEM',
                   false, 'Ticket resolved by HandyFlow support team.', NOW()
            FROM desk_tickets WHERE id = ?
            """, ticketId, ticketId);

        audit(adminId, adminEmail, "INCIDENT_RESOLVE", "TICKET",
                ticketId.toString(), (String) ticket.get("ticket_number"), null, null);
        log.info("Admin {} resolved incident {}", adminEmail, ticket.get("ticket_number"));
    }

    @Transactional
    public void assignIncident(UUID ticketId, UUID assignToAdminId,
                               UUID adminId, String adminEmail) {
        // WHY comment not FK? desk_tickets.assigned_to references tenant users,
        // not admin_users. A schema change would couple two bounded contexts.
        // Recording assignment as a system comment is clean and auditable.
        String assigneeName;
        try {
            assigneeName = jdbc.queryForObject(
                    "SELECT full_name FROM admin_users WHERE id = ? AND active = true",
                    String.class, assignToAdminId);
        } catch (Exception e) {
            throw new HandyFlowException("Admin user not found or inactive",
                    HttpStatus.NOT_FOUND, "NOT_FOUND");
        }

        jdbc.update("""
            INSERT INTO desk_comments
            (id, ticket_id, tenant_id, author_name, author_type, is_internal, body, created_at)
            SELECT gen_random_uuid(), ?, tenant_id, 'System', 'SYSTEM',
                   true, ?, NOW()
            FROM desk_tickets WHERE id = ?
            """, ticketId, "Assigned to HandyFlow staff: " + assigneeName, ticketId);

        jdbc.update("UPDATE desk_tickets SET updated_at = NOW() WHERE id = ?", ticketId);

        audit(adminId, adminEmail, "INCIDENT_ASSIGN", "TICKET",
                ticketId.toString(), assignToAdminId.toString(),
                "{\"assignedTo\":\"" + assigneeName + "\"}", null);
        log.info("Admin {} assigned incident {} to {}", adminEmail, ticketId, assigneeName);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAdminStaff() {
        return jdbc.queryForList("""
            SELECT id, email, full_name, role
            FROM admin_users
            WHERE active = true
            ORDER BY full_name
            """);
    }

    // ── Slug resolution ───────────────────────────────────────────────────────────

    public UUID resolveTenantBySlug(String slug) {
        try {
            String id = jdbc.queryForObject(
                    "SELECT id::text FROM tenants WHERE slug = ?", String.class, slug);
            if (id == null) throw new RuntimeException("Not found");
            return UUID.fromString(id);
        } catch (Exception e) {
            throw new HandyFlowException(
                    "Tenant not found: " + slug, HttpStatus.NOT_FOUND, "NOT_FOUND");
        }
    }

    // Alias for notification calls — same as resolveTenantBySlug
    private UUID resolveTenantId(String slug) {
        return resolveTenantBySlug(slug);
    }

    private String resolveTenantName(String slug) {
        try {
            String name = jdbc.queryForObject(
                    "SELECT name FROM tenants WHERE slug = ?", String.class, slug);
            return name != null ? name : slug;
        } catch (Exception e) { return slug; }
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





