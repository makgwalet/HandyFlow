package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/** Managing warehousing webhooks and looking at what was sent. The secret is shown once, when a webhook is made or its secret replaced. */
@Service
@RequiredArgsConstructor
public class WhseWebhookService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final WhseWebhookDispatcher dispatcher;

    /** Optional so the services can be built without it; Spring injects it. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private WhsePackageService packages;

    private void requireRoom(TenantId tenantId, WhsePackageRules.Limit limit) {
        if (packages != null) packages.requireRoomFor(tenantId, limit);
    }

    public record Webhook(UUID id, String name, String url, List<String> events, boolean active, boolean suspended, int consecutiveFailures, Instant createdAt) {}
    public record Created(Webhook webhook, String secret) {}
    public record Delivery(UUID id, UUID webhookId, String webhookName, String eventType, UUID eventId, String status, int attempts, Instant nextAttemptAt,
                           Integer statusCode, String detail, Instant lastAttemptAt, Instant deliveredAt, Instant createdAt) {}
    public record TestResult(boolean ok, Integer statusCode, String detail) {}

    private static String newSecret() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static Webhook row(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        String ev = rs.getString(4);
        return new Webhook(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), "*".equals(ev) ? List.of("*") : Arrays.asList(ev.split(",")), rs.getBoolean(5), rs.getBoolean(6),
                rs.getInt(7), rs.getTimestamp(8).toInstant());
    }

    private static final String SELECT = "SELECT id, name, url, events, active, suspended, consecutive_failures, created_at FROM whse_webhooks WHERE tenant_id = ? AND deleted_at IS NULL";

    @Transactional(readOnly = true)
    public List<Webhook> list(TenantId tenantId) {
        return jdbc.query(SELECT + " ORDER BY created_at, id", WhseWebhookService::row, tenantId.getValue());
    }

    @Transactional
    public Created create(TenantId tenantId, String name, String url, List<String> events, UUID actor) {
        requireActor(actor);
        WhseWebhookRules.check(name, url, events);
        requireRoom(tenantId, WhsePackageRules.Limit.WEBHOOKS);
        UUID t = tenantId.getValue();
        Integer count = jdbc.queryForObject("SELECT count(*) FROM whse_webhooks WHERE tenant_id = ? AND deleted_at IS NULL", Integer.class, t);
        if (count != null && count >= WhseWebhookRules.MAX_WEBHOOKS) throw new IllegalStateException("At most " + WhseWebhookRules.MAX_WEBHOOKS + " webhooks.");
        UUID id = UUID.randomUUID();
        String secret = newSecret();
        jdbc.update("INSERT INTO whse_webhooks (id, tenant_id, name, url, secret, events, created_by) VALUES (?,?,?,?,?,?,?)",
                id, t, name.strip(), url.strip(), secret, WhseWebhookRules.joinEvents(events), actor);
        audit(t, actor, "WHSE_WEBHOOK_CREATED", id, "{\"name\":" + quote(name.strip()) + "}");
        return new Created(get(tenantId, id), secret);
    }

    @Transactional
    public Webhook update(TenantId tenantId, UUID id, String name, String url, List<String> events, boolean active, UUID actor) {
        requireActor(actor);
        WhseWebhookRules.check(name, url, events);
        require(tenantId, id);
        jdbc.update("UPDATE whse_webhooks SET name = ?, url = ?, events = ?, active = ?, updated_at = now() WHERE tenant_id = ? AND id = ?",
                name.strip(), url.strip(), WhseWebhookRules.joinEvents(events), active, tenantId.getValue(), id);
        audit(tenantId.getValue(), actor, "WHSE_WEBHOOK_UPDATED", id, "{\"active\":" + active + "}");
        return get(tenantId, id);
    }

    @Transactional
    public void delete(TenantId tenantId, UUID id, UUID actor) {
        requireActor(actor);
        require(tenantId, id);
        jdbc.update("UPDATE whse_webhooks SET deleted_at = now(), active = FALSE, updated_at = now() WHERE tenant_id = ? AND id = ?", tenantId.getValue(), id);
        // Whatever was still waiting for it will never be sent.
        jdbc.update("UPDATE whse_webhook_deliveries SET status = 'DEAD', last_detail = 'The webhook was deleted' WHERE webhook_id = ? AND status IN ('PENDING','RETRY')", id);
        audit(tenantId.getValue(), actor, "WHSE_WEBHOOK_DELETED", id, "{}");
    }

    /** Clears a suspension, so deliveries flow again. */
    @Transactional
    public void resume(TenantId tenantId, UUID id, UUID actor) {
        requireActor(actor);
        require(tenantId, id);
        jdbc.update("UPDATE whse_webhooks SET suspended = FALSE, consecutive_failures = 0, updated_at = now() WHERE tenant_id = ? AND id = ?", tenantId.getValue(), id);
        audit(tenantId.getValue(), actor, "WHSE_WEBHOOK_RESUMED", id, "{}");
    }

    /** Replaces the secret. The old one stops working at once; the new one is returned only here. */
    @Transactional
    public Created rotateSecret(TenantId tenantId, UUID id, UUID actor) {
        requireActor(actor);
        require(tenantId, id);
        String secret = newSecret();
        jdbc.update("UPDATE whse_webhooks SET secret = ?, updated_at = now() WHERE tenant_id = ? AND id = ?", secret, tenantId.getValue(), id);
        audit(tenantId.getValue(), actor, "WHSE_WEBHOOK_SECRET_ROTATED", id, "{}");
        return new Created(get(tenantId, id), secret);
    }

    /** Sends a ping now, outside the outbox, and says how it went, so the address and signature can be checked before relying on it. */
    public TestResult test(TenantId tenantId, UUID id) {
        List<String[]> r = jdbc.query("SELECT url, secret FROM whse_webhooks WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL", (rs, n) -> new String[]{rs.getString(1), rs.getString(2)}, tenantId.getValue(), id);
        if (r.isEmpty()) throw new ResourceNotFoundException("WhseWebhook", id.toString());
        UUID deliveryId = UUID.randomUUID();
        String body = "{\"id\":\"" + deliveryId + "\",\"type\":\"ping\",\"createdAt\":\"" + Instant.now() + "\",\"data\":{\"message\":\"This is a test from HandyFlow.\"}}";
        WhseWebhookDispatcher.Outcome o = dispatcher.send(r.get(0)[0], r.get(0)[1], WhseWebhookRules.PING, deliveryId, 1, body);
        return new TestResult(o.ok(), o.code(), o.detail());
    }

    /** Most recent deliveries first, optionally only those with one status (PENDING, RETRY, DELIVERED, DEAD). */
    @Transactional(readOnly = true)
    public List<Delivery> deliveries(TenantId tenantId, String status, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT d.id, d.webhook_id, w.name, e.event_type, d.event_id, d.status, d.attempts, d.next_attempt_at, d.last_status_code, d.last_detail, d.last_attempt_at, d.delivered_at, d.created_at
                  FROM whse_webhook_deliveries d JOIN whse_webhooks w ON w.id = d.webhook_id JOIN whse_outbox_events e ON e.id = d.event_id WHERE d.tenant_id = ?""");
        java.util.ArrayList<Object> args = new java.util.ArrayList<>(List.of(tenantId.getValue()));
        if (status != null && !status.isBlank()) {
            if (!List.of("PENDING", "RETRY", "DELIVERED", "DEAD").contains(status)) throw new IllegalArgumentException("Unknown status.");
            sql.append(" AND d.status = ?");
            args.add(status);
        }
        sql.append(" ORDER BY d.created_at DESC, d.id LIMIT ?");
        args.add(Math.max(1, Math.min(limit, 200)));
        return jdbc.query(sql.toString(), (rs, n) -> new Delivery(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), rs.getObject(5, UUID.class),
                rs.getString(6), rs.getInt(7), rs.getTimestamp(8).toInstant(), (Integer) rs.getObject(9), rs.getString(10),
                rs.getTimestamp(11) == null ? null : rs.getTimestamp(11).toInstant(), rs.getTimestamp(12) == null ? null : rs.getTimestamp(12).toInstant(),
                rs.getTimestamp(13).toInstant()), args.toArray());
    }

    /** Puts a dead or retrying delivery back in the queue to be sent now, with its attempts counted afresh. */
    @Transactional
    public void redeliver(TenantId tenantId, UUID deliveryId, UUID actor) {
        requireActor(actor);
        int n = jdbc.update("UPDATE whse_webhook_deliveries SET status = 'PENDING', attempts = 0, next_attempt_at = now() WHERE tenant_id = ? AND id = ? AND status IN ('DEAD','RETRY')",
                tenantId.getValue(), deliveryId);
        if (n == 0) throw new IllegalStateException("Only a delivery that failed can be sent again.");
        audit(tenantId.getValue(), actor, "WHSE_WEBHOOK_REDELIVERED", deliveryId, "{}");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Webhook get(TenantId tenantId, UUID id) {
        List<Webhook> r = jdbc.query(SELECT + " AND id = ?", WhseWebhookService::row, tenantId.getValue(), id);
        if (r.isEmpty()) throw new ResourceNotFoundException("WhseWebhook", id.toString());
        return r.get(0);
    }

    private void require(TenantId tenantId, UUID id) { get(tenantId, id); }

    private void audit(UUID t, UUID actor, String action, UUID subject, String extra) {
        jdbc.update("INSERT INTO audit_events (tenant_id, actor_type, actor_id, action, item_type, item_id, after_json) VALUES (?, 'STAFF', ?, ?, 'WHSE_WEBHOOK', ?, ?::jsonb)",
                t, actor, action, subject, extra);
    }

    private static String quote(String s) { return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }

    private static void requireActor(UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
    }
}
