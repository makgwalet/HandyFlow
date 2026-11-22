// security/application/internal/OccurrenceBookRules.java
package za.co.handyflow.platform.security.application.internal;

import za.co.handyflow.platform.shared.HandyFlowException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Pure rules for the Daily Occurrence Book: the day window, the text of manual entries, and the order of lines. */
public final class OccurrenceBookRules {

    public static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    static final int MIN_TEXT = 3;
    static final int MAX_TEXT = 2000;

    public enum Kind { ON_DUTY, OFF_DUTY, PATROL, GATE_IN, GATE_OUT, INCIDENT, ALARM, NOTE, HANDOVER, CORRECTION }

    public record Line(Instant at, Kind kind, String text, java.util.UUID guardId, String guardName, java.util.UUID refId) {}

    /** Reads of the same day by the same person are written to the audit trail at most this often. */
    static final java.time.Duration VIEW_AUDIT_EVERY = java.time.Duration.ofMinutes(15);
    static final int MAX_SIGNOFF_NOTE = 500;

    private OccurrenceBookRules() {}

    /** The South African day an instant falls in. */
    public static LocalDate dayOf(Instant at) { return at.atZone(SAST).toLocalDate(); }

    /** A signed day accepts only corrections. */
    public static void requireOpenOrCorrection(boolean daySigned, Kind kind) {
        if (daySigned && kind != Kind.CORRECTION) {
            throw new HandyFlowException("This day has been signed off. Add a Correction instead, or tell your supervisor.",
                    HttpStatus.CONFLICT, "OB_DAY_SIGNED");
        }
    }

    /** A day may be signed once it has started; a future day may not. */
    public static void requireSignable(LocalDate day, Instant now) {
        if (day == null) throw new HandyFlowException("Day is required.", HttpStatus.BAD_REQUEST, "OB_DAY_REQUIRED");
        dayOrToday(day, now);
    }

    public static String cleanSignoffNote(String note) {
        if (note == null || note.isBlank()) return null;
        String n = note.strip();
        if (n.length() > MAX_SIGNOFF_NOTE) {
            throw new HandyFlowException("Keep the note under " + MAX_SIGNOFF_NOTE + " characters.", HttpStatus.BAD_REQUEST, "OB_NOTE_INVALID");
        }
        return n;
    }

    /** True when a read should be written to the audit trail: never seen recently by this person. */
    public static boolean viewAuditDue(Instant lastViewed, Instant now) {
        return lastViewed == null || !lastViewed.isAfter(now.minus(VIEW_AUDIT_EVERY));
    }

    /** [start, end) of a South African calendar day. */
    public static Instant[] window(LocalDate day) {
        if (day == null) throw new IllegalArgumentException("day");
        return new Instant[]{day.atStartOfDay(SAST).toInstant(), day.plusDays(1).atStartOfDay(SAST).toInstant()};
    }

    /** The day to show: the one asked for, else today in South Africa. A day in the future is refused. */
    public static LocalDate dayOrToday(LocalDate asked, Instant now) {
        LocalDate today = now.atZone(SAST).toLocalDate();
        if (asked == null) return today;
        if (asked.isAfter(today)) {
            throw new HandyFlowException("That day has not happened yet.", HttpStatus.BAD_REQUEST, "OB_FUTURE_DAY");
        }
        return asked;
    }

    /** A manual entry's text, trimmed. Too short or too long is refused. */
    public static String cleanText(String text) {
        String t = text == null ? "" : text.strip();
        if (t.length() < MIN_TEXT || t.length() > MAX_TEXT) {
            throw new HandyFlowException("An entry needs " + MIN_TEXT + " to " + MAX_TEXT + " characters.",
                    HttpStatus.BAD_REQUEST, "OB_TEXT_INVALID");
        }
        return t;
    }

