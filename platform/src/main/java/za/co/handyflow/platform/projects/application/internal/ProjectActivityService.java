package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.projects.dto.ActivityItem;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A project's recent activity, read from the histories each feature already keeps (status changes, change order submissions
 * and decisions, RFI events, document reviews, member changes, budget revisions, issues) plus project_activity for edits to
 * the project's own details and contract links. Newest first.
 */
@Service
@RequiredArgsConstructor
public class ProjectActivityService {

    static final int MAX = 200;

    private final JdbcTemplate jdbc;

    @Transactional
    public void record(UUID tenantId, UUID projectId, String kind, String summary, String detail, UUID actorId, String actorName) {
        jdbc.update("INSERT INTO project_activity (id, tenant_id, project_id, kind, summary, detail, actor_id, actor_name) VALUES (?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), tenantId, projectId, kind, summary.length() > 500 ? summary.substring(0, 497) + "…" : summary, detail, actorId, actorName);
    }

    static final String FEED = """
            SELECT * FROM (
              SELECT 'STATUS' AS kind, 'Status ' || COALESCE(h.from_status || ' → ', '') || h.to_status AS summary, h.reason AS detail,
                     h.changed_by_name AS actor, h.changed_at AS at, h.project_id AS ref
                FROM project_status_history h WHERE h.tenant_id = ? AND h.project_id = ?
              UNION ALL
              SELECT 'CHANGE_ORDER', c.change_number || ' submitted (revision ' || r.revision || '): ' || r.title, NULL, r.submitted_by_name, r.submitted_at, c.id
                FROM change_order_revisions r JOIN change_orders c ON c.id = r.change_order_id WHERE c.tenant_id = ? AND c.project_id = ?
              UNION ALL
              SELECT 'CHANGE_ORDER', c.change_number || ' ' || LOWER(r.outcome), r.outcome_reason, NULL, r.decided_at, c.id
                FROM change_order_revisions r JOIN change_orders c ON c.id = r.change_order_id
               WHERE c.tenant_id = ? AND c.project_id = ? AND r.decided_at IS NOT NULL
              UNION ALL
              SELECT 'RFI', f.rfi_number || ' ' || LOWER(REPLACE(e.event_type, '_', ' ')) || ': ' || f.title, LEFT(e.comment, 300), e.actor_name, e.occurred_at, f.id
                FROM project_rfi_events e JOIN project_rfis f ON f.id = e.rfi_id WHERE e.tenant_id = ? AND e.project_id = ?
              UNION ALL
              SELECT 'DOCUMENT', COALESCE(d.document_number || ' ', '') || d.title || ' ' || LOWER(v.action), LEFT(v.comment, 300), v.actor_name, v.occurred_at, d.id
                FROM project_document_reviews v JOIN project_documents d ON d.id = v.document_id WHERE v.tenant_id = ? AND v.project_id = ?
              UNION ALL
              SELECT 'MEMBER', COALESCE(m.user_name, 'A member') || CASE e.action WHEN 'ADDED' THEN ' added as ' || REPLACE(LOWER(e.new_role), '_', ' ')
                                                                                 WHEN 'REMOVED' THEN ' removed'
                                                                                 ELSE ' now ' || REPLACE(LOWER(e.new_role), '_', ' ') END, NULL, e.actor_name, e.occurred_at, e.user_id
                FROM project_member_events e LEFT JOIN project_members m ON m.project_id = e.project_id AND m.user_id = e.user_id
               WHERE e.tenant_id = ? AND e.project_id = ?
              UNION ALL
              SELECT 'BUDGET', l.category || ' — ' || l.description || ': ' || LOWER(REPLACE(b.revision_type, '_', ' ')) || ' to R ' || TO_CHAR(b.new_amount, 'FM999G999G999G990D00'),
                     b.reason, b.revised_by_name, b.revised_at, b.budget_line_id
                FROM project_budget_revisions b JOIN project_budget_lines l ON l.id = b.budget_line_id WHERE b.tenant_id = ? AND b.project_id = ?
              UNION ALL
              SELECT 'ISSUE', i.issue_number || ' raised: ' || i.title, NULL, i.raised_by_name, i.created_at, i.id
                FROM project_issues i WHERE i.tenant_id = ? AND i.project_id = ?
              UNION ALL
              SELECT 'ISSUE', i.issue_number || ' resolved', LEFT(i.resolution, 300), i.resolved_by_name, i.resolved_at, i.id
                FROM project_issues i WHERE i.tenant_id = ? AND i.project_id = ? AND i.resolved_at IS NOT NULL
              UNION ALL
              SELECT CASE WHEN a.kind = 'DETAILS_EDITED' THEN 'DETAILS' WHEN a.kind LIKE 'CLIENT%' THEN 'CLIENT' ELSE 'CONTRACT' END, a.summary, a.detail, a.actor_name, a.occurred_at, a.id
                FROM project_activity a WHERE a.tenant_id = ? AND a.project_id = ?
            ) x
            WHERE x.at IS NOT NULL AND (?::timestamptz IS NULL OR x.at < ?::timestamptz)
            ORDER BY x.at DESC
            LIMIT ?
            """;

    @Transactional(readOnly = true)
    public List<ActivityItem> feed(UUID tenantId, UUID projectId, Instant before, int limit) {
        int n = Math.max(1, Math.min(limit, MAX));
        Timestamp b = before == null ? null : Timestamp.from(before);
        Object[] args = new Object[24];
        for (int i = 0; i < 20; i += 2) { args[i] = tenantId; args[i + 1] = projectId; }
        args[20] = b; args[21] = b; args[22] = n;
        return jdbc.query(FEED, (rs, i) -> new ActivityItem(rs.getString("kind"), rs.getString("summary"), rs.getString("detail"),
                rs.getString("actor"), rs.getTimestamp("at").toInstant(), (UUID) rs.getObject("ref")), java.util.Arrays.copyOf(args, 23));
    }
}
