package za.co.handyflow.platform.warehousing.application.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The transactional outbox. {@link #record} is called in the same transaction as the change it reports, so the event exists exactly when the change
 * does: a rollback takes the event with it, and a crash after commit loses nothing (delivery is done later by {@link WhseWebhookDispatcher}).
 * One pending delivery is created for each active webhook that listens for the event.
 */
@Component
@RequiredArgsConstructor
public class WhseOutbox {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    /** Writes the event and fans it out. Returns the event id. Must run inside the caller's transaction. */
    public UUID record(TenantId tenantId, String eventType, String aggregateType, UUID aggregateId, Map<String, Object> data) {
        UUID t = tenantId.getValue();
        UUID id = UUID.randomUUID();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", id);
        envelope.put("type", eventType);
        envelope.put("createdAt", Instant.now().toString());
        envelope.put("data", data);
        String json;
        try {
            json = mapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not write the " + eventType + " event", e);
        }
        jdbc.update("INSERT INTO whse_outbox_events (id, tenant_id, event_type, aggregate_type, aggregate_id, payload) VALUES (?,?,?,?,?,?::jsonb)", id, t, eventType, aggregateType, aggregateId, json);
        jdbc.update("""
                INSERT INTO whse_webhook_deliveries (id, tenant_id, webhook_id, event_id)
                SELECT gen_random_uuid(), w.tenant_id, w.id, ? FROM whse_webhooks w
                 WHERE w.tenant_id = ? AND w.active = TRUE AND w.suspended = FALSE AND w.deleted_at IS NULL
                   AND (w.events = '*' OR ',' || w.events || ',' LIKE '%,' || ? || ',%')""", id, t, eventType);
        return id;
    }

    /** SKU of each item, for payloads that list lines. Unknown ids are left out. */
    public Map<UUID, String> skus(TenantId tenantId, Collection<UUID> itemIds) {
        Map<UUID, String> out = new HashMap<>();
        if (itemIds.isEmpty()) return out;
        String marks = String.join(",", java.util.Collections.nCopies(itemIds.size(), "?"));
        java.util.List<Object> args = new java.util.ArrayList<>();
        args.add(tenantId.getValue());
        args.addAll(itemIds);
        jdbc.query("SELECT id, sku FROM whse_items WHERE tenant_id = ? AND id IN (" + marks + ")", rs -> { out.put(rs.getObject(1, UUID.class), rs.getString(2)); }, args.toArray());
        return out;
    }
}
