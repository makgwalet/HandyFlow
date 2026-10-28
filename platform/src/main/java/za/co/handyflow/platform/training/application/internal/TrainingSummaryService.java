package za.co.handyflow.platform.training.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.training.dto.TrainingSummaryDtos.*;

import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/** The Training dashboard's figures, counted in the database so they stay right however many records there are. Every query is filtered to the company. */
@Service
@RequiredArgsConstructor
public class TrainingSummaryService {

    public static final int EXPIRY_WINDOW_DAYS = 30;
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Summary summary(TenantId tenantId, LocalDate today) {
        UUID t = tenantId.getValue();
        long courses = count("SELECT COUNT(*) FROM training_courses WHERE tenant_id = ? AND deleted_at IS NULL AND status = 'ACTIVE'", t);
        long sessions = count("SELECT COUNT(*) FROM training_sessions WHERE tenant_id = ? AND status = 'SCHEDULED'", t);
        long live = count("SELECT COUNT(*) FROM training_enrollments WHERE tenant_id = ? AND status = 'ENROLLED'", t);
        long soon = jdbc.queryForObject("SELECT COUNT(*) FROM training_certificates WHERE tenant_id = ? AND status = 'VALID' AND expiry_date >= ? AND expiry_date <= ?",
                Long.class, t, Date.valueOf(today), Date.valueOf(today.plusDays(EXPIRY_WINDOW_DAYS)));
        long lapsed = jdbc.queryForObject("SELECT COUNT(*) FROM training_certificates WHERE tenant_id = ? AND status = 'VALID' AND expiry_date < ?",
                Long.class, t, Date.valueOf(today));
        List<UpcomingSession> upcoming = jdbc.query(
                "SELECT s.id, c.title, s.start_date, s.end_date, s.capacity, "
                        + "(SELECT COUNT(*) FROM training_enrollments e WHERE e.session_id = s.id AND e.tenant_id = s.tenant_id AND e.status <> 'CANCELLED') AS enrolled "
                        + "FROM training_sessions s JOIN training_courses c ON c.id = s.course_id AND c.tenant_id = s.tenant_id "
                        + "WHERE s.tenant_id = ? AND s.status = 'SCHEDULED' AND s.start_date >= ? ORDER BY s.start_date, s.id LIMIT 5",
                (rs, i) -> new UpcomingSession(rs.getObject("id", UUID.class), rs.getString("title"), rs.getDate("start_date").toLocalDate(), rs.getDate("end_date").toLocalDate(),
                        rs.getLong("enrolled"), (Integer) rs.getObject("capacity")), t, Date.valueOf(today));
        List<ExpiringCertificate> expiring = jdbc.query(
                "SELECT id, employee_name_snapshot, course_title_snapshot, expiry_date FROM training_certificates "
                        + "WHERE tenant_id = ? AND status = 'VALID' AND expiry_date <= ? ORDER BY expiry_date, id LIMIT 10",
                (rs, i) -> {
                    LocalDate d = rs.getDate("expiry_date").toLocalDate();
                    return new ExpiringCertificate(rs.getObject("id", UUID.class), rs.getString("employee_name_snapshot"), rs.getString("course_title_snapshot"), d, ChronoUnit.DAYS.between(today, d));
                }, t, Date.valueOf(today.plusDays(EXPIRY_WINDOW_DAYS)));
        return new Summary(courses, sessions, live, soon, lapsed, upcoming, expiring);
    }

    private long count(String sql, UUID tenant) { return jdbc.queryForObject(sql, Long.class, tenant); }
}
