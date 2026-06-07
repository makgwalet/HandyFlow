package za.co.handyflow.platform.admin.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Phase 8 — Real-time admin notifications via Server-Sent Events.
 *
 * Two concerns handled here:
 *  1. PERSISTENCE — notifications written to admin_notifications table by
 *     lifecycle hooks in AdminService (new tenant, pilot expiry, invoice paid, etc.)
 *  2. STREAMING — SSE emitters from connected admin browsers receive pushed
 *     events within 5 seconds of a notification being created.
 *
 * WHY SSE instead of WebSocket?
 * SSE is unidirectional (server → client), which is all we need for admin
 * notifications. It works through Vite's proxy, requires no additional
 * protocol upgrade, and survives standard HTTP load balancers without
 * sticky sessions. WebSocket would add complexity for no benefit here.
 *
 * WHY CopyOnWriteArrayList for emitters?
 * Multiple admin tabs may be open simultaneously. The list is written rarely
 * (on connect/disconnect) and read frequently (on each broadcast), making
 * CopyOnWriteArrayList the correct concurrent structure — reads are lock-free
 * and writes copy the array, which is acceptable at low concurrency.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminNotificationService {

    private final JdbcTemplate jdbc;

    // SSE emitters — one per connected admin browser tab
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    // ── Notification types ────────────────────────────────────────────────────
    public static final String TENANT_SIGNED_UP    = "TENANT_SIGNED_UP";
    public static final String PILOT_EXPIRING      = "PILOT_EXPIRING";
    public static final String PILOT_CONVERTED     = "PILOT_CONVERTED";
    public static final String TENANT_SUSPENDED    = "TENANT_SUSPENDED";
    public static final String TENANT_REACTIVATED  = "TENANT_REACTIVATED";
    public static final String INVOICE_PAID        = "INVOICE_PAID";
    public static final String INVOICE_OVERDUE     = "INVOICE_OVERDUE";
    public static final String INCIDENT_RAISED     = "INCIDENT_RAISED";
    public static final String MODULE_ACTIVATED    = "MODULE_ACTIVATED";
    public static final String MODULE_CANCELLED    = "MODULE_CANCELLED";

    // ── SSE connection management ─────────────────────────────────────────────

    /**
     * Called by AdminNotificationController when an admin browser connects.
     * The emitter is kept alive for 5 minutes; the frontend reconnects
     * automatically using EventSource's built-in retry mechanism.
     */
    public SseEmitter subscribe(UUID adminId) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(()    -> emitters.remove(emitter));
        emitter.onError(e       -> emitters.remove(emitter));

        // Send a keepalive comment immediately so the browser knows the connection is live
        try {
            emitter.send(SseEmitter.event().comment("connected").name("ping"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        log.debug("Admin SSE subscriber connected. Total connections: {}", emitters.size());
        return emitter;
    }

    // ── Broadcast to all connected admins ─────────────────────────────────────

    private void broadcast(Map<String, Object> notification) {
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                    .name("notification")
                    .data(notification));
            } catch (IOException e) {
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
    }

    // ── Keepalive ping every 30 seconds ───────────────────────────────────────
    // Without periodic pings, proxies and load balancers close idle SSE connections.

    @Scheduled(fixedDelay = 30_000)
    public void ping() {
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("ping").name("ping"));
            } catch (IOException e) {
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
    }

    // ── Persist + broadcast ───────────────────────────────────────────────────

    @Transactional
    public void notify(String type, String title, String body,
                        UUID tenantId, String tenantName, String tenantSlug,
                        Map<String, Object> metadata) {
        UUID id = UUID.randomUUID();
        String metaJson = metadata != null
            ? "{" + metadata.entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\":\"" + e.getValue() + "\"")
                .reduce((a, b) -> a + "," + b).orElse("") + "}"
            : null;

        jdbc.update("""
            INSERT INTO admin_notifications
              (id, type, title, body, tenant_id, tenant_name, tenant_slug, metadata, read_by, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, '{}', NOW())
            """,
            id, type, title, body, tenantId, tenantName, tenantSlug, metaJson);

        // Build the payload the frontend will receive
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id",          id.toString());
        payload.put("type",        type);
        payload.put("title",       title);
        payload.put("body",        body);
        payload.put("tenantName",  tenantName);
        payload.put("tenantSlug",  tenantSlug);
        payload.put("metadata",    metadata != null ? metadata : Map.of());
        payload.put("createdAt",   java.time.Instant.now().toString());
        payload.put("read",        false);

        broadcast(payload);
        log.info("Admin notification: [{}] {} — {}", type, title, tenantName);
    }

    // ── Convenience factories ─────────────────────────────────────────────────

    public void notifyTenantSignedUp(UUID tenantId, String name, String slug, String plan) {
        notify(TENANT_SIGNED_UP,
            "New tenant signed up",
            name + " signed up on the " + plan + " plan",
            tenantId, name, slug,
            Map.of("plan", plan));
    }

    public void notifyPilotExpiring(UUID tenantId, String name, String slug, int daysLeft) {
        notify(PILOT_EXPIRING,
            "Pilot expiring in " + daysLeft + " day" + (daysLeft == 1 ? "" : "s"),
            name + " has " + daysLeft + " day" + (daysLeft == 1 ? "" : "s") + " left on their trial",
            tenantId, name, slug,
            Map.of("daysLeft", String.valueOf(daysLeft)));
    }

    public void notifyPilotConverted(UUID tenantId, String name, String slug) {
        notify(PILOT_CONVERTED,
            "Pilot converted to paid",
            name + " converted from trial to a paid subscription",
            tenantId, name, slug, null);
    }

    public void notifyTenantSuspended(UUID tenantId, String name, String slug) {
        notify(TENANT_SUSPENDED,
            "Tenant suspended",
            name + " has been suspended",
            tenantId, name, slug, null);
    }

    public void notifyTenantReactivated(UUID tenantId, String name, String slug) {
        notify(TENANT_REACTIVATED,
            "Tenant reactivated",
            name + " has been reactivated",
            tenantId, name, slug, null);
    }

    public void notifyInvoicePaid(UUID tenantId, String name, String slug,
                                   String invoiceNumber, String amount) {
        notify(INVOICE_PAID,
            "Invoice paid — " + amount,
            name + " paid invoice " + invoiceNumber,
            tenantId, name, slug,
            Map.of("invoiceNumber", invoiceNumber, "amount", amount));
    }

    public void notifyInvoiceOverdue(UUID tenantId, String name, String slug,
                                      String invoiceNumber, int daysOverdue) {
        notify(INVOICE_OVERDUE,
            "Invoice overdue — " + daysOverdue + " days",
            name + " invoice " + invoiceNumber + " is " + daysOverdue + " days overdue",
            tenantId, name, slug,
            Map.of("invoiceNumber", invoiceNumber, "daysOverdue", String.valueOf(daysOverdue)));
    }

    public void notifyIncidentRaised(UUID tenantId, String name, String slug,
                                      String subject, String priority) {
        notify(INCIDENT_RAISED,
            "New " + priority + " incident",
            name + ": " + subject,
            tenantId, name, slug,
            Map.of("priority", priority));
    }

    public void notifyModuleActivated(UUID tenantId, String name, String slug, String moduleKey) {
        notify(MODULE_ACTIVATED,
            "Module activated",
            name + " activated " + moduleKey,
            tenantId, name, slug,
            Map.of("moduleKey", moduleKey));
    }

    // ── Read / list ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getNotifications(UUID adminId, int limit, boolean unreadOnly) {
        String adminIdStr = adminId.toString();
        if (unreadOnly) {
            return jdbc.queryForList("""
                SELECT id, type, title, body, tenant_name, tenant_slug,
                       metadata, read_by, created_at,
                       NOT (? = ANY(read_by::text[])) AS unread
                FROM admin_notifications
                WHERE NOT (? = ANY(read_by::text[]))
                ORDER BY created_at DESC
                LIMIT ?
                """, adminIdStr, adminIdStr, limit);
        }
        return jdbc.queryForList("""
            SELECT id, type, title, body, tenant_name, tenant_slug,
                   metadata, read_by, created_at,
                   NOT (? = ANY(read_by::text[])) AS unread
            FROM admin_notifications
            ORDER BY created_at DESC
            LIMIT ?
            """, adminIdStr, limit);
    }

    @Transactional
    public void markRead(UUID notificationId, UUID adminId) {
        // Append adminId to read_by array if not already present
        jdbc.update("""
            UPDATE admin_notifications
            SET read_by = array_append(read_by, ?::uuid)
            WHERE id = ?
              AND NOT (? = ANY(read_by))
            """, adminId, notificationId, adminId);
    }

    @Transactional
    public void markAllRead(UUID adminId) {
        jdbc.update("""
            UPDATE admin_notifications
            SET read_by = array_append(read_by, ?::uuid)
            WHERE NOT (? = ANY(read_by))
            """, adminId, adminId);
    }

    @Transactional(readOnly = true)
    public int getUnreadCount(UUID adminId) {
        String adminIdStr = adminId.toString();
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM admin_notifications
            WHERE NOT (? = ANY(read_by::text[]))
              AND created_at > NOW() - INTERVAL '7 days'
            """, Integer.class, adminIdStr);
        return count != null ? count : 0;
    }

    // ── Scheduled checks ─────────────────────────────────────────────────────
    // Runs daily at 08:00 SAST to emit pilot expiry notifications.

    @Scheduled(cron = "0 0 6 * * *") // 06:00 UTC = 08:00 SAST
    @Transactional
    public void checkPilotExpiries() {
        List<Map<String, Object>> expiring = jdbc.queryForList("""
            SELECT DISTINCT ON (t.id)
                t.id, t.name, t.slug,
                MIN(tm.trial_ends_at) AS earliest_expiry,
                EXTRACT(DAY FROM MIN(tm.trial_ends_at) - NOW())::int AS days_left
            FROM tenants t
            JOIN tenant_modules tm ON tm.tenant_id = t.id
            WHERE tm.status = 'TRIAL'
              AND tm.trial_ends_at BETWEEN NOW() AND NOW() + INTERVAL '7 days'
              AND t.deleted_at IS NULL
            GROUP BY t.id, t.name, t.slug
            HAVING EXTRACT(DAY FROM MIN(tm.trial_ends_at) - NOW())::int IN (7, 3, 1)
            """);

        for (Map<String, Object> row : expiring) {
            UUID tenantId  = (UUID) row.get("id");
            String name    = (String) row.get("name");
            String slug    = (String) row.get("slug");
            int daysLeft   = ((Number) row.get("days_left")).intValue();

            // Only notify once per day — check no notification sent in last 20h
            Integer recentCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM admin_notifications
                WHERE tenant_id = ?
                  AND type = 'PILOT_EXPIRING'
                  AND created_at > NOW() - INTERVAL '20 hours'
                """, Integer.class, tenantId);

            if (recentCount == null || recentCount == 0) {
                notifyPilotExpiring(tenantId, name, slug, daysLeft);
            }
        }
    }
}
