package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.*;

/**
 * A queued write the server refused. Recorded in its own transaction, because the refused write rolls its own back. It holds
 * no stock and no money: it is the supervisor's list of what the phone tried and why the server said no.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelOfflineConflictService {
    public static final String SHIFT_OPEN = "SHIFT_OPEN", SHIFT_CLOSE = "SHIFT_CLOSE", STAY_BOOK = "STAY_BOOK", STAY_CHECK_IN = "STAY_CHECK_IN", STAY_CHECK_OUT = "STAY_CHECK_OUT";

    private final JdbcTemplate jdbc;
    private final AuditTrailFacade audit;

    public record Conflict(UUID id, String kind, UUID clientRequestId, UUID entityId, UUID userId, String reason, Instant capturedAt, Instant createdAt, String status) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(TenantId tenant, String kind, UUID requestId, UUID entityId, UUID userId, String reason, Instant capturedAt) {
        String why = reason == null ? "Refused." : reason.length() > 500 ? reason.substring(0, 500) : reason;
        // The same request refused twice is one conflict: a phone that retries must not fill the list.
        int n = jdbc.update("INSERT INTO fuel_offline_conflicts (id, tenant_id, kind, client_request_id, entity_id, user_id, reason, captured_at) VALUES (?,?,?,?,?,?,?,?) "
                        + "ON CONFLICT (tenant_id, kind, client_request_id) DO NOTHING",
                UUID.randomUUID(), tenant.getValue(), kind, requestId, entityId, userId, why, capturedAt == null ? null : java.sql.Timestamp.from(capturedAt));
        if (n > 0) {
            audit.record(tenant, "FUEL_OFFLINE_CONFLICT", "FuelOfflineConflict", requestId, Map.of(),
                    Map.of("kind", kind, "reason", why, "userId", String.valueOf(userId), "capturedAt", String.valueOf(capturedAt)));
            log.info("Offline write refused kind={} request={} reason={}", kind, requestId, why);
        }
    }

    @Transactional(readOnly = true)
    public List<Conflict> open(TenantId tenant, int limit) {
        return jdbc.query("SELECT id, kind, client_request_id, entity_id, user_id, reason, captured_at, created_at, status FROM fuel_offline_conflicts "
                        + "WHERE tenant_id = ? AND status = 'OPEN' ORDER BY created_at DESC LIMIT ?",
                (rs, i) -> new Conflict(rs.getObject("id", UUID.class), rs.getString("kind"), rs.getObject("client_request_id", UUID.class), rs.getObject("entity_id", UUID.class),
                        rs.getObject("user_id", UUID.class), rs.getString("reason"),
                        rs.getTimestamp("captured_at") == null ? null : rs.getTimestamp("captured_at").toInstant(), rs.getTimestamp("created_at").toInstant(), rs.getString("status")),
                tenant.getValue(), Math.max(1, Math.min(limit, 200)));
    }

    @Transactional
    public boolean resolve(TenantId tenant, UUID id, UUID by, String note) {
        int n = jdbc.update("UPDATE fuel_offline_conflicts SET status = 'RESOLVED', resolved_by = ?, resolved_at = now(), resolution_note = ? WHERE tenant_id = ? AND id = ? AND status = 'OPEN'",
                by, note == null ? null : (note.length() > 300 ? note.substring(0, 300) : note), tenant.getValue(), id);
        if (n > 0) audit.record(tenant, "FUEL_OFFLINE_CONFLICT_RESOLVED", "FuelOfflineConflict", id, Map.of("status", "OPEN"), Map.of("status", "RESOLVED", "by", String.valueOf(by)));
        return n > 0;
    }
}
