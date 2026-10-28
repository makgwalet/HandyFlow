package za.co.handyflow.platform.recruiter.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.recruiter.domain.model.RecruiterDashboardRules;
import za.co.handyflow.platform.recruiter.dto.RecruiterDashboardDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The Recruiter dashboard: every figure comes from the same records the screens and reports read, filtered to the company. */
@Service
@RequiredArgsConstructor
public class RecruiterDashboardService {

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Dashboard dashboard(TenantId tenantId, int days, Instant now) {
        UUID t = tenantId.getValue();
        Timestamp from = Timestamp.from(RecruiterDashboardRules.periodStart(now, days));
        Timestamp prev = Timestamp.from(RecruiterDashboardRules.previousStart(now, days));
        Timestamp end = Timestamp.from(now);

        long activeJobs = count("SELECT COUNT(*) FROM rec_jobs WHERE tenant_id = ? AND deleted_at IS NULL AND status = 'OPEN'", t);

        Metric applications = metric(count("SELECT COUNT(*) FROM rec_applications WHERE tenant_id = ? AND applied_at >= ? AND applied_at < ?", t, from, end),
                count("SELECT COUNT(*) FROM rec_applications WHERE tenant_id = ? AND applied_at >= ? AND applied_at < ?", t, prev, from));
        String interviewSql = "SELECT COUNT(*) FROM rec_interviews WHERE tenant_id = ? AND scheduled_at >= ? AND scheduled_at < ? AND COALESCE(outcome,'PENDING') <> 'RESCHEDULED'";
        Metric interviews = metric(count(interviewSql, t, from, end), count(interviewSql, t, prev, from));
        String offerSql = "SELECT COUNT(*) FROM rec_applications WHERE tenant_id = ? AND offer_letter_sent_at >= ? AND offer_letter_sent_at < ?";
        Metric offers = metric(count(offerSql, t, from, end), count(offerSql, t, prev, from));
        String hireSql = "SELECT COUNT(*) FROM rec_applications WHERE tenant_id = ? AND stage = 'HIRED' AND hired_at >= ? AND hired_at < ?";
        Metric hires = metric(count(hireSql, t, from, end), count(hireSql, t, prev, from));
        Metric timeToHire = new Metric(avgDays(t, from, end), avgDays(t, prev, from), null);
        timeToHire = new Metric(timeToHire.value(), timeToHire.previous(), RecruiterDashboardRules.changePercent(timeToHire.value(), timeToHire.previous()));

        long received = applications.value().longValue();
        List<StageCount> funnel = new java.util.ArrayList<>();
        funnel.add(new StageCount("APPLIED", received));
        for (String stage : List.of("SCREENING", "INTERVIEW", "OFFER", "HIRED")) {
            funnel.add(new StageCount(stage, count("""
                    SELECT COUNT(DISTINCT x.id) FROM rec_applications x JOIN rec_stage_history h ON h.application_id = x.id AND h.to_stage = ?
                    WHERE x.tenant_id = ? AND x.applied_at >= ? AND x.applied_at < ?
                    """, stage, t, from, end)));
        }

        List<TopJob> topJobs = jdbc.query("""
                SELECT j.id, j.title, COUNT(a.id) AS apps, COUNT(a.id) FILTER (WHERE a.stage = 'HIRED') AS hires
                FROM rec_jobs j JOIN rec_applications a ON a.job_id = j.id AND a.applied_at >= ? AND a.applied_at < ?
                WHERE j.tenant_id = ? AND j.deleted_at IS NULL
                GROUP BY j.id, j.title ORDER BY apps DESC, j.title LIMIT 5
                """, (rs, i) -> new TopJob(rs.getObject("id", UUID.class), rs.getString("title"), rs.getLong("apps"), rs.getLong("hires")), from, end, t);

        List<SourceCount> sources = jdbc.query("""
                SELECT COALESCE(source, 'OTHER') AS source, COUNT(*) AS n FROM rec_applications
                WHERE tenant_id = ? AND applied_at >= ? AND applied_at < ? GROUP BY 1 ORDER BY n DESC
                """, (rs, i) -> new SourceCount(rs.getString("source"), rs.getLong("n")), t, from, end);

        List<UpcomingInterview> upcoming = jdbc.query("""
                SELECT i.application_id, ap.first_name || ' ' || ap.last_name AS candidate, j.title, i.interview_type, i.scheduled_at
                FROM rec_interviews i JOIN rec_applications a ON a.id = i.application_id
                JOIN rec_applicants ap ON ap.id = a.applicant_id JOIN rec_jobs j ON j.id = a.job_id
                WHERE i.tenant_id = ? AND i.scheduled_at >= ? AND COALESCE(i.outcome,'PENDING') = 'PENDING'
                ORDER BY i.scheduled_at LIMIT 5
                """, (rs, i) -> new UpcomingInterview(rs.getObject("application_id", UUID.class), rs.getString("candidate"), rs.getString("title"),
                rs.getString("interview_type"), rs.getTimestamp("scheduled_at").toInstant()), t, end);

        List<Activity> activity = jdbc.query("""
                SELECT * FROM (
                    SELECT a.applied_at AS at, 'APPLIED' AS kind, a.id AS application_id, ap.first_name || ' ' || ap.last_name AS candidate, j.title, 'APPLIED' AS stage
                    FROM rec_applications a JOIN rec_applicants ap ON ap.id = a.applicant_id JOIN rec_jobs j ON j.id = a.job_id WHERE a.tenant_id = ?
                    UNION ALL
                    SELECT h.created_at, 'STAGE', a.id, ap.first_name || ' ' || ap.last_name, j.title, h.to_stage
                    FROM rec_stage_history h JOIN rec_applications a ON a.id = h.application_id
                    JOIN rec_applicants ap ON ap.id = a.applicant_id JOIN rec_jobs j ON j.id = a.job_id
                    WHERE a.tenant_id = ? AND h.from_stage IS NOT NULL
                ) e ORDER BY at DESC LIMIT 8
                """, (rs, i) -> new Activity(rs.getTimestamp("at").toInstant(), rs.getString("kind"), rs.getObject("application_id", UUID.class),
                rs.getString("candidate"), rs.getString("title"), rs.getString("stage")), t, t);

        return new Dashboard(days, activeJobs, applications, interviews, offers, hires, timeToHire, funnel, topJobs, sources, upcoming, activity);
    }

    private Metric metric(long current, long previous) {
        return new Metric((double) current, (double) previous, RecruiterDashboardRules.changePercent((double) current, (double) previous));
    }

    private Double avgDays(UUID t, Timestamp from, Timestamp to) {
        Double d = jdbc.queryForObject("SELECT AVG(EXTRACT(EPOCH FROM (hired_at - applied_at)) / 86400) FROM rec_applications WHERE tenant_id = ? AND stage = 'HIRED' AND hired_at >= ? AND hired_at < ?",
                Double.class, t, from, to);
        return d == null ? null : Math.round(d * 10.0) / 10.0;
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }
}
