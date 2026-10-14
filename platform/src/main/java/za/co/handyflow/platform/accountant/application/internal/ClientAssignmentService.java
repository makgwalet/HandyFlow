package za.co.handyflow.platform.accountant.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accountant.dto.ClientAssignmentResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Who works on which practice client (finance patch F15). */
@Service
@RequiredArgsConstructor
public class ClientAssignmentService {

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<ClientAssignmentResponse> forClient(TenantId tenantId, UUID clientId) {
        return jdbc.query("""
                SELECT a.id, a.client_id, a.user_id, u.first_name || ' ' || u.last_name, a.role, a.assigned_at
                  FROM acc_client_assignments a LEFT JOIN users u ON u.id = a.user_id
                 WHERE a.tenant_id = ? AND a.client_id = ? ORDER BY a.role, u.first_name""",
                (rs, n) -> new ClientAssignmentResponse(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5), rs.getTimestamp(6).toInstant()),
                tenantId.getValue(), clientId);
    }

    @Transactional
    public ClientAssignmentResponse assign(TenantId tenantId, UUID clientId, UUID userId, String role, UUID by) {
        requireClient(tenantId, clientId);
        List<String> who = jdbc.queryForList("SELECT first_name || ' ' || last_name FROM users WHERE tenant_id = ? AND id = ? AND status = 'ACTIVE'",
                String.class, tenantId.getValue(), userId);
        if (who.isEmpty()) throw new ResourceNotFoundException("User", String.valueOf(userId));
        Long already = jdbc.queryForObject("SELECT count(*) FROM acc_client_assignments WHERE client_id = ? AND user_id = ? AND role = ?",
                Long.class, clientId, userId, role);
        String problem = ClientAccessRules.assignProblem(role, already != null && already > 0);
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "ASSIGN_REFUSED");
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO acc_client_assignments (id, tenant_id, client_id, user_id, role, assigned_by) VALUES (?, ?, ?, ?, ?, ?)",
                id, tenantId.getValue(), clientId, userId, role, by);
        return forClient(tenantId, clientId).stream().filter(a -> a.id().equals(id)).findFirst().orElseThrow();
    }

    /** Auto-assigns the person who registered a client as its preparer, so they do not lose sight of it. */
    @Transactional
    public void assignCreator(TenantId tenantId, UUID clientId, UUID userId) {
        if (userId == null) return;
        jdbc.update("""
                INSERT INTO acc_client_assignments (tenant_id, client_id, user_id, role, assigned_by)
                VALUES (?, ?, ?, 'PREPARER', ?) ON CONFLICT (client_id, user_id, role) DO NOTHING""",
                tenantId.getValue(), clientId, userId, userId);
    }

    @Transactional
    public void unassign(TenantId tenantId, UUID clientId, UUID assignmentId) {
        int n = jdbc.update("DELETE FROM acc_client_assignments WHERE tenant_id = ? AND client_id = ? AND id = ?",
                tenantId.getValue(), clientId, assignmentId);
        if (n == 0) throw new ResourceNotFoundException("Assignment", assignmentId.toString());
    }

    private void requireClient(TenantId tenantId, UUID clientId) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM acc_clients WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL",
                Long.class, tenantId.getValue(), clientId);
        if (n == null || n == 0) throw new ResourceNotFoundException("Client", clientId.toString());
    }
}
