package za.co.handyflow.platform.accountant.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accountant.dto.EngagementResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Review and sign-off of a client's financial year (finance patch F15). One row per client and year; every step is logged.
 * A signed-off year refuses new journals (see {@link #requireNotSignedOff}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EngagementService {

    private static final String SIGNOFF = "ACCOUNTANT_SIGNOFF";

    private final JdbcTemplate jdbc;

    @Transactional
    public EngagementResponse get(TenantId tenantId, UUID clientId, int financialYear) {
        return toResponse(tenantId, clientId, financialYear, row(tenantId, clientId, financialYear));
    }

    @Transactional(readOnly = true)
    public List<EngagementResponse> list(TenantId tenantId, UUID clientId) {
        int yem = yearEndMonth(tenantId, clientId);
        List<Integer> years = jdbc.queryForList("SELECT financial_year FROM acc_engagements WHERE client_id = ? ORDER BY financial_year DESC",
                Integer.class, clientId);
        int current = EngagementRules.financialYearOf(yem, LocalDate.now().getYear(), LocalDate.now().getMonthValue());
        if (!years.contains(current)) years.add(0, current);
        List<EngagementResponse> out = new ArrayList<>();
        for (int y : years) {
            Row r = find(tenantId, clientId, y);
            out.add(toResponse(tenantId, clientId, y, r != null ? r : new Row(null, EngagementRules.OPEN, null, null, null, null, null, null)));
        }
        return out;
    }

    @Transactional
    public EngagementResponse prepare(TenantId tenantId, UUID clientId, int fy, String note, UUID by) {
        Row r = row(tenantId, clientId, fy);
        String problem = EngagementRules.prepareProblem(r.status(), unposted(tenantId, clientId, fy));
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "ENGAGEMENT_REFUSED");
        jdbc.update("UPDATE acc_engagements SET status = 'PREPARED', prepared_by = ?, prepared_at = now(), reviewed_by = NULL, reviewed_at = NULL, note = ?, updated_at = now() WHERE id = ?",
                by, trim(note), r.id());
        event(tenantId, r.id(), "PREPARED", by, note);
        return get(tenantId, clientId, fy);
    }

    @Transactional
    public EngagementResponse review(TenantId tenantId, UUID clientId, int fy, String note, UUID by) {
        Row r = row(tenantId, clientId, fy);
        String problem = EngagementRules.reviewProblem(r.status(), r.preparedBy(), by);
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "ENGAGEMENT_REFUSED");
        jdbc.update("UPDATE acc_engagements SET status = 'REVIEWED', reviewed_by = ?, reviewed_at = now(), updated_at = now() WHERE id = ?", by, r.id());
        event(tenantId, r.id(), "REVIEWED", by, note);
        return get(tenantId, clientId, fy);
    }

    @Transactional
    public EngagementResponse signOff(TenantId tenantId, UUID clientId, int fy, String note, UUID by) {
        Row r = row(tenantId, clientId, fy);
        boolean others = otherSigners(tenantId, by) > 0;
        String problem = EngagementRules.signOffProblem(r.status(), r.preparedBy(), by, others);
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "ENGAGEMENT_REFUSED");
        long unposted = unposted(tenantId, clientId, fy);
        if (unposted > 0)
            throw new HandyFlowException(unposted + " journal(s) in this year are not posted. Post or reverse them first",
                    HttpStatus.BAD_REQUEST, "ENGAGEMENT_REFUSED");
        if (EngagementRules.soleSigner(r.preparedBy(), by, others))
            log.warn("[Accountant] Year signed off by the person who prepared it; nobody else holds {}: tenant={} client={} fy={} user={}",
                    SIGNOFF, tenantId, clientId, fy, by);
        jdbc.update("UPDATE acc_engagements SET status = 'SIGNED_OFF', signed_by = ?, signed_at = now(), updated_at = now() WHERE id = ?", by, r.id());
        event(tenantId, r.id(), "SIGNED_OFF", by, note);
        return get(tenantId, clientId, fy);
    }

    /**
     * Sends a prepared or reviewed year back, or reopens a signed-off one. The reason is kept. {@code signedOffOnly} is true
     * on the endpoint for ACCOUNTANT_SIGNOFF holders and false on the one for ordinary staff, so staff can send work back
     * but only a partner can undo a sign-off.
     */
    @Transactional
    public EngagementResponse reopenOrSendBack(TenantId tenantId, UUID clientId, int fy, String reason, UUID by, boolean partner) {
        Row r = row(tenantId, clientId, fy);
        if (!partner && EngagementRules.SIGNED_OFF.equals(r.status()))
            throw new HandyFlowException("A signed-off year can only be reopened by a partner", HttpStatus.FORBIDDEN, "ENGAGEMENT_REFUSED");
        String problem = EngagementRules.reopenProblem(r.status(), reason);
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "ENGAGEMENT_REFUSED");
        jdbc.update("UPDATE acc_engagements SET status = 'OPEN', reviewed_by = NULL, reviewed_at = NULL, signed_by = NULL, signed_at = NULL, updated_at = now() WHERE id = ?", r.id());
        event(tenantId, r.id(), EngagementRules.SIGNED_OFF.equals(r.status()) ? "REOPENED" : "SENT_BACK", by, reason.trim());
        log.warn("[Accountant] Year {} for client {} returned to OPEN from {} by {}: {}", fy, clientId, r.status(), by, reason.trim());
        return get(tenantId, clientId, fy);
    }

    /** Throws when a journal for this month would land in a signed-off year. */
    @Transactional(readOnly = true)
    public void requireNotSignedOff(TenantId tenantId, UUID clientId, int periodYear, int periodMonth) {
        int fy = EngagementRules.financialYearOf(yearEndMonth(tenantId, clientId), periodYear, periodMonth);
        Row r = find(tenantId, clientId, fy);
        if (r != null && EngagementRules.locksJournals(r.status()))
            throw new HandyFlowException("The financial year ending " + fy + " has been signed off. Reopen it before adding journals to it",
                    HttpStatus.BAD_REQUEST, "ENGAGEMENT_SIGNED_OFF");
    }

    // ── internals ────────────────────────────────────────────────────────────

    private record Row(UUID id, String status, UUID preparedBy, Timestamp preparedAt, UUID reviewedBy, Timestamp reviewedAt,
                       UUID signedBy, Timestamp signedAt) {}

    private Row find(TenantId tenantId, UUID clientId, int fy) {
        return jdbc.query("""
                SELECT id, status, prepared_by, prepared_at, reviewed_by, reviewed_at, signed_by, signed_at
                  FROM acc_engagements WHERE tenant_id = ? AND client_id = ? AND financial_year = ?""",
                (rs, n) -> new Row(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class), rs.getTimestamp(4),
                        rs.getObject(5, UUID.class), rs.getTimestamp(6), rs.getObject(7, UUID.class), rs.getTimestamp(8)),
                tenantId.getValue(), clientId, fy).stream().findFirst().orElse(null);
    }

    /** The year's row, created OPEN on first use. */
    private Row row(TenantId tenantId, UUID clientId, int fy) {
        yearEndMonth(tenantId, clientId); // 404 for an unknown client
        jdbc.update("INSERT INTO acc_engagements (tenant_id, client_id, financial_year) VALUES (?, ?, ?) ON CONFLICT (client_id, financial_year) DO NOTHING",
                tenantId.getValue(), clientId, fy);
        return find(tenantId, clientId, fy);
    }

    private int yearEndMonth(TenantId tenantId, UUID clientId) {
        List<Integer> m = jdbc.queryForList("SELECT year_end_month FROM acc_clients WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL",
                Integer.class, tenantId.getValue(), clientId);
        if (m.isEmpty()) throw new ResourceNotFoundException("Client", clientId.toString());
        return m.get(0);
    }

    private long unposted(TenantId tenantId, UUID clientId, int fy) {
        int yem = yearEndMonth(tenantId, clientId);
        Long n = jdbc.queryForObject("""
                SELECT count(*) FROM prac_journals
                 WHERE tenant_id = ? AND client_id = ? AND journal_date BETWEEN ? AND ?
                   AND status IN ('DRAFT', 'PREPARED', 'REVIEWED', 'APPROVED')""",
                Long.class, tenantId.getValue(), clientId,
                Date.valueOf(EngagementRules.start(yem, fy)), Date.valueOf(EngagementRules.end(yem, fy)));
        return n != null ? n : 0;
    }

    private long otherSigners(TenantId tenantId, UUID signer) {
        Long n = jdbc.queryForObject("""
                SELECT count(DISTINCT u.id)
                  FROM users u
                  JOIN user_roles ur ON ur.user_id = u.id
                  JOIN role_permissions rp ON rp.role_id = ur.role_id
                  JOIN permissions p ON p.id = rp.permission_id
                 WHERE u.tenant_id = ? AND u.status = 'ACTIVE' AND p.name = ? AND u.id <> ?""",
                Long.class, tenantId.getValue(), SIGNOFF, signer);
        return n != null ? n : 0;
    }

    private void event(TenantId tenantId, UUID engagementId, String action, UUID by, String note) {
        jdbc.update("INSERT INTO acc_engagement_events (tenant_id, engagement_id, action, by_user, note) VALUES (?, ?, ?, ?, ?)",
                tenantId.getValue(), engagementId, action, by, trim(note));
    }

    private static String trim(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        return t.length() > 500 ? t.substring(0, 500) : t;
    }

    private String nameOf(UUID userId) {
        if (userId == null) return null;
        return jdbc.queryForList("SELECT first_name || ' ' || last_name FROM users WHERE id = ?", String.class, userId)
                .stream().findFirst().orElse(null);
    }

    private EngagementResponse toResponse(TenantId tenantId, UUID clientId, int fy, Row r) {
        int yem = yearEndMonth(tenantId, clientId);
        Long overdue = jdbc.queryForObject("SELECT count(*) FROM acc_tax_deadlines WHERE client_id = ? AND status = 'OVERDUE'", Long.class, clientId);
        Long openReq = jdbc.queryForObject("SELECT count(*) FROM acc_document_requests WHERE client_id = ? AND status = 'PENDING'", Long.class, clientId);
        Boolean fica = jdbc.queryForObject("SELECT fica_completed FROM acc_clients WHERE id = ?", Boolean.class, clientId);
        List<EngagementResponse.Event> events = r.id() == null ? List.of() : jdbc.query(
                "SELECT action, by_user, note, at FROM acc_engagement_events WHERE engagement_id = ? ORDER BY at",
                (rs, n) -> new EngagementResponse.Event(rs.getString(1), rs.getObject(2, UUID.class), null, rs.getString(3), rs.getTimestamp(4).toInstant()),
                r.id()).stream().map(e -> new EngagementResponse.Event(e.action(), e.byUser(), nameOf(e.byUser()), e.note(), e.at())).toList();
        return new EngagementResponse(r.id(), clientId, fy, EngagementRules.start(yem, fy), EngagementRules.end(yem, fy), r.status(),
                r.preparedBy(), nameOf(r.preparedBy()), inst(r.preparedAt()),
                r.reviewedBy(), nameOf(r.reviewedBy()), inst(r.reviewedAt()),
                r.signedBy(), nameOf(r.signedBy()), inst(r.signedAt()),
                unposted(tenantId, clientId, fy),
                EngagementRules.warnings(overdue != null ? overdue : 0, Boolean.TRUE.equals(fica), openReq != null ? openReq : 0),
                events);
    }

    private static java.time.Instant inst(Timestamp t) { return t == null ? null : t.toInstant(); }
}
