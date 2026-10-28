package za.co.handyflow.platform.recruiter.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.recruiter.domain.model.RecruiterReportRules;
import za.co.handyflow.platform.recruiter.dto.RecruiterCalendarDtos.CalendarInterview;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Interviews between two days, for the calendar. Interviews that were moved or called off are left out. */
@Service
@RequiredArgsConstructor
public class RecruiterCalendarService {

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<CalendarInterview> between(TenantId tenantId, LocalDate from, LocalDate to) {
        Timestamp a = Timestamp.from(RecruiterReportRules.start(from)), b = Timestamp.from(RecruiterReportRules.endExclusive(to));
        return jdbc.query("""
                SELECT i.id, i.application_id, ap.first_name || ' ' || ap.last_name AS candidate, j.title, i.interview_type, i.scheduled_at,
                       i.duration_minutes, i.outcome, i.interviewer_name, i.location
                FROM rec_interviews i JOIN rec_applications a ON a.id = i.application_id
                JOIN rec_applicants ap ON ap.id = a.applicant_id JOIN rec_jobs j ON j.id = a.job_id
                WHERE i.tenant_id = ? AND i.scheduled_at >= ? AND i.scheduled_at < ?
                  AND COALESCE(i.outcome, 'PENDING') NOT IN ('RESCHEDULED', 'CANCELLED')
                ORDER BY i.scheduled_at LIMIT 1000
                """, (rs, n) -> new CalendarInterview(rs.getObject("id", UUID.class), rs.getObject("application_id", UUID.class), rs.getString("candidate"),
                rs.getString("title"), rs.getString("interview_type"), rs.getTimestamp("scheduled_at").toInstant(), rs.getInt("duration_minutes"),
                rs.getString("outcome") == null ? "PENDING" : rs.getString("outcome"), rs.getString("interviewer_name"), rs.getString("location")),
                tenantId.getValue(), a, b);
    }
}
