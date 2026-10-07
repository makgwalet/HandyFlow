package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPatientRepository;
import za.co.handyflow.platform.clinic.dto.PatientNoteDtos.CreateNoteRequest;
import za.co.handyflow.platform.clinic.dto.PatientNoteDtos.NoteResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/** Sticky notes and alerts on a patient's file. Notes are resolved, never edited or deleted. */
@Service
@RequiredArgsConstructor
public class ClinicPatientNoteService {

    private static final String COLS = "id, kind, severity, body, created_by, created_at, resolved_at, resolved_by";

    private final JdbcTemplate jdbc;
    private final ClinicPatientRepository patientRepo;

    /** Open notes and alerts first (alerts, most severe first, then newest); with includeResolved the resolved ones follow. */
    @Transactional(readOnly = true)
    public List<NoteResponse> list(TenantId t, UUID patientId, boolean includeResolved) {
        requirePatient(t, patientId);
        return jdbc.query("SELECT " + COLS + " FROM clinic_patient_notes WHERE tenant_id = ? AND patient_id = ? "
                        + (includeResolved ? "" : "AND resolved_at IS NULL ")
                        + "ORDER BY (resolved_at IS NOT NULL), (kind = 'ALERT') DESC, "
                        + "CASE severity WHEN 'CRITICAL' THEN 0 WHEN 'WARNING' THEN 1 ELSE 2 END, created_at DESC",
                (rs, i) -> map(rs), t.getValue(), patientId);
    }

    @Transactional
    public NoteResponse create(TenantId t, UUID patientId, CreateNoteRequest req) {
        requirePatient(t, patientId);
        String kind = PatientNoteRules.kind(req == null ? null : req.kind());
        String severity = PatientNoteRules.severity(kind, req.severity());
        String body = PatientNoteRules.body(req.body());
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO clinic_patient_notes (id, tenant_id, patient_id, kind, severity, body, created_by) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)", id, t.getValue(), patientId, kind, severity, body, currentUserIdOrNull());
        return jdbc.queryForObject("SELECT " + COLS + " FROM clinic_patient_notes WHERE id = ?", (rs, i) -> map(rs), id);
    }

    /** Resolving an already-resolved note changes nothing. */
    @Transactional
    public NoteResponse resolve(TenantId t, UUID patientId, UUID noteId) {
        int n = jdbc.update("UPDATE clinic_patient_notes SET resolved_at = now(), resolved_by = ? "
                        + "WHERE id = ? AND tenant_id = ? AND patient_id = ? AND resolved_at IS NULL",
                currentUserIdOrNull(), noteId, t.getValue(), patientId);
        List<NoteResponse> rows = jdbc.query("SELECT " + COLS + " FROM clinic_patient_notes WHERE id = ? AND tenant_id = ? AND patient_id = ?",
                (rs, i) -> map(rs), noteId, t.getValue(), patientId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Note", noteId.toString());
        return rows.get(0);
    }

    private void requirePatient(TenantId t, UUID patientId) {
        patientRepo.findActiveById(t, patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId.toString()));
    }

    private static NoteResponse map(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp created = rs.getTimestamp("created_at"), resolved = rs.getTimestamp("resolved_at");
        return new NoteResponse(rs.getObject("id", UUID.class), rs.getString("kind"), rs.getString("severity"), rs.getString("body"),
                rs.getObject("created_by", UUID.class), created == null ? null : created.toInstant(),
                resolved == null ? null : resolved.toInstant(), rs.getObject("resolved_by", UUID.class));
    }

    private static UUID currentUserIdOrNull() {
        try {
            return za.co.handyflow.platform.shared.UserContext.getCurrentUserId();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
