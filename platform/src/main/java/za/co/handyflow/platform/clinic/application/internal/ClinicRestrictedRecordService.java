package za.co.handyflow.platform.clinic.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.application.internal.RestrictedRecordRules.Guard;
import za.co.handyflow.platform.clinic.dto.RestrictedRecordDtos.Session;
import za.co.handyflow.platform.clinic.dto.RestrictedRecordDtos.SessionRow;
import za.co.handyflow.platform.clinic.dto.RestrictedRecordDtos.Status;
import za.co.handyflow.platform.shared.ResourceNotFoundException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Restricted patient records and break-glass sessions (CLINIC-DEC-008, 009). Rules live in {@link RestrictedRecordRules};
 * this only stores and reads. A request that cannot be checked is refused, not allowed: a privacy control that fails open
 * is not a control.
 */
@Slf4j
@Service
public class ClinicRestrictedRecordService {

    public record Restriction(String category, Instant flaggedAt) {}

    private final JdbcTemplate jdbc;

    public ClinicRestrictedRecordService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static Timestamp ts(Instant i) { return Timestamp.from(i); }

    /** The patient a guarded resource belongs to, or empty when it does not exist for this tenant. */
    public Optional<UUID> patientOf(UUID tenantId, Guard g) {
        String sql = switch (g.kind()) {
            case PATIENT -> "SELECT id FROM clinic_patients WHERE tenant_id = ? AND id = ?";
            case CONSULTATION -> "SELECT patient_id FROM clinic_consultations WHERE tenant_id = ? AND id = ?";
            case LAB_RESULT -> "SELECT patient_id FROM clinic_lab_results WHERE tenant_id = ? AND id = ?";
            case APPOINTMENT -> "SELECT patient_id FROM clinic_appointments WHERE tenant_id = ? AND id = ?";
            case PRESCRIPTION -> "SELECT patient_id FROM clinic_prescriptions WHERE tenant_id = ? AND id = ?";
        };
        List<UUID> rows = jdbc.query(sql, (rs, i) -> (UUID) rs.getObject(1), tenantId, g.id());
        return rows.stream().filter(java.util.Objects::nonNull).findFirst();
    }

    @Transactional(readOnly = true)
    public Optional<Restriction> restriction(UUID tenantId, UUID patientId) {
        return jdbc.query("SELECT category, created_at FROM clinic_restricted_records WHERE tenant_id = ? AND patient_id = ? AND released_at IS NULL",
                (rs, i) -> new Restriction(rs.getString("category"), rs.getTimestamp("created_at").toInstant()), tenantId, patientId)
                .stream().findFirst();
    }

    /** The unexpired break-glass session this user has for the patient, if any. */
    @Transactional(readOnly = true)
    public Optional<UUID> activeSession(UUID tenantId, UUID patientId, UUID userId) {
        return jdbc.query("SELECT id FROM clinic_break_glass_sessions WHERE tenant_id = ? AND patient_id = ? AND user_id = ? AND expires_at > ? "
                        + "ORDER BY expires_at DESC LIMIT 1",
                (rs, i) -> (UUID) rs.getObject(1), tenantId, patientId, userId, ts(Instant.now())).stream().findFirst();
    }

    @Transactional(readOnly = true)
    public Instant activeUntil(UUID tenantId, UUID patientId, UUID userId) {
        return jdbc.query("SELECT MAX(expires_at) FROM clinic_break_glass_sessions WHERE tenant_id = ? AND patient_id = ? AND user_id = ? AND expires_at > ?",
                (rs, i) -> rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant(), tenantId, patientId, userId, ts(Instant.now()))
                .stream().filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    @Transactional(readOnly = true)
    public Status status(UUID tenantId, UUID patientId, UUID userId, boolean standingAccess) {
        Optional<Restriction> r = restriction(tenantId, patientId);
        if (r.isEmpty()) return new Status(false, null, null, true, null);
        Instant until = userId == null ? null : activeUntil(tenantId, patientId, userId);
        return new Status(true, r.get().category(), r.get().flaggedAt(), standingAccess || until != null, until);
    }

    @Transactional
    public Status flag(UUID tenantId, UUID patientId, UUID userId, String rawCategory, String rawReason) {
        String category = RestrictedRecordRules.category(rawCategory);
        String reason = RestrictedRecordRules.reason(rawReason);
        requirePatient(tenantId, patientId);
        if (restriction(tenantId, patientId).isPresent()) throw new IllegalStateException("This record is already restricted.");
        jdbc.update("INSERT INTO clinic_restricted_records (id, tenant_id, patient_id, category, reason, flagged_by, created_at) VALUES (?,?,?,?,?,?,?)",
                UUID.randomUUID(), tenantId, patientId, category, reason, userId, ts(Instant.now()));
        log.info("Record restricted patient={} category={} by={}", patientId, category, userId);
        return status(tenantId, patientId, userId, true);
    }

    @Transactional
    public Status release(UUID tenantId, UUID patientId, UUID userId, String rawReason) {
        String reason = RestrictedRecordRules.reason(rawReason);
        int n = jdbc.update("UPDATE clinic_restricted_records SET released_at = ?, released_by = ?, release_reason = ? "
                + "WHERE tenant_id = ? AND patient_id = ? AND released_at IS NULL", ts(Instant.now()), userId, reason, tenantId, patientId);
        if (n == 0) throw new IllegalStateException("This record is not restricted.");
        log.info("Restriction released patient={} by={}", patientId, userId);
        return status(tenantId, patientId, userId, true);
    }

    /** Opens a time-limited session, writes BREAK_GLASS_OPENED and leaves the session unacknowledged for the reviewer. */
    @Transactional
    public Session start(UUID tenantId, UUID patientId, UUID userId, String rawReason, String ip, String userAgent, Integer minutes) {
        String reason = RestrictedRecordRules.reason(rawReason);
        if (restriction(tenantId, patientId).isEmpty()) throw new IllegalStateException("This record is not restricted, so there is nothing to break.");
        Instant now = Instant.now();
        Instant until = now.plusSeconds(60L * RestrictedRecordRules.sessionMinutes(minutes));
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO clinic_break_glass_sessions (id, tenant_id, patient_id, user_id, reason, started_at, expires_at, ip_address, user_agent) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)", id, tenantId, patientId, userId, reason, ts(now), ts(until), trunc(ip, 64), trunc(userAgent, 300));
        audit(tenantId, id, patientId, userId, RestrictedRecordRules.OPENED, "PATIENT", patientId, null);
        log.warn("BREAK GLASS opened patient={} user={} until={}", patientId, userId, until);
        return new Session(id, now, until);
    }

