// security/application/internal/GuardClientLedger.java
package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

/**
 * Remembers which client-generated ids have been accepted, so a retry returns the first result. Keyed by tenant, kind and
 * id. An id first used by a different guard is refused, never replayed to someone else.
 */
@Component
@RequiredArgsConstructor
public class GuardClientLedger {

    public enum Kind { SCAN, INCIDENT, DURESS, EVIDENCE, GATE_ENTRY, CHECKOUT }

    private final JdbcTemplate jdbc;

    /** The id of what this request created the first time, if it was seen. */
    public Optional<UUID> find(TenantId tenantId, Kind kind, UUID clientRef, UUID guardId) {
        var rows = jdbc.query(
                "SELECT result_id, guard_id FROM security_client_requests WHERE tenant_id = ? AND kind = ? AND client_ref = ?",
                (rs, i) -> new UUID[]{rs.getObject("result_id", UUID.class), rs.getObject("guard_id", UUID.class)},
                tenantId.getValue(), kind.name(), clientRef);
        if (rows.isEmpty()) return Optional.empty();
        if (!rows.get(0)[1].equals(guardId)) {
            throw new HandyFlowException("This request id was already used. Create a new one.", HttpStatus.CONFLICT, "CLIENT_REF_IN_USE");
        }
        return Optional.of(rows.get(0)[0]);
    }

    public void record(TenantId tenantId, UUID guardId, Kind kind, UUID clientRef, UUID resultId) {
        jdbc.update("INSERT INTO security_client_requests (tenant_id, guard_id, kind, client_ref, result_id) VALUES (?, ?, ?, ?, ?)",
                tenantId.getValue(), guardId, kind.name(), clientRef, resultId);
    }
}