    /** Entry types a person may add by hand. Guards may only add NOTE or HANDOVER. */
    public static Kind manualKind(String raw, boolean supervisor) {
        Kind k;
        try { k = Kind.valueOf(raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { k = null; }
        boolean ok = k == Kind.NOTE || k == Kind.HANDOVER || (supervisor && k == Kind.CORRECTION);
        if (!ok) throw new HandyFlowException("Entry type must be NOTE or HANDOVER" + (supervisor ? " or CORRECTION" : "") + ".",
                HttpStatus.BAD_REQUEST, "OB_KIND_INVALID");
        return k;
    }

    /** Oldest first; ties keep a stable order by kind so the page does not shuffle between loads. */
    public static List<Line> order(List<Line> lines) {
        return lines.stream()
                .sorted(Comparator.comparing(Line::at).thenComparing(l -> l.kind().ordinal()).thenComparing(l -> l.text() == null ? "" : l.text()))
                .toList();
    }

    /** A client reference is kept only if it is sane. */
    public static String cleanRef(String ref) {
        if (ref == null || ref.isBlank()) return null;
        String r = ref.strip();
        if (r.length() > 64) throw new HandyFlowException("Reference too long.", HttpStatus.BAD_REQUEST, "OB_REF_INVALID");
        return r;
    }

    /** The union of everything that happened at a site in the window. Params: tenant, site, from, to. */
    public static final String DAY_SQL = """
            WITH p AS (SELECT ?::uuid AS t, ?::uuid AS s, ?::timestamptz AS f, ?::timestamptz AS e)
            SELECT at, kind, text, guard_id, guard_name, ref_id FROM (
              SELECT d.started_at AS at, 'ON_DUTY' AS kind, 'Went on duty' AS text, d.guard_id, g.first_name || ' ' || g.last_name AS guard_name, d.id AS ref_id
                FROM security_device_sessions d JOIN security_shifts sh ON sh.id = d.shift_id
                LEFT JOIN security_guards g ON g.id = d.guard_id, p
               WHERE d.tenant_id = p.t AND sh.site_id = p.s AND d.started_at >= p.f AND d.started_at < p.e
              UNION ALL
              SELECT d.ended_at, 'OFF_DUTY',
                     'Went off duty' || COALESCE('. Handover: ' || NULLIF(d.handover_notes, ''), '') || COALESCE('. Closed by supervisor: ' || NULLIF(d.forced_close_reason, ''), ''),
                     d.guard_id, g.first_name || ' ' || g.last_name, d.id
                FROM security_device_sessions d JOIN security_shifts sh ON sh.id = d.shift_id
                LEFT JOIN security_guards g ON g.id = d.guard_id, p
               WHERE d.tenant_id = p.t AND sh.site_id = p.s AND d.ended_at >= p.f AND d.ended_at < p.e
              UNION ALL
              SELECT r.started_at, 'PATROL', 'Patrol round ' || r.round_number || ' started', sh.guard_id, g.first_name || ' ' || g.last_name, r.id
                FROM security_patrol_rounds r LEFT JOIN security_shifts sh ON sh.id = r.shift_id
                LEFT JOIN security_guards g ON g.id = sh.guard_id, p
               WHERE r.tenant_id = p.t AND r.site_id = p.s AND r.started_at >= p.f AND r.started_at < p.e
              UNION ALL
              SELECT r.completed_at, 'PATROL', 'Patrol round ' || r.round_number || ' completed, ' || r.checkpoints_scanned || ' of ' || r.checkpoints_expected || ' checkpoints scanned', sh.guard_id, g.first_name || ' ' || g.last_name, r.id
                FROM security_patrol_rounds r LEFT JOIN security_shifts sh ON sh.id = r.shift_id
                LEFT JOIN security_guards g ON g.id = sh.guard_id, p
               WHERE r.tenant_id = p.t AND r.site_id = p.s AND r.completed_at >= p.f AND r.completed_at < p.e
              UNION ALL
              SELECT e.logged_in_at, 'GATE_IN', e.entry_type || ' in' || COALESCE(' from ' || NULLIF(e.company, ''), '') || COALESCE(', vehicle ' || NULLIF(e.vehicle_registration, ''), ''), e.logged_in_by_guard_id, g.first_name || ' ' || g.last_name, e.id
                FROM security_gate_register_entries e LEFT JOIN security_guards g ON g.id = e.logged_in_by_guard_id, p
               WHERE e.tenant_id = p.t AND e.site_id = p.s AND e.logged_in_at >= p.f AND e.logged_in_at < p.e
              UNION ALL
              SELECT e.logged_out_at, 'GATE_OUT', e.entry_type || ' out' || COALESCE(' from ' || NULLIF(e.company, ''), '') || COALESCE(', vehicle ' || NULLIF(e.vehicle_registration, ''), ''), e.logged_out_by_guard_id, g.first_name || ' ' || g.last_name, e.id
                FROM security_gate_register_entries e LEFT JOIN security_guards g ON g.id = e.logged_out_by_guard_id, p
               WHERE e.tenant_id = p.t AND e.site_id = p.s AND e.logged_out_at >= p.f AND e.logged_out_at < p.e
              UNION ALL
              SELECT (i.occurred_at AT TIME ZONE 'UTC'), 'INCIDENT', COALESCE(NULLIF(i.title, ''), i.type) || ' (' || i.severity || ', ' || i.status || ')', i.guard_id, g.first_name || ' ' || g.last_name, i.id
                FROM security_incidents i LEFT JOIN security_guards g ON g.id = i.guard_id, p
               WHERE i.tenant_id = p.t AND i.site_id = p.s AND i.deleted_at IS NULL
                 AND (i.occurred_at AT TIME ZONE 'UTC') >= p.f AND (i.occurred_at AT TIME ZONE 'UTC') < p.e
              UNION ALL
              SELECT a.created_at, 'ALARM', 'Alarm from ' || a.source || COALESCE(': ' || NULLIF(a.description, ''), ''), a.triggered_by_guard_id, g.first_name || ' ' || g.last_name, a.id
                FROM security_alarm_events a LEFT JOIN security_guards g ON g.id = a.triggered_by_guard_id, p
               WHERE a.tenant_id = p.t AND a.site_id = p.s AND a.created_at >= p.f AND a.created_at < p.e
              UNION ALL
              SELECT o.recorded_at, o.entry_type, o.text, o.guard_id, g.first_name || ' ' || g.last_name, o.id
                FROM security_ob_entries o LEFT JOIN security_guards g ON g.id = o.guard_id, p
               WHERE o.tenant_id = p.t AND o.site_id = p.s AND o.recorded_at >= p.f AND o.recorded_at < p.e
            ) x ORDER BY at, kind LIMIT 2000""";
}
