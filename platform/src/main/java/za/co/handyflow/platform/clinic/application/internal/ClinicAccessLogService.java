package za.co.handyflow.platform.clinic.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Records reads of patient records (S1-6). Fail-open on purpose: if the log write fails the clinician
 * still gets the record (an outage must not block care), and the failure is logged at ERROR so it is noticed.
 */
@Slf4j
@Service
public class ClinicAccessLogService {

    private static final String UUID_RE = "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})";
    private static final Pattern PATIENT      = Pattern.compile("/patients/" + UUID_RE);
    private static final Pattern CONSULTATION = Pattern.compile("/consultations/" + UUID_RE);
    private static final Pattern LAB_RESULT   = Pattern.compile("/lab/results/" + UUID_RE);

    /** What a request path refers to. patientId is null when only a consultation or lab result is named. */
    public record Target(String resourceType, UUID resourceId, UUID patientId) {}

    private final JdbcTemplate jdbc;

    public ClinicAccessLogService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Returns the patient-record target of a read path, or null when the path is not a record read. */
    public static Target classify(String path) {
        Matcher p = PATIENT.matcher(path);
        if (p.find()) {
            UUID id = UUID.fromString(p.group(1));
            return new Target("PATIENT", id, id);
        }
        Matcher l = LAB_RESULT.matcher(path);
        if (l.find()) return new Target("LAB_RESULT", UUID.fromString(l.group(1)), null);
        Matcher c = CONSULTATION.matcher(path);
        if (c.find()) return new Target("CONSULTATION", UUID.fromString(c.group(1)), null);
        return null;
    }

    public void record(UUID tenantId, UUID userId, Target t, String method, String path,
                       int status, String ip, boolean impersonated) {
        try {
            jdbc.update("""
                INSERT INTO clinic_access_log
                    (id, tenant_id, user_id, patient_id, resource_type, resource_id,
                     http_method, path, status_code, ip_address, impersonated, accessed_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)""",
                    UUID.randomUUID(), tenantId, userId, t.patientId(), t.resourceType(), t.resourceId(),
                    method, truncate(path, 300), status, truncate(ip, 64), impersonated,
                    Timestamp.from(Instant.now()));
        } catch (RuntimeException e) {
            log.error("Could not write clinic access log (record was still served): {}", e.getMessage());
        }
    }

    public List<Map<String, Object>> query(UUID tenantId, UUID patientId, UUID userId, int limit) {
        int capped = Math.max(1, Math.min(limit, 500));
        return jdbc.queryForList("""
            SELECT id, user_id, patient_id, resource_type, resource_id, http_method, path,
                   status_code, ip_address, impersonated, accessed_at
            FROM clinic_access_log
            WHERE tenant_id = ?
              AND (?::uuid IS NULL OR patient_id = ?::uuid)
              AND (?::uuid IS NULL OR user_id = ?::uuid)
            ORDER BY accessed_at DESC
            LIMIT ?""", tenantId, patientId, patientId, userId, userId, capped);
    }

    private static String truncate(String s, int max) {
        return s == null ? null : (s.length() <= max ? s : s.substring(0, max));
    }
}
