// security/application/internal/OccurrenceBookService.java
package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.application.internal.OccurrenceBookRules.Kind;
import za.co.handyflow.platform.security.application.internal.OccurrenceBookRules.Line;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The Daily Occurrence Book of a site: what the system recorded that day plus the notes people wrote by hand. */
@Service
@RequiredArgsConstructor
public class OccurrenceBookService {

    public record Signoff(UUID signedBy, String signedByName, Instant signedAt, String note) {}

    public record Day(UUID siteId, String siteName, LocalDate date, List<Line> lines, boolean truncated, Signoff signoff) {}

    private final JdbcTemplate jdbc;

    /** The company's calendar. Optional so a hand-built service still works, on the South African default. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private za.co.handyflow.platform.shared.BusinessCalendar calendar;

    private java.time.ZoneId zone(TenantId tenantId) {
        return za.co.handyflow.platform.shared.BusinessCalendar.zoneOf(calendar, tenantId);
    }

    /** Reading is recorded in the audit trail (once per person, site and day every 15 minutes), so this is not read-only. */
    @Transactional
    public Day day(TenantId tenantId, UUID siteId, LocalDate asked, UUID viewer) {
        Instant now = Instant.now();
        Day d = day(tenantId, siteId, asked, now);
        auditView(tenantId.getValue(), viewer, siteId, d.date(), now);
        return d;
    }

    @Transactional(readOnly = true)
    Day day(TenantId tenantId, UUID siteId, LocalDate asked, Instant now) {
        String siteName = siteName(tenantId.getValue(), siteId);
        LocalDate date = OccurrenceBookRules.dayOrToday(asked, now, zone(tenantId));
        Instant[] w = OccurrenceBookRules.window(date, zone(tenantId));
        List<Line> lines = jdbc.query(OccurrenceBookRules.DAY_SQL, (rs, i) -> new Line(
                rs.getTimestamp("at").toInstant(), Kind.valueOf(rs.getString("kind")), rs.getString("text"),
                (UUID) rs.getObject("guard_id"), rs.getString("guard_name"), (UUID) rs.getObject("ref_id")),
                tenantId.getValue(), siteId, Timestamp.from(w[0]), Timestamp.from(w[1]));
        return new Day(siteId, siteName, date, OccurrenceBookRules.order(lines), lines.size() >= 2000, signoff(tenantId.getValue(), siteId, date));
    }

    private Signoff signoff(UUID tenant, UUID siteId, LocalDate date) {
        List<Signoff> l = jdbc.query("""
                SELECT s.signed_by, u.first_name || ' ' || u.last_name AS name, s.signed_at, s.note
                FROM security_ob_signoffs s LEFT JOIN users u ON u.id = s.signed_by
                WHERE s.tenant_id = ? AND s.site_id = ? AND s.day = ?""",
                (rs, i) -> new Signoff((UUID) rs.getObject("signed_by"), rs.getString("name"), rs.getTimestamp("signed_at").toInstant(), rs.getString("note")),
                tenant, siteId, java.sql.Date.valueOf(date));
        return l.isEmpty() ? null : l.get(0);
    }

    /** Signs the day. Signing again changes nothing. Returns the signature on record. */
    @Transactional
    public Signoff sign(TenantId tenantId, UUID siteId, LocalDate day, UUID signer, String note) {
        siteName(tenantId.getValue(), siteId);
        Instant now = Instant.now();
        OccurrenceBookRules.requireSignable(day, now, zone(tenantId));
        String n = OccurrenceBookRules.cleanSignoffNote(note);
        int added = jdbc.update("""
                INSERT INTO security_ob_signoffs (tenant_id, site_id, day, signed_by, signed_at, note) VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, site_id, day) DO NOTHING""",
                tenantId.getValue(), siteId, java.sql.Date.valueOf(day), signer, Timestamp.from(now), n);
        if (added > 0) audit(tenantId.getValue(), signer, siteId, "OB_SIGNED", "{\"date\":\"" + day + "\"}");
        return signoff(tenantId.getValue(), siteId, day);
    }

    private void auditView(UUID tenant, UUID viewer, UUID siteId, LocalDate date, Instant now) {
        if (viewer == null) return;
        List<Timestamp> last = jdbc.query("""
                SELECT max(occurred_at) AS t FROM security_audit_log
                WHERE tenant_id = ? AND actor_id = ? AND entity_type = 'OCCURRENCE_BOOK' AND entity_id = ? AND action = 'VIEWED' AND metadata->>'date' = ?""",
                (rs, i) -> rs.getTimestamp("t"), tenant, viewer, siteId, date.toString());
        Timestamp t = last.isEmpty() ? null : last.get(0);
        if (OccurrenceBookRules.viewAuditDue(t == null ? null : t.toInstant(), now)) {
            audit(tenant, viewer, siteId, "VIEWED", "{\"date\":\"" + date + "\"}");
        }
    }

    private void audit(UUID tenant, UUID actor, UUID siteId, String action, String metadataJson) {
        jdbc.update("""
                INSERT INTO security_audit_log (id, tenant_id, actor_id, actor_type, entity_type, entity_id, action, metadata, occurred_at)
                VALUES (gen_random_uuid(), ?, ?, 'USER', 'OCCURRENCE_BOOK', ?, ?, ?::jsonb, now())""",
                tenant, actor, siteId, action, metadataJson);
    }

    /** Adds a hand-written entry. Entries are never edited or deleted. A repeated client reference is ignored. */
    @Transactional
    public void addEntry(TenantId tenantId, UUID siteId, UUID guardId, UUID enteredBy, String kind, boolean supervisor,
                         String text, Instant recordedAt, String clientRef) {
        siteName(tenantId.getValue(), siteId);
        Kind k = OccurrenceBookRules.manualKind(kind, supervisor);
        String clean = OccurrenceBookRules.cleanText(text);
        Instant when = OfflineTimestamp.accept(recordedAt, Instant.now());
        Integer signed = jdbc.queryForObject("SELECT count(*) FROM security_ob_signoffs WHERE tenant_id = ? AND site_id = ? AND day = ?",
                Integer.class, tenantId.getValue(), siteId, java.sql.Date.valueOf(OccurrenceBookRules.dayOf(when, zone(tenantId))));
        OccurrenceBookRules.requireOpenOrCorrection(signed != null && signed > 0, k);
        jdbc.update("""
                INSERT INTO security_ob_entries (tenant_id, site_id, guard_id, entered_by, entry_type, text, recorded_at, client_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, entered_by, client_ref) WHERE client_ref IS NOT NULL DO NOTHING""",
                tenantId.getValue(), siteId, guardId, enteredBy, k.name(), clean, Timestamp.from(when), OccurrenceBookRules.cleanRef(clientRef));
    }

    private String siteName(UUID tenant, UUID siteId) {
        List<String> n = jdbc.queryForList("SELECT name FROM security_sites WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL",
                String.class, siteId, tenant);
        if (n.isEmpty()) throw new HandyFlowException("Site not found", HttpStatus.NOT_FOUND, "SITE_NOT_FOUND");
        return n.get(0);
    }
}
