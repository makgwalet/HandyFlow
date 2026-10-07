// security/application/internal/ReportRunService.java
package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.dto.ReportCatalogueDtos.*;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/**
 * Remembers when each security report was last generated and by whom, and lists the reports on offer.
 * Recording never fails a report: a problem writing the history is logged and the report is still returned.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportRunService {

    public static final String SITE_COVERAGE = "site-coverage";
    public static final String GUARD_ATTENDANCE = "guard-attendance";
    public static final String MONTHLY_SUMMARY = "monthly-summary";
    public static final String SITE_ACCESS = "site-access";

    static final int RECENT_LIMIT = 10;
    static final int SUBJECT_MAX = 200;

    /** The reports on offer, in display order: key, title, description, scope. */
    record Def(String key, String title, String description, String scope) {}

    static final List<Def> DEFS = List.of(
            new Def(MONTHLY_SUMMARY, "Monthly summary",
                    "Company-wide shifts, guard hours, coverage by site and incidents for a month.", "NONE"),
            new Def(SITE_COVERAGE, "Site coverage",
                    "Shifts scheduled against completed, patrol rounds, scans and incidents at one site.", "SITE"),
            new Def(GUARD_ATTENDANCE, "Guard attendance",
                    "Shifts attended and missed, hours worked, scans and incidents for one guard.", "GUARD"),
            new Def(SITE_ACCESS, "Site access",
                    "Visitor, contractor and vehicle entries and exits at one site.", "SITE"));

    private final JdbcTemplate jdbc;

    /** Called after a report is produced. format is VIEW or PDF. Skipped during read-only support sessions. */
    public void record(TenantId tenantId, String reportKey, String period, String subject, String format) {
        try {
            if (TenantContext.isImpersonation()) return;
            String userId = TenantContext.getUserId();
            insert(tenantId.getValue(), reportKey, period, subject, format,
                    userId == null ? null : UUID.fromString(userId), TenantContext.getCurrentUserName(), Instant.now());
        } catch (RuntimeException e) {
            log.warn("Could not record report run {} {}: {}", reportKey, period, e.getMessage());
        }
    }

    void insert(UUID tenant, String key, String period, String subject, String format,
                UUID userId, String userName, Instant at) {
        jdbc.update("""
                INSERT INTO security_report_runs (id, tenant_id, report_key, period, subject, format,
                                                  generated_by_id, generated_by_name, generated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                UUID.randomUUID(), tenant, key, period, clip(subject), format, userId, userName, Timestamp.from(at));
    }

    static String clip(String s) {
        if (s == null) return null;
        return s.length() <= SUBJECT_MAX ? s : s.substring(0, SUBJECT_MAX);
    }

    @Transactional(readOnly = true)
    public Catalogue catalogue(TenantId tenantId) {
        UUID tenant = tenantId.getValue();
        List<Run> latest = jdbc.query("""
                SELECT DISTINCT ON (report_key) report_key, period, subject, format, generated_by_name, generated_at
                FROM security_report_runs WHERE tenant_id = ?
                ORDER BY report_key, generated_at DESC""", (rs, i) -> run(rs), tenant);
        List<Run> recent = jdbc.query("""
                SELECT report_key, period, subject, format, generated_by_name, generated_at
                FROM security_report_runs WHERE tenant_id = ?
                ORDER BY generated_at DESC LIMIT ?""", (rs, i) -> run(rs), tenant, RECENT_LIMIT);
        return new Catalogue(cards(latest), recent);
    }

    /** Joins the latest run per report onto the list of reports. */
    static List<Card> cards(List<Run> latest) {
        Map<String, Run> byKey = new HashMap<>();
        for (Run r : latest) byKey.put(r.reportKey(), r);
        List<Card> out = new ArrayList<>();
        for (Def d : DEFS) out.add(new Card(d.key(), d.title(), d.description(), d.scope(), byKey.get(d.key())));
        return out;
    }

    static Run run(ResultSet rs) throws SQLException {
        Timestamp at = rs.getTimestamp("generated_at");
        return new Run(rs.getString("report_key"), rs.getString("period"), rs.getString("subject"),
                rs.getString("format"), rs.getString("generated_by_name"), at == null ? null : at.toInstant());
    }
}
