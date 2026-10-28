package za.co.handyflow.platform.recruiter.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.recruiter.domain.model.RecruiterDiversityRules;
import za.co.handyflow.platform.recruiter.domain.model.RecruiterReportRules;
import za.co.handyflow.platform.recruiter.dto.RecruiterReportDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Recruiting reports. Every report is a table with its definition written beside it, filtered to the company, and every one of them reads the same
 * records the screens do. Dates are South African; a range includes the whole of its last day.
 */
@Service
@RequiredArgsConstructor
public class RecruiterReportService {

    private final JdbcTemplate jdbc;

    private static final List<ReportInfo> CATALOGUE = List.of(
            new ReportInfo("vacancies", "Vacancy status", "Every job that is not deleted, as it is today. Days open runs from the day the job was created until today for a live job, and until it was last changed for a closed or filled one.", false),
            new ReportInfo("funnel", "Application funnel", "Applications received in the range, and how many of them entered each stage at any time (from the stage history). Conversion is against applications received.", true),
            new ReportInfo("time-to-hire", "Time to hire and fill", "Time to hire: days from application to being marked hired, for people hired in the range. Time to fill: days from the job being created to its last hire, for jobs whose vacancies were all filled by a hire in the range.", true),
            new ReportInfo("sources", "Source effectiveness", "For applications received in the range, by source: how many were interviewed, reached the offer stage and were hired. Hire rate is hires over applications.", true),
            new ReportInfo("offers", "Offer performance", "Offer letters sent in the range and where they stand today. Acceptance rate is accepted over accepted plus declined; offers still waiting or lapsed are not counted either way.", true),
            new ReportInfo("interviews", "Interview outcomes", "Interviews scheduled in the range by outcome. Pass rate is passed over passed plus failed.", true),
            new ReportInfo("drop-off", "Rejections and withdrawals", "Applications rejected in the range grouped by the reason recorded, and applications the candidate withdrew in the range.", true),
            new ReportInfo("recruiter-performance", "Recruiter activity", "For each person, the stage changes they made in the range: how many in total, and how many moved a candidate into Screening, Interview, Offer, Hired or Rejected. Average days to hire is from application to the moment that person marked the candidate hired.", true),
            new ReportInfo("cost-per-hire", "Cost per hire", "Costs recorded against jobs, dated in the range, and people hired in the range, per job. Cost per hire is the job's costs divided by its hires, and is left empty when nobody was hired. The total line adds up every job.", true,
            new ReportInfo("diversity", "Diversity of applicants", "Applications received in the range, by the voluntary equal-opportunity answers applicants chose to give: how many were interviewed, reached an offer and were hired. The answers are never shown on an application and never used to rank anyone. A group of fewer than 5 is shown as \"<5\", so no one can be picked out.", true));

    public List<ReportInfo> catalogue() { return CATALOGUE; }

    @Transactional(readOnly = true)
    public ReportTable run(TenantId tenantId, String id, LocalDate fromIn, LocalDate toIn, LocalDate today) {
        ReportInfo info = CATALOGUE.stream().filter(r -> r.id().equals(id)).findFirst()
                .orElseThrow(() -> new HandyFlowException("There is no such report.", HttpStatus.NOT_FOUND, "NO_SUCH_REPORT"));
        String problem = RecruiterReportRules.rangeProblem(fromIn, toIn, today);
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "INVALID_RANGE");
        LocalDate to = RecruiterReportRules.toOrDefault(toIn, today);
        LocalDate from = RecruiterReportRules.fromOrDefault(fromIn, toIn, today);
        Timestamp a = Timestamp.from(RecruiterReportRules.start(from)), b = Timestamp.from(RecruiterReportRules.endExclusive(to));
        java.util.UUID t = tenantId.getValue();
        List<String> cols;
        List<List<Object>> rows = new ArrayList<>();
        switch (id) {
            case "vacancies" -> {
                cols = List.of("Job", "Status", "Vacancies", "Hired", "Still to fill", "Applications", "Days open");
                jdbc.query("""
                        SELECT j.title, j.status, j.vacancies,
                               (SELECT COUNT(*) FROM rec_applications x WHERE x.job_id = j.id AND x.stage = 'HIRED') AS hired,
                               (SELECT COUNT(*) FROM rec_applications x WHERE x.job_id = j.id) AS apps,
                               GREATEST(0, FLOOR(EXTRACT(EPOCH FROM (CASE WHEN j.status IN ('OPEN','PAUSED','DRAFT') THEN NOW() ELSE j.updated_at END - j.created_at)) / 86400)) AS days_open
                        FROM rec_jobs j WHERE j.tenant_id = ? AND j.deleted_at IS NULL
                        ORDER BY j.created_at DESC LIMIT 500
                        """, rs -> {
                    int vac = rs.getInt("vacancies"), hired = rs.getInt("hired");
                    rows.add(List.of(rs.getString("title"), rs.getString("status"), vac, hired, Math.max(0, vac - hired), rs.getInt("apps"), rs.getLong("days_open")));
                }, t);
            }
            case "funnel" -> {
                cols = List.of("Stage", "Applications", "Conversion from received (%)");
                long received = count("SELECT COUNT(*) FROM rec_applications WHERE tenant_id = ? AND applied_at >= ? AND applied_at < ?", t, a, b);
                rows.add(List.of("Received", received, received == 0 ? "" : (Object) 100.0));
                for (String stage : List.of("SCREENING", "INTERVIEW", "ASSESSMENT", "OFFER", "HIRED")) {
                    long n = count("""
                            SELECT COUNT(DISTINCT x.id) FROM rec_applications x JOIN rec_stage_history h ON h.application_id = x.id AND h.to_stage = ?
                            WHERE x.tenant_id = ? AND x.applied_at >= ? AND x.applied_at < ?
                            """, stage, t, a, b);
                    Double pct = RecruiterReportRules.percent(n, received);
                    rows.add(List.of(label(stage), n, pct == null ? "" : (Object) pct));
                }
            }
            case "time-to-hire" -> {
                cols = List.of("Measure", "Value");
                List<Double> days = jdbc.query("SELECT EXTRACT(EPOCH FROM (hired_at - applied_at)) / 86400 FROM rec_applications "
                        + "WHERE tenant_id = ? AND stage = 'HIRED' AND hired_at >= ? AND hired_at < ?", (rs, i) -> rs.getDouble(1), t, a, b);
                rows.add(List.of("People hired", days.size()));
                rows.add(List.of("Average days to hire", days.isEmpty() ? "" : (Object) round1(days.stream().mapToDouble(Double::doubleValue).average().orElse(0))));
                Double med = RecruiterReportRules.median(days);
                rows.add(List.of("Median days to hire", med == null ? "" : (Object) round1(med)));
                rows.add(List.of("Fastest (days)", days.isEmpty() ? "" : (Object) round1(days.stream().min(Double::compare).get())));
                rows.add(List.of("Slowest (days)", days.isEmpty() ? "" : (Object) round1(days.stream().max(Double::compare).get())));
                List<Double> fill = jdbc.query("""
                        SELECT EXTRACT(EPOCH FROM (h.last_hire - j.created_at)) / 86400
                        FROM rec_jobs j JOIN (SELECT job_id, COUNT(*) AS hires, MAX(hired_at) AS last_hire FROM rec_applications WHERE stage = 'HIRED' GROUP BY job_id) h ON h.job_id = j.id
                        WHERE j.tenant_id = ? AND j.deleted_at IS NULL AND h.hires >= j.vacancies AND h.last_hire >= ? AND h.last_hire < ?
                        """, (rs, i) -> rs.getDouble(1), t, a, b);
                rows.add(List.of("Jobs filled", fill.size()));
                rows.add(List.of("Average days to fill", fill.isEmpty() ? "" : (Object) round1(fill.stream().mapToDouble(Double::doubleValue).average().orElse(0))));
            }
            case "sources" -> {
                cols = List.of("Source", "Applications", "Interviewed", "Reached offer", "Hired", "Hire rate (%)");
                jdbc.query("""
                        SELECT x.source,
                               COUNT(*) AS apps,
                               COUNT(*) FILTER (WHERE EXISTS (SELECT 1 FROM rec_interviews i WHERE i.application_id = x.id)) AS interviewed,
                               COUNT(*) FILTER (WHERE EXISTS (SELECT 1 FROM rec_stage_history h WHERE h.application_id = x.id AND h.to_stage = 'OFFER')) AS offered,
                               COUNT(*) FILTER (WHERE x.stage = 'HIRED') AS hired
                        FROM rec_applications x WHERE x.tenant_id = ? AND x.applied_at >= ? AND x.applied_at < ?
                        GROUP BY x.source ORDER BY apps DESC
                        """, rs -> {
                    long apps = rs.getLong("apps"), hired = rs.getLong("hired");
                    Double rate = RecruiterReportRules.percent(hired, apps);
                    rows.add(List.of(rs.getString("source"), apps, rs.getLong("interviewed"), rs.getLong("offered"), hired, rate == null ? "" : (Object) rate));
                }, t, a, b);
            }
            case "offers" -> {
                cols = List.of("Measure", "Value");
                Map<String, Object> m = jdbc.queryForMap("""
                        SELECT COUNT(*) AS sent,
                               COUNT(*) FILTER (WHERE offer_response = 'ACCEPTED') AS accepted,
                               COUNT(*) FILTER (WHERE offer_response = 'DECLINED') AS declined,
                               COUNT(*) FILTER (WHERE offer_response = 'WITHDRAWN') AS withdrawn,
                               COUNT(*) FILTER (WHERE offer_response = 'NONE' AND offer_expires_on < ?) AS lapsed,
                               COUNT(*) FILTER (WHERE offer_response = 'NONE' AND (offer_expires_on IS NULL OR offer_expires_on >= ?)) AS waiting
                        FROM rec_applications WHERE tenant_id = ? AND offer_letter_sent_at >= ? AND offer_letter_sent_at < ?
                        """, java.sql.Date.valueOf(today), java.sql.Date.valueOf(today), t, a, b);
                long sent = ((Number) m.get("sent")).longValue(), acc = ((Number) m.get("accepted")).longValue(), dec = ((Number) m.get("declined")).longValue();
                rows.add(List.of("Offers sent", sent));
                rows.add(List.of("Accepted", acc));
                rows.add(List.of("Declined", dec));
                rows.add(List.of("Withdrawn by the company", ((Number) m.get("withdrawn")).longValue()));
                rows.add(List.of("Lapsed without an answer", ((Number) m.get("lapsed")).longValue()));
                rows.add(List.of("Waiting for an answer", ((Number) m.get("waiting")).longValue()));
                Double rate = RecruiterReportRules.percent(acc, acc + dec);
                rows.add(List.of("Acceptance rate (%)", rate == null ? "" : (Object) rate));
            }
            case "interviews" -> {
                cols = List.of("Outcome", "Interviews");
                long pass = 0, fail = 0;
                for (String o : List.of("PENDING", "PASSED", "FAILED", "NO_SHOW", "CANCELLED", "RESCHEDULED")) {
                    long n = count("SELECT COUNT(*) FROM rec_interviews WHERE tenant_id = ? AND outcome = ? AND scheduled_at >= ? AND scheduled_at < ?", t, o, a, b);
                    if ("PASSED".equals(o)) pass = n;
                    if ("FAILED".equals(o)) fail = n;
                    rows.add(List.of(label(o), n));
                }
                Double rate = RecruiterReportRules.percent(pass, pass + fail);
                rows.add(List.of("Pass rate (%)", rate == null ? "" : (Object) rate));
            }
            case "drop-off" -> {
                cols = List.of("Reason", "Applications");
                jdbc.query("""
                        SELECT COALESCE(NULLIF(TRIM(rejection_reason), ''), 'No reason recorded') AS reason, COUNT(*) AS n
                        FROM rec_applications WHERE tenant_id = ? AND stage = 'REJECTED' AND stage_changed_at >= ? AND stage_changed_at < ?
                        GROUP BY 1 ORDER BY n DESC, 1 LIMIT 200
                        """, rs -> { rows.add(List.of("Rejected: " + rs.getString("reason"), rs.getLong("n"))); }, t, a, b);
                rows.add(List.of("Withdrawn by the candidate", count("SELECT COUNT(*) FROM rec_applications WHERE tenant_id = ? AND stage = 'WITHDRAWN' AND stage_changed_at >= ? AND stage_changed_at < ?", t, a, b)));
            }
            case "recruiter-performance" -> {
                cols = List.of("Person", "Stage changes", "To screening", "To interview", "To offer", "Hired", "Rejected", "Average days to hire");
                jdbc.query("""
                        SELECT COALESCE(NULLIF(TRIM(h.changed_by_name), ''), 'Not recorded') AS who,
                               COUNT(*) AS moves,
                               COUNT(*) FILTER (WHERE h.to_stage = 'SCREENING') AS screening,
                               COUNT(*) FILTER (WHERE h.to_stage = 'INTERVIEW') AS interview,
                               COUNT(*) FILTER (WHERE h.to_stage = 'OFFER') AS offer,
                               COUNT(*) FILTER (WHERE h.to_stage = 'HIRED') AS hired,
                               COUNT(*) FILTER (WHERE h.to_stage = 'REJECTED') AS rejected,
                               AVG(EXTRACT(EPOCH FROM (h.created_at - a.applied_at)) / 86400) FILTER (WHERE h.to_stage = 'HIRED') AS days_to_hire
                        FROM rec_stage_history h JOIN rec_applications a ON a.id = h.application_id
                        WHERE a.tenant_id = ? AND h.from_stage IS NOT NULL AND h.created_at >= ? AND h.created_at < ?
                        GROUP BY 1 ORDER BY moves DESC, 1 LIMIT 100
                        """, rs -> {
                    double d = rs.getDouble("days_to_hire");
                    boolean noHires = rs.wasNull();
                    rows.add(List.of(rs.getString("who"), rs.getLong("moves"), rs.getLong("screening"), rs.getLong("interview"), rs.getLong("offer"),
                            rs.getLong("hired"), rs.getLong("rejected"), noHires ? "" : (Object) round1(d)));
                }, t, a, b);
            }
            case "cost-per-hire" -> {
                cols = List.of("Job", "Hires", "Total cost (R)", "Cost per hire (R)");
                java.math.BigDecimal all = java.math.BigDecimal.ZERO;
                long allHires = 0;
                List<Object[]> perJob = jdbc.query("""
                        SELECT j.title,
                               (SELECT COUNT(*) FROM rec_applications x WHERE x.job_id = j.id AND x.stage = 'HIRED' AND x.hired_at >= ? AND x.hired_at < ?) AS hires,
                               (SELECT COALESCE(SUM(c.amount), 0) FROM rec_job_costs c WHERE c.job_id = j.id AND c.incurred_on >= ? AND c.incurred_on <= ?) AS cost
                        FROM rec_jobs j WHERE j.tenant_id = ? AND j.deleted_at IS NULL
                        ORDER BY j.title LIMIT 500
                        """, (rs, i) -> new Object[]{rs.getString("title"), rs.getLong("hires"), rs.getBigDecimal("cost")}, a, b, java.sql.Date.valueOf(from), java.sql.Date.valueOf(to), t);
                for (Object[] r : perJob) {
                    long hires = (Long) r[1];
                    java.math.BigDecimal cost = (java.math.BigDecimal) r[2];
                    if (hires == 0 && cost.signum() == 0) continue;
                    java.math.BigDecimal per = za.co.handyflow.platform.recruiter.domain.model.RecruiterCostRules.costPerHire(cost, hires);
                    rows.add(List.of(r[0], hires, cost, per == null ? "" : (Object) per));
                    all = all.add(cost);
                    allHires += hires;
                }
                if (!rows.isEmpty()) {
                    java.math.BigDecimal per = za.co.handyflow.platform.recruiter.domain.model.RecruiterCostRules.costPerHire(all, allHires);
                    rows.add(List.of("All jobs", allHires, all, per == null ? "" : (Object) per));
                }
            }
            case "diversity" -> {
                cols = List.of("Group", "Applications", "Interviewed", "Reached offer", "Hired");
                String[][] dimensions = {{"Gender", "gender"}, {"Race", "race"}, {"Disability", "disability"}};
                for (String[] dim : dimensions) {
                    java.util.Map<String, String> words = "gender".equals(dim[1]) ? RecruiterDiversityRules.GENDERS : "race".equals(dim[1]) ? RecruiterDiversityRules.RACES : RecruiterDiversityRules.DISABILITY;
                    // dim[1] comes from the fixed list above, never from the request.
                    jdbc.query("SELECT d." + dim[1] + " AS grp, COUNT(*) AS apps, "
                            + "COUNT(*) FILTER (WHERE EXISTS (SELECT 1 FROM rec_interviews i WHERE i.application_id = x.id)) AS interviewed, "
                            + "COUNT(*) FILTER (WHERE EXISTS (SELECT 1 FROM rec_stage_history h WHERE h.application_id = x.id AND h.to_stage = 'OFFER')) AS offered, "
                            + "COUNT(*) FILTER (WHERE x.stage = 'HIRED') AS hired "
                            + "FROM rec_applications x LEFT JOIN rec_diversity d ON d.application_id = x.id "
                            + "WHERE x.tenant_id = ? AND x.applied_at >= ? AND x.applied_at < ? GROUP BY 1 ORDER BY 1", rs -> {
                        String g = rs.getString("grp");
                        Object[] c = RecruiterDiversityRules.row(rs.getLong("apps"), rs.getLong("interviewed"), rs.getLong("offered"), rs.getLong("hired"));
                        rows.add(List.of(dim[0] + ": " + (g == null ? "Did not answer" : words.getOrDefault(g, g)), c[0], c[1], c[2], c[3]));
                    }, t, a, b);
                }
            }
            default -> throw new HandyFlowException("There is no such report.", HttpStatus.NOT_FOUND, "NO_SUCH_REPORT");
        }
        return new ReportTable(info.id(), info.title(), info.definition(), info.usesDateRange() ? from : null, info.usesDateRange() ? to : null, cols, rows);
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }

    private static String label(String stage) {
        String s = stage.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
