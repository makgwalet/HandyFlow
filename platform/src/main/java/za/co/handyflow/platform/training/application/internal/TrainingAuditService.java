package za.co.handyflow.platform.training.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.training.domain.model.TrainingAuditRules;
import za.co.handyflow.platform.training.dto.TrainingAuditDtos.*;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The audit trail. Writing is its own transaction and never throws: the change it records has already been made, and an audit table that is
 * unavailable must be noticed (it is logged as an error) without undoing or failing the person's work.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingAuditService {

    private static final int PAGE = 100;
    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(TenantId tenantId, UUID actorId, String actorName, String action, String entityType, UUID entityId, String detail) {
        try {
            jdbc.update("INSERT INTO training_audit_log (id, tenant_id, actor_id, actor_name, action, entity_type, entity_id, detail) VALUES (?,?,?,?,?,?,?,?)",
                    UUID.randomUUID(), tenantId.getValue(), actorId, actorName == null ? null : (actorName.length() > 200 ? actorName.substring(0, 200) : actorName),
                    action, entityType, entityId, TrainingAuditRules.detail(detail));
        } catch (RuntimeException e) {
            log.error("TRAINING AUDIT WRITE FAILED action={} tenant={} entity={}", action, tenantId.getValue(), entityId, e);
        }
    }

    @Transactional(readOnly = true)
    public AuditPage page(TenantId tenantId, LocalDate from, LocalDate to, String action, UUID entityId, int page) {
        check(from, to, action);
        List<AuditEntry> rows = query(tenantId, from, to, action, entityId, Math.max(0, page) * PAGE, PAGE + 1);
        boolean more = rows.size() > PAGE;
        return new AuditPage(more ? rows.subList(0, PAGE) : rows, more);
    }

    /** Everything in the range, newest first, for export. Capped so one export cannot read the whole table. */
    @Transactional(readOnly = true)
    public List<AuditEntry> forExport(TenantId tenantId, LocalDate from, LocalDate to, String action, UUID entityId) {
        check(from, to, action);
        return query(tenantId, from, to, action, entityId, 0, 20_000);
    }

    private static void check(LocalDate from, LocalDate to, String action) {
        String range = TrainingAuditRules.rangeProblem(from, to, LocalDate.now(TrainingAuditRules.ZONE));
        if (range != null) throw new HandyFlowException(range, HttpStatus.BAD_REQUEST, "INVALID_RANGE");
        String filter = TrainingAuditRules.filterProblem(action);
        if (filter != null) throw new HandyFlowException(filter, HttpStatus.BAD_REQUEST, "INVALID_FILTER");
    }

    private List<AuditEntry> query(TenantId tenantId, LocalDate fromIn, LocalDate toIn, String action, UUID entityId, int offset, int limit) {
        LocalDate today = LocalDate.now(TrainingAuditRules.ZONE);
        LocalDate to = TrainingAuditRules.toOrDefault(toIn, today);
        LocalDate from = TrainingAuditRules.fromOrDefault(fromIn, toIn, today);
        String act = action == null || action.isBlank() ? null : action.trim().toUpperCase();
        List<Object> args = new ArrayList<>(List.of(tenantId.getValue(), Timestamp.from(TrainingAuditRules.start(from)), Timestamp.from(TrainingAuditRules.endExclusive(to))));
        StringBuilder sql = new StringBuilder("SELECT id, occurred_at, actor_name, action, entity_type, entity_id, detail FROM training_audit_log "
                + "WHERE tenant_id = ? AND occurred_at >= ? AND occurred_at < ?");
        if (act != null) { sql.append(" AND action = ?"); args.add(act); }
        if (entityId != null) { sql.append(" AND entity_id = ?"); args.add(entityId); }
        sql.append(" ORDER BY occurred_at DESC, id LIMIT ? OFFSET ?");
        args.add(limit); args.add(offset);
        return jdbc.query(sql.toString(), (rs, i) -> new AuditEntry(rs.getObject("id", UUID.class), rs.getTimestamp("occurred_at").toInstant(), rs.getString("actor_name"),
                rs.getString("action"), rs.getString("entity_type"), rs.getObject("entity_id", UUID.class), rs.getString("detail")), args.toArray());
    }
}
