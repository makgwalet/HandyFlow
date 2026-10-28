package za.co.handyflow.platform.audittrail.application.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditTrailService implements AuditTrailFacade {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public record Event(UUID id, String actorType, UUID actorId, String actorName, String action, String itemType,
                        UUID itemId, String beforeJson, String afterJson, java.time.Instant at) {}

    @Override
    @Transactional
    public void record(TenantId tenantId, String action, String itemType, UUID itemId, Map<String, ?> before, Map<String, ?> after) {
        UUID actor = null;
        try { actor = TenantContext.getCurrentUserId(); } catch (RuntimeException ignored) { /* no staff user in context */ }
        recordAs(tenantId, STAFF, actor, actor == null ? null : TenantContext.getCurrentUserName(), action, itemType, itemId, before, after);
    }

    @Override
    @Transactional
    public void recordAs(TenantId tenantId, String actorType, UUID actorId, String actorName,
                         String action, String itemType, UUID itemId, Map<String, ?> before, Map<String, ?> after) {
        jdbc.update("""
                INSERT INTO audit_events (tenant_id, actor_type, actor_id, actor_name, action, item_type, item_id, before_json, after_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)
                """, tenantId.getValue(), actorType, actorId, actorName, action, itemType, itemId, json(before), json(after));
    }

    @Transactional(readOnly = true)
    public List<Event> list(TenantId tenantId, String itemType, UUID itemId, int limit) {
        int cap = Math.max(1, Math.min(limit, 500));
        return jdbc.query("""
                SELECT id, actor_type, actor_id, actor_name, action, item_type, item_id, before_json::text, after_json::text, occurred_at
                FROM audit_events
                WHERE tenant_id = ? AND (?::text IS NULL OR item_type = ?) AND (?::uuid IS NULL OR item_id = ?)
                ORDER BY occurred_at DESC, id LIMIT ?
                """, (rs, i) -> new Event((UUID) rs.getObject(1), rs.getString(2), (UUID) rs.getObject(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), (UUID) rs.getObject(7), rs.getString(8), rs.getString(9),
                        rs.getTimestamp(10).toInstant()),
                tenantId.getValue(), itemType, itemType, itemId, itemId, cap);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Activity> recent(TenantId tenantId, List<String> actionPatterns, int limit) {
        if (actionPatterns == null || actionPatterns.isEmpty()) return List.of();
        int cap = Math.max(1, Math.min(limit, 100));
        return jdbc.query("""
                SELECT actor_name, action, item_type, item_id, occurred_at
                FROM audit_events
                WHERE tenant_id = ? AND action LIKE ANY (?)
                ORDER BY occurred_at DESC, id LIMIT ?
                """, (rs, i) -> new Activity(rs.getString(1), rs.getString(2), rs.getString(3), (UUID) rs.getObject(4), rs.getTimestamp(5).toInstant()),
                tenantId.getValue(), actionPatterns.toArray(new String[0]), cap);
    }

    private String json(Map<String, ?> m) {
        if (m == null) return null;
        try { return mapper.writeValueAsString(m); } catch (JsonProcessingException e) { throw new IllegalStateException("Could not record audit event", e); }
    }
}
