// security/application/internal/MonthlyPackEmailService.java
package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.application.internal.MonthlyPackService.Pack;
import za.co.handyflow.platform.security.application.internal.ReportEmailRules.Content;
import za.co.handyflow.platform.security.application.internal.ReportEmailRules.Line;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Manages who receives a site's monthly service report, and sends it. */
@Service
@RequiredArgsConstructor
public class MonthlyPackEmailService {

    public record Recipient(UUID id, String name, String email) {}
    public record Sent(int queued, String month) {}

    private final JdbcTemplate jdbc;
    private final MonthlyPackService packs;
    @Autowired(required = false)
    private EmailService mail;

    @Transactional(readOnly = true)
    public List<Recipient> recipients(TenantId tenantId, UUID siteId) {
        requireSite(tenantId.getValue(), siteId);
        return list(tenantId.getValue(), siteId);
    }

    @Transactional
    public UUID add(TenantId tenantId, UUID siteId, UUID actor, String name, String email) {
        UUID tenant = tenantId.getValue();
        requireSite(tenant, siteId);
        String n = ReportEmailRules.cleanName(name), e = ReportEmailRules.cleanEmail(email);
        List<Recipient> now = list(tenant, siteId);
        if (now.stream().anyMatch(r -> r.email().equals(e)))
            throw new HandyFlowException("That address is already on the list", HttpStatus.CONFLICT, "RECIPIENT_EXISTS");
        ReportEmailRules.requireRoom(now.size());
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO security_report_recipients (id, tenant_id, site_id, name, email, created_by) VALUES (?,?,?,?,?,?)",
                id, tenant, siteId, n, e, actor);
        audit(tenant, actor, siteId, "RECIPIENT_ADDED", "{\"recipientId\":\"" + id + "\"}");
        return id;
    }

    @Transactional
    public void remove(TenantId tenantId, UUID siteId, UUID recipientId, UUID actor) {
        UUID tenant = tenantId.getValue();
        requireSite(tenant, siteId);
        int n = jdbc.update("UPDATE security_report_recipients SET active = FALSE, removed_at = now(), removed_by = ? " +
                "WHERE id = ? AND tenant_id = ? AND site_id = ? AND active", actor, recipientId, tenant, siteId);
        if (n == 0) throw new HandyFlowException("Recipient not found", HttpStatus.NOT_FOUND, "RECIPIENT_NOT_FOUND");
        audit(tenant, actor, siteId, "RECIPIENT_REMOVED", "{\"recipientId\":\"" + recipientId + "\"}");
    }

    @Transactional
    public Sent send(TenantId tenantId, UUID siteId, UUID actor, String monthAsked) { return send(tenantId, siteId, actor, monthAsked, Instant.now()); }

    @Transactional
    Sent send(TenantId tenantId, UUID siteId, UUID actor, String monthAsked, Instant now) {
        UUID tenant = tenantId.getValue();
        requireSite(tenant, siteId);
        if (mail == null) throw new HandyFlowException("Email is not set up on this server", HttpStatus.CONFLICT, "EMAIL_NOT_CONFIGURED");
        List<Recipient> to = list(tenant, siteId);
        ReportEmailRules.requireRecipients(to.size());
        Pack p = packs.pack(tenantId, siteId, monthAsked, now);
        ReportEmailRules.requireFinished(YearMonth.parse(p.month()), now);
        List<Timestamp> last = jdbc.query("""
                SELECT max(occurred_at) AS t FROM security_audit_log
                WHERE tenant_id = ? AND entity_type = 'SITE_REPORT' AND entity_id = ? AND action = 'PACK_EMAILED' AND metadata->>'month' = ?""",
                (rs, i) -> rs.getTimestamp("t"), tenant, siteId, p.month());
        ReportEmailRules.requireNotJustSent(last.isEmpty() || last.get(0) == null ? null : last.get(0).toInstant(), now);

        String title = ReportEmailRules.monthTitle(YearMonth.parse(p.month()));
        Content content = new Content(p.siteName(), title, p.from() + " to " + p.to(), lines(p));
        String subject = ReportEmailRules.subject(p.siteName(), title), html = ReportEmailRules.html(content);
        audit(tenant, actor, siteId, "PACK_EMAILED", "{\"month\":\"" + p.month() + "\",\"recipients\":" + to.size() + "}");
        for (Recipient r : to) mail.send(r.email(), subject, html); // one message each: recipients never see each other
        return new Sent(to.size(), p.month());
    }

    static List<Line> lines(Pack p) {
        List<Line> out = new ArrayList<>();
        var c = p.cover();
        String detail = PackWording.coverDetail(c.measured(), c.postDays(), c.postDaysShort(), c.postDaysUncovered());
        out.add(new Line("Cover", PackWording.cover(c.measured(), c.postDays(), c.requiredGuardHours(), c.coveredGuardHours(), c.coveragePct())
                + (detail.isEmpty() ? "" : " " + detail)));
        out.add(new Line("Shifts", PackWording.shifts(p.shifts())));
        var pt = p.patrols();
        out.add(new Line("Patrols", PackWording.patrols(pt.rounds(), pt.complete(), pt.partial(), pt.missed(), pt.checkpointsExpected(), pt.checkpointsScanned(), pt.checkpointPct())));
        out.add(new Line("Incidents", PackWording.incidents(p.incidents().total(), p.incidents().resolved(), p.incidents().bySeverity())));
        out.add(new Line("Visitors", PackWording.visitors(p.visitors().total(), p.visitors().withVehicle(), p.visitors().byType())));
        out.add(new Line("Alarms", p.alarms() == 0 ? "No alarms." : p.alarms() + (p.alarms() == 1 ? " alarm." : " alarms.")));
        out.add(new Line("Occurrence Book", PackWording.book(p.occurrenceBook().daysSigned(), p.occurrenceBook().daysInPeriod())));
        return out;
    }

    private List<Recipient> list(UUID tenant, UUID siteId) {
        return jdbc.query("SELECT id, name, email FROM security_report_recipients WHERE tenant_id = ? AND site_id = ? AND active ORDER BY created_at, id",
                (rs, i) -> new Recipient((UUID) rs.getObject("id"), rs.getString("name"), rs.getString("email")), tenant, siteId);
    }

    private void requireSite(UUID tenant, UUID siteId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM security_sites WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL", Integer.class, siteId, tenant);
        if (n == null || n == 0) throw new HandyFlowException("Site not found", HttpStatus.NOT_FOUND, "SITE_NOT_FOUND");
    }

    private void audit(UUID tenant, UUID actor, UUID siteId, String action, String metadataJson) {
        jdbc.update("""
                INSERT INTO security_audit_log (id, tenant_id, actor_id, actor_type, entity_type, entity_id, action, metadata, occurred_at)
                VALUES (gen_random_uuid(), ?, ?, 'USER', 'SITE_REPORT', ?, ?, ?::jsonb, now())""", tenant, actor, siteId, action, metadataJson);
    }
}