    public void audit(UUID tenantId, UUID sessionId, UUID patientId, UUID userId, String eventType, String resourceType, UUID resourceId, String path) {
        jdbc.update("INSERT INTO clinic_break_glass_audit (id, tenant_id, session_id, patient_id, user_id, event_type, resource_type, resource_id, path, created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?)", UUID.randomUUID(), tenantId, sessionId, patientId, userId, eventType, resourceType, resourceId,
                trunc(path, 300), ts(Instant.now()));
    }

    /** Sessions for the reviewer, newest first. */
    @Transactional(readOnly = true)
    public List<SessionRow> sessions(UUID tenantId, boolean onlyUnacknowledged, int limit) {
        int capped = Math.max(1, Math.min(limit, 200));
        return jdbc.query("""
            SELECT s.id, s.patient_id, p.full_name AS patient_name, s.user_id,
                   NULLIF(TRIM(CONCAT(u.first_name, ' ', u.last_name)), '') AS user_name, s.reason, s.started_at, s.expires_at,
                   (SELECT COUNT(*) FROM clinic_break_glass_audit a WHERE a.session_id = s.id AND a.event_type = 'BREAK_GLASS_VIEWED') AS viewed,
                   (SELECT COUNT(*) FROM clinic_break_glass_audit a WHERE a.session_id = s.id
                       AND a.event_type IN ('BREAK_GLASS_DOCUMENT_PRINTED','BREAK_GLASS_DOCUMENT_EXPORTED')) AS documents,
                   s.acknowledged_at, s.acknowledged_note
            FROM clinic_break_glass_sessions s
            LEFT JOIN clinic_patients p ON p.id = s.patient_id AND p.tenant_id = s.tenant_id
            LEFT JOIN users u ON u.id = s.user_id AND u.tenant_id = s.tenant_id
            WHERE s.tenant_id = ? AND (? = FALSE OR s.acknowledged_at IS NULL)
            ORDER BY s.started_at DESC LIMIT ?""",
                (rs, i) -> new SessionRow((UUID) rs.getObject("id"), (UUID) rs.getObject("patient_id"), rs.getString("patient_name"),
                        (UUID) rs.getObject("user_id"), rs.getString("user_name"), rs.getString("reason"),
                        rs.getTimestamp("started_at").toInstant(), rs.getTimestamp("expires_at").toInstant(),
                        rs.getInt("viewed"), rs.getInt("documents"),
                        rs.getTimestamp("acknowledged_at") == null ? null : rs.getTimestamp("acknowledged_at").toInstant(),
                        rs.getString("acknowledged_note")),
                tenantId, onlyUnacknowledged, capped);
    }

    @Transactional
    public void acknowledge(UUID tenantId, UUID sessionId, UUID userId, String note) {
        String n = note == null ? null : note.trim();
        if (n != null && n.length() > RestrictedRecordRules.MAX_REASON) throw new IllegalArgumentException("The note is limited to " + RestrictedRecordRules.MAX_REASON + " characters.");
        int rows = jdbc.update("UPDATE clinic_break_glass_sessions SET acknowledged_at = ?, acknowledged_by = ?, acknowledged_note = ? "
                + "WHERE tenant_id = ? AND id = ? AND acknowledged_at IS NULL", ts(Instant.now()), userId, n == null || n.isEmpty() ? null : n, tenantId, sessionId);
        if (rows == 0) {
            Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM clinic_break_glass_sessions WHERE tenant_id = ? AND id = ?", Integer.class, tenantId, sessionId);
            if (exists == null || exists == 0) throw new ResourceNotFoundException("Break-glass session", sessionId.toString());
        }
    }

    private void requirePatient(UUID tenantId, UUID patientId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM clinic_patients WHERE tenant_id = ? AND id = ?", Integer.class, tenantId, patientId);
        if (n == null || n == 0) throw new ResourceNotFoundException("Patient", patientId.toString());
    }

    private static String trunc(String s, int max) { return s == null ? null : s.length() <= max ? s : s.substring(0, max); }
}
