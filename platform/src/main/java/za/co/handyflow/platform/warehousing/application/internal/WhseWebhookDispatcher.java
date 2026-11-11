package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Delivers the outbox. Every few seconds it claims due deliveries (a short lease, so two servers never send the same one), calls each receiver with a
 * signed POST (5 second limit, redirects not followed, private addresses refused again at send time) and records the answer: DELIVERED, or RETRY with
 * a growing wait, or DEAD after 6 attempts. A webhook that fails 20 times in a row is suspended. Nothing here throws out of the scheduler.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WhseWebhookDispatcher {

    static final Duration TIMEOUT = Duration.ofSeconds(5);
    static final Duration LEASE = Duration.ofMinutes(2);
    static final int BATCH = 20;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(TIMEOUT).build();

    /** A delivery claimed for sending. */
    public record Due(UUID id, UUID tenantId, UUID webhookId, UUID eventId, String eventType, String url, String secret, String body, int attempt) {}
    /** How a send went. {@code code} is null when there was no answer. */
    public record Outcome(boolean ok, Integer code, String detail) {}

    @Scheduled(fixedDelayString = "${handyflow.warehousing.webhook-sync-ms:15000}", initialDelayString = "${handyflow.warehousing.webhook-initial-ms:45000}")
    public void deliverDue() {
        try {
            int n = runOnce();
            if (n > 0) log.debug("[Warehousing] Webhook worker handled {} delivery(ies)", n);
            tidy();
        } catch (Exception e) {
            log.error("[Warehousing] Webhook worker failed: {}", e.getMessage(), e);
        }
    }

    /** Claims and sends one batch. Returns how many were attempted. */
    public int runOnce() {
        List<Due> due = claim();
        for (Due d : due) {
            Outcome o;
            try {
                o = send(d.url(), d.secret(), d.eventType(), d.id(), d.attempt(), d.body());
            } catch (Exception e) {
                o = new Outcome(false, null, "Could not send: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
            try {
                record(d, o);
            } catch (Exception e) {
                log.warn("[Warehousing] Webhook delivery {} result could not be saved: {}", d.id(), e.getMessage());
            }
        }
        return due.size();
    }

    /** Takes due deliveries and pushes their next attempt out by the lease, so they are not claimed again while being sent. */
    List<Due> claim() {
        return tx.execute(s -> {
            List<Due> rows = jdbc.query("""
                    SELECT d.id, d.tenant_id, d.webhook_id, d.event_id, e.event_type, w.url, w.secret, e.payload::text, d.attempts + 1
                      FROM whse_webhook_deliveries d JOIN whse_webhooks w ON w.id = d.webhook_id JOIN whse_outbox_events e ON e.id = d.event_id
                     WHERE d.status IN ('PENDING','RETRY') AND d.next_attempt_at <= now() AND w.deleted_at IS NULL AND w.active = TRUE AND w.suspended = FALSE
                     ORDER BY d.next_attempt_at, d.id LIMIT ? FOR UPDATE OF d SKIP LOCKED""",
                    (rs, n) -> new Due(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), rs.getObject(4, UUID.class), rs.getString(5),
                            rs.getString(6), rs.getString(7), rs.getString(8), rs.getInt(9)), BATCH);
            for (Due d : rows) {
                jdbc.update("UPDATE whse_webhook_deliveries SET next_attempt_at = ?, attempts = attempts + 1, last_attempt_at = now() WHERE id = ?", Timestamp.from(Instant.now().plus(LEASE)), d.id());
            }
            return rows;
        });
    }

    /** Makes the call. Never throws for an unreachable receiver: every problem becomes a failed outcome with a readable detail. */
    public Outcome send(String url, String secret, String eventType, UUID deliveryId, int attempt, String body) {
        try {
            WhseWebhookRules.checkUrl(url);
            URI uri = URI.create(url);
            for (InetAddress a : InetAddress.getAllByName(uri.getHost())) {
                if (WhseWebhookRules.isBlocked(a)) return new Outcome(false, null, "The address points inside a private network, so it was not called");
            }
            HttpRequest req = HttpRequest.newBuilder(uri).timeout(TIMEOUT)
                    .header("Content-Type", "application/json").header("User-Agent", "HandyFlow-Warehousing-Webhooks/1")
                    .header("X-HandyFlow-Event", eventType).header("X-HandyFlow-Delivery", deliveryId.toString()).header("X-HandyFlow-Attempt", String.valueOf(attempt))
                    .header("X-HandyFlow-Signature", WhseWebhookRules.sign(secret, body))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
            int code = http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
            return new Outcome(code >= 200 && code < 300, code, WhseWebhookRules.statusText(code));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Outcome(false, null, "Interrupted");
        } catch (java.net.http.HttpTimeoutException e) {
            return new Outcome(false, null, "The receiver did not answer within 5 seconds");
        } catch (IllegalArgumentException e) {
            return new Outcome(false, null, e.getMessage());
        } catch (Exception e) {
            return new Outcome(false, null, "Could not reach the address: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    /** Saves an attempt's result on the delivery and its webhook. */
    void record(Due d, Outcome o) {
        String detail = o.detail() == null ? null : o.detail().length() > 300 ? o.detail().substring(0, 300) : o.detail();
        tx.executeWithoutResult(s -> {
            if (o.ok()) {
                jdbc.update("UPDATE whse_webhook_deliveries SET status = 'DELIVERED', delivered_at = now(), last_status_code = ?, last_detail = ?, last_attempt_at = now() WHERE id = ?", o.code(), detail, d.id());
                jdbc.update("UPDATE whse_webhooks SET consecutive_failures = 0 WHERE id = ?", d.webhookId());
                return;
            }
            if (WhseWebhookRules.exhausted(d.attempt())) {
                jdbc.update("UPDATE whse_webhook_deliveries SET status = 'DEAD', last_status_code = ?, last_detail = ?, last_attempt_at = now() WHERE id = ?", o.code(), detail, d.id());
            } else {
                jdbc.update("UPDATE whse_webhook_deliveries SET status = 'RETRY', next_attempt_at = ?, last_status_code = ?, last_detail = ?, last_attempt_at = now() WHERE id = ?",
                        Timestamp.from(Instant.now().plus(WhseWebhookRules.backoff(d.attempt()))), o.code(), detail, d.id());
            }
            Integer fails = jdbc.queryForObject("UPDATE whse_webhooks SET consecutive_failures = consecutive_failures + 1 WHERE id = ? RETURNING consecutive_failures", Integer.class, d.webhookId());
            if (fails != null && WhseWebhookRules.shouldSuspend(fails)) {
                jdbc.update("UPDATE whse_webhooks SET suspended = TRUE WHERE id = ?", d.webhookId());
                log.warn("[Warehousing] Webhook {} suspended after {} failures in a row tenant={}", d.webhookId(), fails, d.tenantId());
            }
        });
    }

    /** Drops finished deliveries and events older than 30 days. */
    void tidy() {
        tx.executeWithoutResult(s -> {
            jdbc.update("DELETE FROM whse_webhook_deliveries WHERE status IN ('DELIVERED','DEAD') AND created_at < now() - interval '30 days'");
            jdbc.update("DELETE FROM whse_outbox_events e WHERE e.created_at < now() - interval '30 days' AND NOT EXISTS (SELECT 1 FROM whse_webhook_deliveries d WHERE d.event_id = e.id)");
        });
    }
}
