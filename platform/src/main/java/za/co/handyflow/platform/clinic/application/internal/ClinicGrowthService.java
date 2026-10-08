package za.co.handyflow.platform.clinic.application.internal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.application.internal.GrowthRules.Point;
import za.co.handyflow.platform.clinic.domain.model.ObservationCode;
import za.co.handyflow.platform.clinic.dto.GrowthDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Growth charts (CLINIC-DEC-013). Measurements always show. Z-scores, percentiles and reference curves appear only when an
 * ACTIVE set (reviewed and approved by two different people) covers that measure and the child's sex; otherwise the chart
 * carries the banner "DATA NOT CLINICALLY APPROVED". Nothing here supplies a reference value.
 */
@Service
public class ClinicGrowthService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final List<Double> Z_LINES = List.of(-3.0, -2.0, -1.0, 0.0, 1.0, 2.0, 3.0);
    private static final List<ObservationCode> CODES = List.of(ObservationCode.WEIGHT, ObservationCode.HEIGHT,
            ObservationCode.HEAD_CIRCUMFERENCE, ObservationCode.BMI);

    private final JdbcTemplate jdbc;

    public ClinicGrowthService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // ---- chart -------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public GrowthChart chart(UUID tenantId, UUID patientId) {
        var p = jdbc.query("SELECT date_of_birth, sex_at_birth FROM clinic_patients WHERE tenant_id = ? AND id = ?",
                (rs, i) -> new Object[]{rs.getDate(1), rs.getString(2)}, tenantId, patientId);
        if (p.isEmpty()) throw new ResourceNotFoundException("Patient", patientId.toString());
        LocalDate dob = p.get(0)[0] == null ? null : ((java.sql.Date) p.get(0)[0]).toLocalDate();
        String sexAtBirth = (String) p.get(0)[1];
        String refSex = "MALE".equals(sexAtBirth) || "FEMALE".equals(sexAtBirth) ? sexAtBirth : null;

        List<String> notes = new ArrayList<>();
        if (dob == null) notes.add("No date of birth on file: measurements are listed but cannot be placed on a chart by age.");
        if (refSex == null) notes.add("Sex at birth is not recorded as male or female: no reference curves can be chosen.");

        List<MeasureChart> out = new ArrayList<>();
        for (ObservationCode code : CODES) {
            List<Measured> measured = new ArrayList<>();
            var set = refSex == null ? null : activeSet(tenantId, code.name(), refSex);
            List<Point> pts = set == null ? List.of() : points(set.id());
            var rows = jdbc.query("SELECT id, taken_at, value_numeric FROM clinic_observations WHERE tenant_id = ? AND patient_id = ? AND code = ? AND status = 'FINAL' ORDER BY taken_at",
                    (rs, i) -> new Object[]{rs.getObject(1), rs.getTimestamp(2), rs.getBigDecimal(3)}, tenantId, patientId, code.name());
            for (Object[] r : rows) {
                Instant at = ((Timestamp) r[1]).toInstant();
                BigDecimal v = (BigDecimal) r[2];
                if (dob == null || v == null) continue;
                LocalDate day = at.atZone(SAST).toLocalDate();
                if (day.isBefore(dob)) continue;
                double age = GrowthRules.ageMonths(dob, day);
                Double z = null, pct = null;
                var lms = GrowthRules.at(pts, age);
                if (lms.isPresent() && v.signum() > 0) { z = GrowthRules.zScore(v.doubleValue(), lms.get()); pct = GrowthRules.percentile(z); }
                measured.add(new Measured((UUID) r[0], at, age, v, z, pct));
            }
            Curves curves = null;
            if (set != null && !pts.isEmpty()) {
                List<CurvePoint> cp = new ArrayList<>();
                for (Point pt : pts) {
                    List<Double> vals = new ArrayList<>();
                    for (double z : Z_LINES) vals.add(GrowthRules.valueAtZ(z, pt));
                    cp.add(new CurvePoint(pt.ageMonths(), vals));
                }
                curves = new Curves(set.id(), set.source(), set.sourceVersion(), Z_LINES, cp);
            }
            out.add(new MeasureChart(code.name(), code.label(), code.unit(), GrowthRules.banner(curves != null), measured, curves));
        }
        Double now = dob == null || dob.isAfter(LocalDate.now(SAST)) ? null : GrowthRules.ageMonths(dob, LocalDate.now(SAST));
        return new GrowthChart(patientId, sexAtBirth, now, notes, out);
    }

    private SetRow activeSet(UUID tenantId, String measure, String sex) {
        var l = jdbc.query(SET_SQL + "AND s.measure = ? AND s.sex = ? AND s.status = 'ACTIVE'", (rs, i) -> setRow(rs), tenantId, measure, sex);
        return l.isEmpty() ? null : l.get(0);
    }

    private List<Point> points(UUID setId) {
        return jdbc.query("SELECT age_months, l_value, m_value, s_value FROM clinic_growth_reference_points WHERE set_id = ? ORDER BY age_months",
                (rs, i) -> new Point(rs.getDouble(1), rs.getDouble(2), rs.getDouble(3), rs.getDouble(4)), setId);
    }

    // ---- reference sets (content administration) --------------------------------------------------------------

    private static final String SET_SQL =
            "SELECT s.id, s.measure, s.sex, s.title, s.source, s.source_version, s.min_age_months, s.max_age_months, s.status, "
          + "(SELECT count(*) FROM clinic_growth_reference_points x WHERE x.set_id = s.id), s.created_by, s.reviewed_by, s.reviewed_at, "
          + "s.approved_by, s.approved_at FROM clinic_growth_reference_sets s WHERE s.tenant_id = ? ";

    private static SetRow setRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp rv = rs.getTimestamp(13), ap = rs.getTimestamp(15);
        return new SetRow((UUID) rs.getObject(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                rs.getBigDecimal(7), rs.getBigDecimal(8), rs.getString(9), rs.getInt(10), (UUID) rs.getObject(11), (UUID) rs.getObject(12),
                rv == null ? null : rv.toInstant(), (UUID) rs.getObject(14), ap == null ? null : ap.toInstant());
    }

    @Transactional(readOnly = true)
    public List<SetRow> sets(UUID tenantId) {
        return jdbc.query(SET_SQL + "ORDER BY s.measure, s.sex, s.created_at DESC", (rs, i) -> setRow(rs), tenantId);
    }

    private SetRow lockedSet(UUID tenantId, UUID id) {
        var l = jdbc.query(SET_SQL + "AND s.id = ? FOR UPDATE OF s", (rs, i) -> setRow(rs), tenantId, id);
        if (l.isEmpty()) throw new ResourceNotFoundException("Growth reference set", id.toString());
        return l.get(0);
    }

    /** Loads a set as DRAFT. Nothing is served to clinicians until it has been reviewed, approved and activated. */
    @Transactional
    public SetRow create(UUID tenantId, UUID userId, CreateSetRequest r) {
        String measure = GrowthRules.measure(r.measure());
        String sex = GrowthRules.sex(r.sex());
        String title = required(r.title(), "Title", 200), source = required(r.source(), "Source", 300), version = required(r.sourceVersion(), "Source version", 60);
        if (r.minAgeMonths() == null || r.maxAgeMonths() == null) throw new IllegalArgumentException("Minimum and maximum age in months are required");
        List<Point> pts = r.points() == null ? List.of() : r.points().stream()
                .map(x -> new Point(x.ageMonths().doubleValue(), x.l().doubleValue(), x.m().doubleValue(), x.s().doubleValue())).toList();
        GrowthRules.requirePoints(pts, r.minAgeMonths().doubleValue(), r.maxAgeMonths().doubleValue());
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO clinic_growth_reference_sets(id, tenant_id, measure, sex, title, source, source_version, min_age_months, max_age_months, created_by) VALUES (?,?,?,?,?,?,?,?,?,?)",
                id, tenantId, measure, sex, title, source, version, r.minAgeMonths(), r.maxAgeMonths(), userId);
        for (var x : r.points())
            jdbc.update("INSERT INTO clinic_growth_reference_points(set_id, age_months, l_value, m_value, s_value) VALUES (?,?,?,?,?)", id, x.ageMonths(), x.l(), x.m(), x.s());
        return lockedSet(tenantId, id);
    }

    private static String required(String v, String what, int max) {
        String t = v == null ? "" : v.trim();
        if (t.isEmpty()) throw new IllegalArgumentException(what + " is required");
        if (t.length() > max) throw new IllegalArgumentException(what + " must be " + max + " characters or fewer");
        return t;
    }

    @Transactional
    public SetRow submitForReview(UUID tenantId, UUID id) {
        SetRow s = lockedSet(tenantId, id);
        GrowthRules.requireMove(s.status(), "CLINICAL_REVIEW");
        jdbc.update("UPDATE clinic_growth_reference_sets SET status = 'CLINICAL_REVIEW' WHERE id = ?", id);
        return lockedSet(tenantId, id);
    }

    /** The reviewer records that the set matches its cited source. Moves CLINICAL_REVIEW to APPROVED. */
    @Transactional
    public SetRow approve(UUID tenantId, UUID userId, UUID id) {
        SetRow s = lockedSet(tenantId, id);
        GrowthRules.requireMove(s.status(), "APPROVED");
        jdbc.update("UPDATE clinic_growth_reference_sets SET status = 'APPROVED', reviewed_by = ?, reviewed_at = now() WHERE id = ?", userId, id);
        return lockedSet(tenantId, id);
    }

    @Transactional
    public SetRow sendBack(UUID tenantId, UUID id) {
        SetRow s = lockedSet(tenantId, id);
        GrowthRules.requireMove(s.status(), "DRAFT");
        jdbc.update("UPDATE clinic_growth_reference_sets SET status = 'DRAFT', reviewed_by = NULL, reviewed_at = NULL WHERE id = ?", id);
        return lockedSet(tenantId, id);
    }

    /** A different qualified person activates the approved set. The set it replaces is retired in the same transaction. */
    @Transactional
    public SetRow activate(UUID tenantId, UUID userId, UUID id) {
        SetRow s = lockedSet(tenantId, id);
        GrowthRules.requireMove(s.status(), "ACTIVE");
        GrowthRules.requireDifferentActivator(s.reviewedBy(), userId);
        jdbc.update("UPDATE clinic_growth_reference_sets SET status = 'RETIRED' WHERE tenant_id = ? AND measure = ? AND sex = ? AND status = 'ACTIVE'",
                tenantId, s.measure(), s.sex());
        jdbc.update("UPDATE clinic_growth_reference_sets SET status = 'ACTIVE', approved_by = ?, approved_at = now() WHERE id = ?", userId, id);
        return lockedSet(tenantId, id);
    }

    @Transactional
    public SetRow retire(UUID tenantId, UUID id) {
        SetRow s = lockedSet(tenantId, id);
        GrowthRules.requireMove(s.status(), "RETIRED");
        jdbc.update("UPDATE clinic_growth_reference_sets SET status = 'RETIRED' WHERE id = ?", id);
        return lockedSet(tenantId, id);
    }
}
