package za.co.handyflow.platform.legalcompliance.application.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * What the Audit Trail report may show, and over what period. Pure rules.
 *
 * Only areas on the allow-list below appear. The restricted areas (FICA, conflict-of-interest declarations and gifts, individual attestations, PAIA requests, watch items)
 * are not listed, so a new kind of event is excluded until someone decides it is safe to show, rather than shown until someone remembers to hide it. The report also leaves out
 * the free-text detail of each event, because details can quote notes and reasons that name people; the record's own screen shows its full history to those allowed to see it.
 */
public final class AuditTrailRules {

    private AuditTrailRules() {}

    public static final int WINDOW_DAYS = 90;
    static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final Map<String, String> AREAS = new LinkedHashMap<>();
    static {
        AREAS.put("INSTRUMENT", "Regulatory library"); AREAS.put("REQUIREMENT", "Regulatory library"); AREAS.put("VERSION", "Regulatory library");
        AREAS.put("CHANGE", "Regulatory change"); AREAS.put("ASSESSMENT", "Applicability"); AREAS.put("OBLIGATION", "Obligation");
        AREAS.put("RISK", "Risk"); AREAS.put("CONTROL", "Control"); AREAS.put("CONTROL_TEST", "Control test");
        AREAS.put("FINDING", "Finding"); AREAS.put("AUDIT", "Audit"); AREAS.put("AUDIT_FINDING", "Audit finding"); AREAS.put("CAPA", "Corrective action");
        AREAS.put("POLICY", "Policy"); AREAS.put("COMMITTEE", "Committee"); AREAS.put("INCIDENT", "Incident"); AREAS.put("PIA", "Privacy impact assessment");
        AREAS.put("FILING", "Regulator filing"); AREAS.put("LICENCE", "Licence"); AREAS.put("LICENCE_RENEWAL", "Licence renewal");
        AREAS.put("THIRD_PARTY", "Third party"); AREAS.put("THIRD_PARTY_ITEM", "Third-party document");
        AREAS.put("BC_PROCESS", "Continuity process"); AREAS.put("BC_PLAN", "Continuity plan"); AREAS.put("ESG_METRIC", "ESG metric"); AREAS.put("ESG_READING", "ESG reading");
        AREAS.put("REPORT", "Report export"); AREAS.put("WATCH_SOURCE", "Regulatory monitoring"); AREAS.put("POSTURE_SNAPSHOT", "Executive snapshot");
        AREAS.put("PROVIDER_CLIENT", "Provider relationship"); AREAS.put("PROVIDER_ACCESS", "Provider access");
    }

    public static Set<String> allowedTypes() { return AREAS.keySet(); }

    /** The friendly area name, or null when the area is not on the allow-list. */
    public static String area(String entityType) { return entityType == null ? null : AREAS.get(entityType); }

    /** [start, end) of the window: the 90 days ending at the close of the given day (today when null). */
    public static Instant[] window(LocalDate asOf, LocalDate today) {
        LocalDate last = asOf == null ? today : asOf;
        Instant end = last.plusDays(1).atStartOfDay(ZONE).toInstant();
        Instant start = last.plusDays(1).minusDays(WINDOW_DAYS).atStartOfDay(ZONE).toInstant();
        return new Instant[]{start, end};
    }

    public static String stamp(Instant t) { return t == null ? "" : ZonedDateTime.ofInstant(t, ZONE).format(STAMP); }

    /** "RESOLUTION_REJECTED" becomes "Resolution rejected". */
    public static String eventLabel(String eventType) {
        if (eventType == null || eventType.isBlank()) return "";
        String s = eventType.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
