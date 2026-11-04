package za.co.handyflow.platform.identity.application.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.identity.dto.response.AccessAuditEventResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * IAM Phase 0.3: append-only record of who changed whose access. Written in the caller's
 * transaction, so an access change and its audit event commit or roll back together.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccessAuditService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbc;

    /** Records an event attributed to the current request's user. */
    @Transactional
    public void record(TenantId tenantId, String action, String targetType, UUID targetId,
                       String targetLabel, Map<String, ?> before, Map<String, ?> after) {
        UUID actorId = null;
        try { actorId = TenantContext.getCurrentUserId(); } catch (RuntimeException ignored) { /* support session or system */ }
        String actorName = null;
        try { actorName = TenantContext.getCurrentUserName(); } catch (RuntimeException ignored) { }
        recordAs(tenantId, actorId, actorName, action, targetType, targetId, targetLabel, before, after);
    }

    /** Records an event with an explicit actor (for public flows such as accepting an invitation). */
    @Transactional
    public void recordAs(TenantId tenantId, UUID actorId, String actorName, String action, String targetType,
                         UUID targetId, String targetLabel, Map<String, ?> before, Map<String, ?> after) {
        jdbc.update("""
                INSERT INTO access_audit_events
                  (tenant_id, actor_id, actor_name, action, target_type, target_id, target_label, before_json, after_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb))
                """,
                tenantId.getValue(), actorId, actorName, action, targetType, targetId, targetLabel,
                json(before), json(after));
    }

    @Transactional(readOnly = true)
    public List<AccessAuditEventResponse> recent(TenantId tenantId, int limit) {
        return jdbc.query("""
                SELECT id, actor_id, actor_name, action, target_type, target_id, target_label,
                       before_json::text AS before_json, after_json::text AS after_json, occurred_at
                FROM access_audit_events WHERE tenant_id = ? ORDER BY occurred_at DESC LIMIT ?
                """,
                (rs, i) -> new AccessAuditEventResponse(
                        rs.getObject("id", UUID.class), rs.getObject("actor_id", UUID.class),
                        rs.getString("actor_name"), rs.getString("action"), rs.getString("target_type"),
                        rs.getObject("target_id", UUID.class), rs.getString("target_label"),
                        rs.getString("before_json"), rs.getString("after_json"),
                        rs.getObject("occurred_at", java.time.OffsetDateTime.class).toInstant()),
                tenantId.getValue(), Math.min(Math.max(limit, 1), 500));
    }

    private static String json(Map<String, ?> m) {
        if (m == null) return null;
        try { return MAPPER.writeValueAsString(m); }
        catch (JsonProcessingException e) { return "{\"error\":\"unserialisable\"}"; }
    }
}
