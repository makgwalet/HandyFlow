package za.co.handyflow.platform.marketing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.marketing.dto.DeliveryEventResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Applies provider delivery events (delivered / bounce / complaint / unsubscribe) to campaign state.
 *
 * - IDEMPOTENT: every event is stored once, keyed by (provider, provider_event_id); a retried webhook is a no-op.
 * - TENANT-SAFE: an event is matched ONLY via the campaignContactId we put in the outgoing headers.
 *   The tenant and the address come from OUR row, never from the event body, so an event can
 *   only ever affect the single contact it names.
 * - ORDER-SAFE: delivery_status only moves forward (QUEUED < ACCEPTED < DELIVERED < BOUNCED < COMPLAINED),
 *   so a late "delivered" can't overwrite a bounce.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingDeliveryEventService {

    public enum IngestResult { APPLIED, DUPLICATE, UNMATCHED, IGNORED }

    public record ParsedEvent(String providerEventId, String type, UUID campaignContactId,
                              String email, Instant occurredAt, String detail) {}

    private static final Set<String> TYPES = Set.of("DELIVERED", "HARD_BOUNCE", "SOFT_BOUNCE", "COMPLAINT", "UNSUBSCRIBE");
    private static final Set<String> COUNTERS = Set.of("delivered_count", "bounced_count", "complained_count");

    // Auto-pause thresholds (rate = events / emails sent), only once there is enough volume to mean something.
    private static final int    BOUNCE_MIN_SENT    = 50;
    private static final double BOUNCE_RATE_LIMIT  = 0.10;
    private static final int    COMPLAINT_MIN_SENT = 100;
    private static final double COMPLAINT_RATE_LIMIT = 0.005;

    private final JdbcTemplate           jdbc;
    private final MarketingService       marketingService;

    @Transactional
    public IngestResult ingest(String provider, ParsedEvent e) {
        if (e == null || e.type() == null || !TYPES.contains(e.type())) return IngestResult.IGNORED;

        Instant occurredAt = e.occurredAt() != null ? e.occurredAt() : Instant.now();
        String eventId = e.providerEventId() != null && !e.providerEventId().isBlank()
                ? e.providerEventId() : fingerprint(provider, e, occurredAt);

        List<Map<String, Object>> rows = e.campaignContactId() == null ? List.of()
                : jdbc.queryForList("SELECT tenant_id, campaign_id, email FROM mkt_campaign_contacts WHERE id = ?",
                e.campaignContactId());
        boolean matched = !rows.isEmpty();
        UUID tenantId   = matched ? (UUID) rows.get(0).get("tenant_id") : null;
        UUID campaignId = matched ? (UUID) rows.get(0).get("campaign_id") : null;
        String email    = matched ? (String) rows.get(0).get("email") : e.email();

        int inserted = jdbc.update("""
            INSERT INTO mkt_delivery_events
                (id, tenant_id, campaign_id, campaign_contact_id, provider, provider_event_id,
                 event_type, email, occurred_at, detail, outcome, created_at)
            VALUES (?, CAST(? AS uuid), CAST(? AS uuid), CAST(? AS uuid), ?, ?, ?, CAST(? AS varchar), ?,
                    CAST(? AS text), ?, now())
            ON CONFLICT (provider, provider_event_id) DO NOTHING
            """,
                UUID.randomUUID(), str(tenantId), str(campaignId), str(e.campaignContactId()),
                provider, eventId, e.type(), email, Timestamp.from(occurredAt),
                truncate(e.detail(), 500), matched ? "APPLIED" : "UNMATCHED");
        if (inserted == 0) return IngestResult.DUPLICATE;

        if (!matched) {
            log.warn("Delivery event {} from {} could not be matched to a send (ccId={})",
                    e.type(), provider, e.campaignContactId());
            return IngestResult.UNMATCHED;
        }

        TenantId tenant = TenantId.of(tenantId);
        UUID ccId = e.campaignContactId();

        switch (e.type()) {
            case "DELIVERED" -> {
                if (advance(ccId, "DELIVERED", 2)) bump(campaignId, "delivered_count");
            }
            case "HARD_BOUNCE" -> {
                if (advance(ccId, "BOUNCED", 3)) {
                    jdbc.update("UPDATE mkt_campaign_contacts SET status = 'BOUNCED', error_message = ? "
                            + "WHERE id = ? AND status = 'SENT'", truncate(e.detail(), 250), ccId);
                    bump(campaignId, "bounced_count");
                }
                marketingService.applyDeliverySuppression(tenant, email, "HARD_BOUNCE", provider, e.detail());
            }
            case "SOFT_BOUNCE" -> { /* recorded in the event log only; repeated soft bounces are a future refinement */ }
            case "COMPLAINT" -> {
                if (advance(ccId, "COMPLAINED", 4)) bump(campaignId, "complained_count");
                marketingService.applyDeliverySuppression(tenant, email, "COMPLAINT", provider, e.detail());
            }
            case "UNSUBSCRIBE" ->
                    marketingService.applyDeliverySuppression(tenant, email, "UNSUBSCRIBED", provider, e.detail());
            default -> { }
        }

        evaluateCampaignHealth(campaignId);
        return IngestResult.APPLIED;
    }

    @Transactional(readOnly = true)
    public List<DeliveryEventResponse> listForCampaign(TenantId tenantId, UUID campaignId) {
        return jdbc.query("""
            SELECT id, provider, event_type, email, occurred_at, outcome, detail, created_at
            FROM mkt_delivery_events
            WHERE tenant_id = ? AND campaign_id = ?
            ORDER BY created_at DESC LIMIT 200
            """,
                (rs, i) -> new DeliveryEventResponse(
                        (UUID) rs.getObject("id"), rs.getString("provider"), rs.getString("event_type"),
                        rs.getString("email"), rs.getTimestamp("occurred_at").toInstant(),
                        rs.getString("outcome"), rs.getString("detail"),
                        rs.getTimestamp("created_at").toInstant()),
                tenantId.getValue(), campaignId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Moves delivery_status forward only. Returns true if this call actually advanced it. */
    private boolean advance(UUID ccId, String newStatus, int newRank) {
        String tsColumn = switch (newStatus) {
            case "DELIVERED"  -> "delivered_at";
            case "BOUNCED"    -> "bounced_at";
            case "COMPLAINED" -> "complained_at";
            default -> throw new IllegalArgumentException(newStatus);
        };
        List<Map<String, Object>> advanced = jdbc.queryForList("""
            WITH prev AS (SELECT id, delivery_status AS old_status
                          FROM mkt_campaign_contacts WHERE id = ? FOR UPDATE)
            UPDATE mkt_campaign_contacts cc
            SET delivery_status = ?, %s = now()
            FROM prev
            WHERE cc.id = prev.id
              AND (CASE COALESCE(prev.old_status, 'QUEUED')
                     WHEN 'QUEUED' THEN 0 WHEN 'ACCEPTED' THEN 1 WHEN 'DELIVERED' THEN 2
                     WHEN 'BOUNCED' THEN 3 WHEN 'FAILED' THEN 3 WHEN 'COMPLAINED' THEN 4 ELSE 0 END) < ?
            RETURNING cc.id
            """.formatted(tsColumn), ccId, newStatus, newRank);
        return !advanced.isEmpty();
    }

    private void bump(UUID campaignId, String column) {
        if (!COUNTERS.contains(column)) throw new IllegalArgumentException("Unsupported counter " + column);
        jdbc.update("UPDATE mkt_campaigns SET " + column + " = " + column + " + 1, updated_at = now() WHERE id = ?",
                campaignId);
    }

    /** Deliverability circuit breaker: pause a SENDING campaign whose bounce/complaint rate is unsafe. */
    private void evaluateCampaignHealth(UUID campaignId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT status, sent_count, bounced_count, complained_count FROM mkt_campaigns WHERE id = ?", campaignId);
        if (rows.isEmpty()) return;
        Map<String, Object> c = rows.get(0);
        if (!"SENDING".equals(c.get("status"))) return;

        int sent       = ((Number) c.get("sent_count")).intValue();
        int bounced    = ((Number) c.get("bounced_count")).intValue();
        int complained = ((Number) c.get("complained_count")).intValue();

        String reason = null;
        if (sent >= BOUNCE_MIN_SENT && (double) bounced / sent >= BOUNCE_RATE_LIMIT) {
            reason = String.format("Auto-paused: hard-bounce rate %.1f%% (%d of %d) exceeded the %.0f%% safety limit. "
                    + "Clean your list before resuming.", 100.0 * bounced / sent, bounced, sent, BOUNCE_RATE_LIMIT * 100);
        } else if (sent >= COMPLAINT_MIN_SENT && (double) complained / sent >= COMPLAINT_RATE_LIMIT) {
            reason = String.format("Auto-paused: complaint rate %.2f%% (%d of %d) exceeded the %.1f%% safety limit. "
                            + "Review your audience and consent evidence before resuming.",
                    100.0 * complained / sent, complained, sent, COMPLAINT_RATE_LIMIT * 100);
        }
        if (reason != null) {
            int n = jdbc.update("UPDATE mkt_campaigns SET status = 'PAUSED', pause_reason = ?, updated_at = now(), "
                    + "version = COALESCE(version, 0) + 1 WHERE id = ? AND status = 'SENDING'", reason, campaignId);
            if (n > 0) log.error("Campaign {} {}", campaignId, reason);
        }
    }

    private static String fingerprint(String provider, ParsedEvent e, Instant at) {
        try {
            String raw = provider + "|" + e.type() + "|" + e.campaignContactId() + "|" + e.email() + "|" + at;
            return "fp-" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String str(UUID id) { return id == null ? null : id.toString(); }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}