package za.co.handyflow.platform.training.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.training.domain.model.TrainingCostRules;
import za.co.handyflow.platform.training.dto.TrainingCostDtos.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** What a session cost. Every query is filtered to the company. */
@Service
@RequiredArgsConstructor
public class TrainingCostService {

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public SessionCosts costs(TenantId tenantId, UUID sessionId) {
        requireSession(tenantId, sessionId);
        List<Cost> rows = jdbc.query("SELECT id, category, amount, incurred_on, note FROM training_session_costs WHERE session_id = ? AND tenant_id = ? ORDER BY incurred_on DESC, created_at DESC",
                (rs, i) -> new Cost(rs.getObject("id", UUID.class), rs.getString("category"), rs.getBigDecimal("amount"), rs.getDate("incurred_on").toLocalDate(), rs.getString("note")),
                sessionId, tenantId.getValue());
        BigDecimal total = rows.stream().map(Cost::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        long people = jdbc.queryForObject("SELECT COUNT(*) FROM training_enrollments WHERE session_id = ? AND tenant_id = ? AND status IN ('ATTENDED', 'COMPLETED', 'FAILED')",
                Long.class, sessionId, tenantId.getValue());
        return new SessionCosts(sessionId, total, people, TrainingCostRules.costPerPerson(total, people), rows);
    }

    @Transactional
    public SessionCosts add(TenantId tenantId, UUID sessionId, CostRequest r, UUID by, LocalDate today) {
        String problem = TrainingCostRules.costProblem(r.category(), r.amount(), r.incurredOn(), r.note(), today);
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "INVALID_COST");
        requireSession(tenantId, sessionId);
        jdbc.update("INSERT INTO training_session_costs (id, tenant_id, session_id, category, amount, incurred_on, note, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), tenantId.getValue(), sessionId, r.category(), r.amount(), java.sql.Date.valueOf(r.incurredOn()),
                r.note() == null || r.note().isBlank() ? null : r.note().trim(), by);
        return costs(tenantId, sessionId);
    }

    @Transactional
    public SessionCosts remove(TenantId tenantId, UUID sessionId, UUID costId) {
        requireSession(tenantId, sessionId);
        int n = jdbc.update("DELETE FROM training_session_costs WHERE id = ? AND session_id = ? AND tenant_id = ?", costId, sessionId, tenantId.getValue());
        if (n == 0) throw new ResourceNotFoundException("Cost", costId.toString());
        return costs(tenantId, sessionId);
    }

    private void requireSession(TenantId tenantId, UUID sessionId) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM training_sessions WHERE id = ? AND tenant_id = ?", Long.class, sessionId, tenantId.getValue());
        if (n == null || n == 0) throw new ResourceNotFoundException("TrainingSession", sessionId.toString());
    }
}
