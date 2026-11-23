// security/application/internal/SensitiveReadAuditService.java
package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Writes and reads the access trail of sensitive material. Rows go into the existing append-only security audit log. */
@Service
@RequiredArgsConstructor
public class SensitiveReadAuditService {

    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 200;

    public record Entry(UUID id, Instant occurredAt, UUID actorId, String actorType, String entityType, UUID entityId, String action, String metadata) {}

    private final JdbcTemplate jdbc;

    /** Own transaction: the read has already been served, and a failure here must not disturb anything else. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID tenant, UUID actor, SensitiveReadRules.Target t, boolean supportSession) {
        jdbc.update("""
                INSERT INTO security_audit_log (id, tenant_id, actor_id, actor_type, entity_type, entity_id, action, metadata, occurred_at)
                VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?::jsonb, now())""",
                tenant, actor, actor == null ? "SYSTEM" : "USER", t.entityType(), t.entityId(), t.action(),
                SensitiveReadRules.json(t.metadata(), supportSession));
    }

    static String placeholders(int n) { return String.join(",", java.util.Collections.nCopies(n, "?")); }

    static int clamp(Integer limit) {
        if (limit == null || limit <= 0) return DEFAULT_LIMIT;
        return Math.min(limit, MAX_LIMIT);
    }

    /** Newest first. Optionally narrowed to one record (entityId) and/or one kind of record. Always within the tenant. */
    @Transactional(readOnly = true)
    public List<Entry> recent(TenantId tenantId, String entityType, UUID entityId, Integer limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, occurred_at, actor_id, actor_type, entity_type, entity_id, action, metadata::text AS metadata
                FROM security_audit_log WHERE tenant_id = ? AND action IN (""" + placeholders(SensitiveReadRules.LOGGED_ACTIONS.size()) + ")");
        java.util.List<Object> args = new java.util.ArrayList<>();
        args.add(tenantId.getValue());
        args.addAll(SensitiveReadRules.LOGGED_ACTIONS);
        if (entityType != null && !entityType.isBlank()) { sql.append(" AND entity_type = ?"); args.add(entityType.trim().toUpperCase(java.util.Locale.ROOT)); }
        if (entityId != null) { sql.append(" AND entity_id = ?"); args.add(entityId); }
        sql.append(" ORDER BY occurred_at DESC, id LIMIT ?");
        args.add(clamp(limit));
        return jdbc.query(sql.toString(), (rs, i) -> new Entry((UUID) rs.getObject("id"), rs.getTimestamp("occurred_at").toInstant(),
                (UUID) rs.getObject("actor_id"), rs.getString("actor_type"), rs.getString("entity_type"), (UUID) rs.getObject("entity_id"),
                rs.getString("action"), rs.getString("metadata")), args.toArray());
    }
}
