package za.co.handyflow.platform.accountant.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accountant.application.internal.EngagementLetterRules.Draft;
import za.co.handyflow.platform.accountant.dto.PracticeServiceDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Engagement letters per client: draft, send (text is frozen), record acceptance, decline, end (finance F22). */
@Service
@RequiredArgsConstructor
public class EngagementLetterService {

    private final JdbcTemplate jdbc;
    private final ClientAccessService access;

    private static HandyFlowException bad(String m) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, "LETTER_REFUSED"); }
    private static HandyFlowException missing() { return new HandyFlowException("Letter not found", HttpStatus.NOT_FOUND, "NOT_FOUND"); }

    private static final String COLS = """
            id, client_id, version, title, scope, exclusions, fee_basis, fee_amount, fee_notes, start_date, end_date, status,
            sent_at, accepted_by_name, accepted_method, accepted_on, decline_reason""";

    private static LetterResponse map(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new LetterResponse(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getInt(3), rs.getString(4), rs.getString(5),
                rs.getString(6), rs.getString(7), rs.getBigDecimal(8), rs.getString(9), rs.getDate(10).toLocalDate(),
                rs.getDate(11) == null ? null : rs.getDate(11).toLocalDate(), rs.getString(12),
                rs.getTimestamp(13) == null ? null : rs.getTimestamp(13).toInstant(), rs.getString(14), rs.getString(15),
                rs.getDate(16) == null ? null : rs.getDate(16).toLocalDate(), rs.getString(17));
    }

    private static Draft draft(LetterRequest r) {
        return new Draft(r.title(), r.scope(), r.exclusions(), r.feeBasis(), r.feeAmount(), r.feeNotes(), r.startDate(), r.endDate());
    }

    @Transactional(readOnly = true)
    public List<LetterResponse> list(TenantId tenantId, UUID clientId) {
        access.require(tenantId, clientId);
        return jdbc.query("SELECT " + COLS + " FROM acc_engagement_letters WHERE tenant_id = ? AND client_id = ? ORDER BY version DESC",
                EngagementLetterService::map, tenantId.getValue(), clientId);
    }

    private LetterResponse load(TenantId tenantId, UUID id) {
        List<LetterResponse> r = jdbc.query("SELECT " + COLS + " FROM acc_engagement_letters WHERE tenant_id = ? AND id = ?",
                EngagementLetterService::map, tenantId.getValue(), id);
        if (r.isEmpty()) throw missing();
        access.require(tenantId, r.get(0).clientId());
        return r.get(0);
    }

    @Transactional
    public LetterResponse create(TenantId tenantId, UUID clientId, LetterRequest req, UUID by) {
        access.require(tenantId, clientId);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM acc_clients WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL", Integer.class, clientId, tenantId.getValue()) == 0)
            throw new HandyFlowException("Client not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
        String problem = EngagementLetterRules.draftProblem(draft(req));
        if (problem != null) throw bad(problem);
        Integer max = jdbc.queryForObject("SELECT COALESCE(MAX(version), 0) FROM acc_engagement_letters WHERE client_id = ?", Integer.class, clientId);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO acc_engagement_letters (id, tenant_id, client_id, version, title, scope, exclusions, fee_basis, fee_amount, fee_notes, start_date, end_date, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                id, tenantId.getValue(), clientId, max + 1, req.title().trim(), req.scope().trim(), blank(req.exclusions()), req.feeBasis(),
                req.feeAmount(), blank(req.feeNotes()), Date.valueOf(req.startDate()), req.endDate() == null ? null : Date.valueOf(req.endDate()), by);
        return load(tenantId, id);
    }

    @Transactional
    public LetterResponse update(TenantId tenantId, UUID id, LetterRequest req) {
        LetterResponse cur = load(tenantId, id);
        String problem = EngagementLetterRules.editProblem(cur.status());
        if (problem == null) problem = EngagementLetterRules.draftProblem(draft(req));
        if (problem != null) throw bad(problem);
        jdbc.update("""
                UPDATE acc_engagement_letters SET title = ?, scope = ?, exclusions = ?, fee_basis = ?, fee_amount = ?, fee_notes = ?, start_date = ?, end_date = ?
                 WHERE id = ? AND tenant_id = ? AND status = 'DRAFT'""",
                req.title().trim(), req.scope().trim(), blank(req.exclusions()), req.feeBasis(), req.feeAmount(), blank(req.feeNotes()),
                Date.valueOf(req.startDate()), req.endDate() == null ? null : Date.valueOf(req.endDate()), id, tenantId.getValue());
        return load(tenantId, id);
    }

    @Transactional
    public LetterResponse send(TenantId tenantId, UUID id, UUID by) {
        LetterResponse cur = load(tenantId, id);
        Draft d = new Draft(cur.title(), cur.scope(), cur.exclusions(), cur.feeBasis(), cur.feeAmount(), cur.feeNotes(), cur.startDate(), cur.endDate());
        String problem = EngagementLetterRules.sendProblem(cur.status(), d);
        if (problem != null) throw bad(problem);
        String text = EngagementLetterRules.render(practice(tenantId), clientName(cur.clientId()), cur.version(), d, LocalDate.now());
        jdbc.update("UPDATE acc_engagement_letters SET status = 'SENT', body_text = ?, sent_by = ?, sent_at = now() WHERE id = ? AND tenant_id = ?",
                text, by, id, tenantId.getValue());
        return load(tenantId, id);
    }

    @Transactional
    public LetterResponse accept(TenantId tenantId, UUID id, AcceptRequest req, UUID by) {
        LetterResponse cur = load(tenantId, id);
        String problem = EngagementLetterRules.acceptProblem(cur.status(), req.acceptedByName(), req.method(), req.acceptedOn(), LocalDate.now());
        if (problem != null) throw bad(problem);
        jdbc.update("UPDATE acc_engagement_letters SET status = 'SUPERSEDED' WHERE client_id = ? AND tenant_id = ? AND status = 'ACCEPTED'", cur.clientId(), tenantId.getValue());
        jdbc.update("""
                UPDATE acc_engagement_letters SET status = 'ACCEPTED', accepted_by_name = ?, accepted_method = ?, accepted_on = ?, accepted_recorded_by = ?
                 WHERE id = ? AND tenant_id = ?""",
                req.acceptedByName().trim(), req.method(), Date.valueOf(req.acceptedOn()), by, id, tenantId.getValue());
        return load(tenantId, id);
    }

    @Transactional
    public LetterResponse decline(TenantId tenantId, UUID id, DeclineRequest req) {
        LetterResponse cur = load(tenantId, id);
        String problem = EngagementLetterRules.declineProblem(cur.status());
        if (problem != null) throw bad(problem);
        String reason = req == null || req.reason() == null ? null : req.reason().trim();
        jdbc.update("UPDATE acc_engagement_letters SET status = 'DECLINED', decline_reason = ? WHERE id = ? AND tenant_id = ?",
                reason == null || reason.isEmpty() ? null : reason.substring(0, Math.min(reason.length(), 500)), id, tenantId.getValue());
        return load(tenantId, id);
    }

    @Transactional
    public LetterResponse end(TenantId tenantId, UUID id) {
        LetterResponse cur = load(tenantId, id);
        String problem = EngagementLetterRules.endProblem(cur.status());
        if (problem != null) throw bad(problem);
        jdbc.update("UPDATE acc_engagement_letters SET status = 'ENDED', ended_at = now() WHERE id = ? AND tenant_id = ?", id, tenantId.getValue());
        return load(tenantId, id);
    }

    /** The frozen text once sent; a preview of the current wording for a draft. */
    @Transactional(readOnly = true)
    public LetterText text(TenantId tenantId, UUID id) {
        LetterResponse cur = load(tenantId, id);
        String frozen = jdbc.queryForObject("SELECT body_text FROM acc_engagement_letters WHERE id = ?", String.class, id);
        String text = frozen != null ? frozen : EngagementLetterRules.render(practice(tenantId), clientName(cur.clientId()), cur.version(),
                new Draft(cur.title(), cur.scope(), cur.exclusions(), cur.feeBasis(), cur.feeAmount(), cur.feeNotes(), cur.startDate(), cur.endDate()), LocalDate.now());
        return new LetterText(id, cur.version(), cur.status(), text);
    }

    private String practice(TenantId tenantId) {
        List<String> r = jdbc.queryForList("SELECT firm_name FROM accountant_profiles WHERE tenant_id = ? LIMIT 1", String.class, tenantId.getValue());
        if (!r.isEmpty() && r.get(0) != null) return r.get(0);
        return jdbc.queryForObject("SELECT name FROM tenants WHERE id = ?", String.class, tenantId.getValue());
    }

    private String clientName(UUID clientId) {
        return jdbc.queryForObject("SELECT COALESCE(registered_name, trading_name) FROM acc_clients WHERE id = ?", String.class, clientId);
    }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
