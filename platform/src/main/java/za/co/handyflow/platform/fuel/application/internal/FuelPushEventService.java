package za.co.handyflow.platform.fuel.application.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Fuel push alerts for operational events. Each alert is one row per person, so:
 * <ul>
 *   <li>the same event to the same person is sent once (unique on tenant, event key and person);</li>
 *   <li>a person's own switch for that event type is honoured (the row is kept as SKIPPED);</li>
 *   <li>delivery and failure are recorded; a failure is retried 3 times with growing gaps, then marked GAVE_UP and audited;</li>
 *   <li>a person with no signed-in phone is marked NO_DEVICE, not retried.</li>
 * </ul>
 * Who is told comes from {@link FuelAlertRecipientService}; this class does not hold its own recipient settings. An alert only
 * carries a title, one short line and the screen to open. It never authorises anything: every protected step is checked again
 * by the server when it is attempted. Nothing here can fail the action that caused the alert.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelPushEventService {

    public static final String OVERDUE_DELIVERY = "OVERDUE_DELIVERY";
    public static final String DIP_VARIANCE = "DIP_VARIANCE";
    public static final String SHIFT_OUTSIDE_TOLERANCE = "SHIFT_OUTSIDE_TOLERANCE";
    public static final String ARRIVAL_CHECK_BLOCKED = "ARRIVAL_CHECK_BLOCKED";
    public static final Set<String> EVENT_TYPES = Set.of(OVERDUE_DELIVERY, DIP_VARIANCE, SHIFT_OUTSIDE_TOLERANCE, ARRIVAL_CHECK_BLOCKED);

    static final int MAX_ATTEMPTS = 3;
    static final Duration[] BACKOFF = { Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15) };

    private final JdbcTemplate jdbc;
    private final FuelPushService push;
    private final AuditTrailFacade audit;
    private final ObjectMapper mapper;

    /**
     * Queue an alert for each person. Returns how many were newly queued (a repeat of the same event key queues none).
     * Joins the caller's transaction, so an alert is only queued for an action that happened. Never throws.
     */
    public int publish(TenantId tenant, String eventType, String dedupKey, Collection<UUID> users, String title, String body, Map<String, String> data) {
        try {
            if (!push.isEnabled() || users == null || users.isEmpty() || !EVENT_TYPES.contains(eventType)) return 0;
            int queued = 0;
            Instant now = Instant.now();
            for (UUID user : new LinkedHashSet<>(users)) {
                if (user == null) continue;
                boolean on = jdbc.queryForList("SELECT enabled FROM fuel_push_preferences WHERE tenant_id = ? AND user_id = ? AND event_type = ?",
                        Boolean.class, tenant.getValue(), user, eventType).stream().findFirst().orElse(true);
                int n = jdbc.update("INSERT INTO fuel_push_events (id, tenant_id, event_type, dedup_key, user_id, title, body, data_json, status, next_attempt_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (tenant_id, dedup_key, user_id) DO NOTHING",
                        UUID.randomUUID(), tenant.getValue(), eventType, dedupKey, user, cut(title, 120), cut(body, 300), json(data),
                        on ? "PENDING" : "SKIPPED", on ? java.sql.Timestamp.from(now) : null);
                if (n > 0 && on) queued++;
            }
            if (queued > 0) {
                audit.recordAs(tenant, "SYSTEM", null, "Fuel alerts", "FUEL_PUSH_QUEUED", "FuelPushEvent", null, null,
                        Map.of("eventType", eventType, "dedupKey", dedupKey, "recipients", queued));
            }
            return queued;
        } catch (RuntimeException e) {
            log.warn("[Fuel] Could not queue the {} push: {}", eventType, e.toString());
            return 0;
        }
    }

    /** Sends what is due: new alerts and retries whose wait is over. Returns how many were attempted. */
    @Scheduled(fixedDelay = 30_000)
    public int dispatchDue() { return dispatchDue(Instant.now()); }

    int dispatchDue(Instant now) {
        if (!push.isEnabled()) return 0;
        List<Map<String, Object>> due;
        try {
            due = jdbc.queryForList("SELECT id, tenant_id, event_type, user_id, title, body, data_json, attempts, dedup_key FROM fuel_push_events "
                    + "WHERE status IN ('PENDING','FAILED') AND next_attempt_at <= ? ORDER BY next_attempt_at LIMIT 200", java.sql.Timestamp.from(now));
        } catch (RuntimeException e) { log.warn("[Fuel] Could not read due alerts: {}", e.toString()); return 0; }
        int tried = 0;
        for (Map<String, Object> r : due) {
            try {
                UUID id = (UUID) r.get("id");
                TenantId tenant = TenantId.of((UUID) r.get("tenant_id"));
                int attempts = ((Number) r.get("attempts")).intValue() + 1;
                var res = push.attempt(tenant, (UUID) r.get("user_id"), (String) r.get("title"), (String) r.get("body"), fromJson((String) r.get("data_json")));
                tried++;
                switch (res.outcome()) {
                    case SENT -> jdbc.update("UPDATE fuel_push_events SET status = 'SENT', attempts = ?, sent_at = ?, next_attempt_at = NULL, last_error = NULL WHERE id = ?", attempts, java.sql.Timestamp.from(now), id);
                    case NO_DEVICE -> jdbc.update("UPDATE fuel_push_events SET status = 'NO_DEVICE', attempts = ?, next_attempt_at = NULL WHERE id = ?", attempts, id);
                    case FAILED -> {
                        if (attempts >= MAX_ATTEMPTS) {
                            jdbc.update("UPDATE fuel_push_events SET status = 'GAVE_UP', attempts = ?, last_error = ?, next_attempt_at = NULL WHERE id = ?", attempts, cut(res.error(), 300), id);
                            audit.recordAs(tenant, "SYSTEM", null, "Fuel alerts", "FUEL_PUSH_GAVE_UP", "FuelPushEvent", id, null,
                                    Map.of("eventType", String.valueOf(r.get("event_type")), "dedupKey", String.valueOf(r.get("dedup_key")), "attempts", attempts));
                        } else {
                            jdbc.update("UPDATE fuel_push_events SET status = 'FAILED', attempts = ?, last_error = ?, next_attempt_at = ? WHERE id = ?", attempts, cut(res.error(), 300),
                                    java.sql.Timestamp.from(now.plus(BACKOFF[Math.min(attempts - 1, BACKOFF.length - 1)])), id);
                        }
                    }
                }
            } catch (RuntimeException e) { log.warn("[Fuel] Alert dispatch failed: {}", e.toString()); }
        }
        return tried;
    }

    // ---- a person's own switches ----

    @Transactional(readOnly = true)
    public Map<String, Boolean> preferences(TenantId tenant, UUID user) {
        Map<String, Boolean> out = new LinkedHashMap<>();
        EVENT_TYPES.stream().sorted().forEach(t -> out.put(t, true));
        jdbc.query("SELECT event_type, enabled FROM fuel_push_preferences WHERE tenant_id = ? AND user_id = ?",
                rs -> { if (out.containsKey(rs.getString(1))) out.put(rs.getString(1), rs.getBoolean(2)); }, tenant.getValue(), user);
        return out;
    }

    @Transactional
    public Map<String, Boolean> setPreferences(TenantId tenant, UUID user, Map<String, Boolean> wanted) {
        if (user == null) throw new IllegalStateException("You are not signed in as a user.");
        for (var e : wanted.entrySet()) {
            if (!EVENT_TYPES.contains(e.getKey())) throw new IllegalArgumentException("Unknown alert type: " + e.getKey());
            jdbc.update("INSERT INTO fuel_push_preferences (tenant_id, user_id, event_type, enabled) VALUES (?, ?, ?, ?) "
                    + "ON CONFLICT (tenant_id, user_id, event_type) DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = now()",
                    tenant.getValue(), user, e.getKey(), Boolean.TRUE.equals(e.getValue()));
        }
        return preferences(tenant, user);
    }

    /** The company's recent alerts with what became of each, newest first, for support. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> recent(TenantId tenant, int limit) {
        return jdbc.queryForList("SELECT id, event_type, dedup_key, user_id, status, attempts, last_error, created_at, sent_at FROM fuel_push_events "
                + "WHERE tenant_id = ? ORDER BY created_at DESC LIMIT ?", tenant.getValue(), Math.max(1, Math.min(limit, 200)));
    }

    private String json(Map<String, String> data) {
        try { return mapper.writeValueAsString(data == null ? Map.of() : data); } catch (JsonProcessingException e) { return "{}"; }
    }

    private Map<String, String> fromJson(String s) {
        try { return mapper.readValue(s, new TypeReference<Map<String, String>>() {}); } catch (Exception e) { return Map.of(); }
    }

    private static String cut(String s, int n) { return s == null ? null : s.length() <= n ? s : s.substring(0, n); }
}
