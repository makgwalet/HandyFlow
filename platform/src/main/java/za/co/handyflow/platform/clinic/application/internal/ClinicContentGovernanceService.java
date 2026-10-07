package za.co.handyflow.platform.clinic.application.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.domain.question.ContentGovernance;
import za.co.handyflow.platform.clinic.domain.question.ContentStatus;
import za.co.handyflow.platform.clinic.domain.question.QuestionRuleEngine;
import za.co.handyflow.platform.clinic.dto.QuestionLibraryDtos.GroupSummary;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Review workflow for clinical configuration (DEC-CLINIC-001). Reviewing, approving and activating are
 * separate permissions held by separate people; every move is written to clinic_content_audit.
 * Only a tenant's own groups can be moved here; platform-provided content (tenant_id NULL) is managed by releases.
 */
@Service
public class ClinicContentGovernanceService {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public ClinicContentGovernanceService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    private record GroupState(UUID id, UUID tenantId, String code, ContentStatus status, UUID reviewer,
                              boolean demo, boolean hasSource) {}

    @Transactional(readOnly = true)
    public List<GroupSummary> list(TenantId tenantId) {
        return jdbc.query("""
            SELECT id, code, version, name, category, status, is_demo, clinical_source, source_version, reviewed_by, approved_by
            FROM clinic_question_group WHERE tenant_id = ? OR tenant_id IS NULL
            ORDER BY code, version DESC""",
                (rs, i) -> new GroupSummary((UUID) rs.getObject(1), rs.getString(2), rs.getInt(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getBoolean(7), rs.getString(8), rs.getString(9),
                        (UUID) rs.getObject(10), (UUID) rs.getObject(11)), tenantId.getValue());
    }

    @Transactional
    public void changeStatus(TenantId tenantId, UUID groupId, String requested, UUID actor, String note) {
        ContentStatus next;
        try {
            next = ContentStatus.valueOf(requested == null ? "" : requested.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown status '" + requested + "'.");
        }
        GroupState g = load(tenantId, groupId);
        List<String> problems = (next == ContentStatus.CLINICAL_REVIEW || next == ContentStatus.ACTIVE)
                ? expressionProblems(groupId) : List.of();
        ContentGovernance.checkTransition(g.status(), next, g.reviewer(), actor, g.demo(), problems, g.hasSource());

        if (g.status() == ContentStatus.CLINICAL_REVIEW
                && (next == ContentStatus.APPROVED || next == ContentStatus.CHANGES_REQUESTED)) {
            jdbc.update("UPDATE clinic_question_group SET status = ?, reviewed_by = ?, reviewed_at = NOW(), updated_at = NOW() WHERE id = ?",
                    next.name(), actor, groupId);
        } else if (next == ContentStatus.ACTIVE) {
            jdbc.update("""
                UPDATE clinic_question_group SET status = 'ACTIVE', approved_by = ?, approved_at = NOW(),
                       effective_from = COALESCE(effective_from, CURRENT_DATE), updated_at = NOW() WHERE id = ?""", actor, groupId);
            // a newer version replaces the older one
            jdbc.update("""
                UPDATE clinic_question_group SET status = 'DEPRECATED', updated_at = NOW()
                WHERE tenant_id = ? AND code = ? AND status = 'ACTIVE' AND id <> ?""", g.tenantId(), g.code(), groupId);
        } else {
            jdbc.update("UPDATE clinic_question_group SET status = ?, updated_at = NOW() WHERE id = ?", next.name(), groupId);
        }
        jdbc.update("""
            INSERT INTO clinic_content_audit (id, tenant_id, group_id, from_status, to_status, actor_user_id, note)
            VALUES (?,?,?,?,?,?,?)""", UUID.randomUUID(), tenantId.getValue(), groupId, g.status().name(), next.name(), actor,
                note == null || note.isBlank() ? null : note.trim());
    }

    private GroupState load(TenantId tenantId, UUID groupId) {
        List<GroupState> rows = jdbc.query("""
            SELECT id, tenant_id, code, status, reviewed_by, is_demo,
                   (clinical_source IS NOT NULL AND btrim(clinical_source) <> '') AS has_source
            FROM clinic_question_group WHERE id = ? AND tenant_id = ?""",
                (rs, i) -> new GroupState((UUID) rs.getObject(1), (UUID) rs.getObject(2), rs.getString(3),
                        ContentStatus.valueOf(rs.getString(4)), (UUID) rs.getObject(5), rs.getBoolean(6), rs.getBoolean(7)),
                groupId, tenantId.getValue());
        if (rows.isEmpty()) throw new ResourceNotFoundException("Question group", groupId.toString());
        return rows.get(0);
    }

    private List<String> expressionProblems(UUID groupId) {
        List<String> problems = new ArrayList<>();
        jdbc.query("""
            SELECT q.code, r.expression::text FROM clinic_question_rule r JOIN clinic_question q ON q.id = r.question_id
            WHERE q.group_id = ?""", rs -> { check(problems, rs.getString(1), rs.getString(2)); }, groupId);
        jdbc.query("SELECT code, expression::text FROM clinic_red_flag_rule WHERE group_id = ?",
                rs -> { check(problems, "flag " + rs.getString(1), rs.getString(2)); }, groupId);
        return problems;
    }

    private void check(List<String> problems, String where, String expression) {
        try {
            QuestionRuleEngine.validateExpression(json.readValue(expression, MAP));
        } catch (IllegalArgumentException | JsonProcessingException e) {
            problems.add(where + ": " + e.getMessage());
        }
    }
}
