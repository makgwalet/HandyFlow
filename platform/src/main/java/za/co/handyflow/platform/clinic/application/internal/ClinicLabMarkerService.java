package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.application.internal.LabMarkerRules.Input;
import za.co.handyflow.platform.clinic.domain.repository.ClinicLabResultRepository;
import za.co.handyflow.platform.clinic.dto.lab.LabMarkerDtos.CriticalLabItem;
import za.co.handyflow.platform.clinic.dto.lab.LabMarkerDtos.SaveMarkersRequest;
import za.co.handyflow.platform.clinic.dto.lab.LabResultResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Markers typed in from a lab report, with each one flagged against the lab's OWN reference range and critical limits,
 * and the queue of unreviewed results that contain a critical marker. Reviewing a result takes it out of the queue.
 */
@Service
@RequiredArgsConstructor
public class ClinicLabMarkerService {

    private final ClinicLabResultRepository labRepo;
    private final JdbcTemplate jdbc;

    @Transactional
    public LabResultResponse saveMarkers(TenantId t, UUID resultId, SaveMarkersRequest req) {
        var result = labRepo.findByIdAndTenant(t, resultId)
                .orElseThrow(() -> new ResourceNotFoundException("LabResult", resultId.toString()));
        var outcome = LabMarkerRules.evaluate(req.markers().stream()
                .map(m -> new Input(m.marker(), m.value(), m.unit(), m.refLow(), m.refHigh(), m.criticalLow(), m.criticalHigh(), m.flag()))
                .toList());
        result.recordMarkers(outcome.markers().isEmpty() ? null : outcome.json(), outcome.anyAbnormal(), outcome.anyCritical(), currentUserIdOrNull());
        labRepo.save(result);
        return new LabResultResponse(
                result.getId(), result.getPatientId(), result.getConsultationId(),
                result.getSource(), result.getLabReference(), result.getCollectedAt(), result.getReceivedAt(),
                result.getPdfUrl(), result.getPdfFilename(), result.getStatus(),
                result.getPatientNameRaw(), result.getParsedMarkersJson(), result.getInterpretation(),
                result.isNotified(), result.getCreatedAt(), result.isHasAbnormal(), result.isHasCritical());
    }

    /** Unreviewed results with a critical marker, oldest first, so the longest-waiting one is at the top. */
    @Transactional(readOnly = true)
    public List<CriticalLabItem> criticalQueue(TenantId t) {
        return jdbc.query("SELECT r.id, r.patient_id, p.full_name AS patient_name, r.patient_name_raw, r.lab_reference, r.received_at, "
                        + "(SELECT string_agg(e->>'marker', ', ') FROM jsonb_array_elements(COALESCE(r.parsed_markers, '[]'::jsonb)) e "
                        + " WHERE e->>'flag' = 'CRITICAL') AS critical_markers "
                        + "FROM clinic_lab_results r LEFT JOIN clinic_patients p ON p.id = r.patient_id "
                        + "WHERE r.tenant_id = ? AND r.has_critical AND r.status = 'UNREVIEWED' ORDER BY r.received_at",
                (rs, i) -> new CriticalLabItem(rs.getObject("id", UUID.class), rs.getObject("patient_id", UUID.class),
                        rs.getString("patient_name"), rs.getString("patient_name_raw"), rs.getString("lab_reference"),
                        rs.getTimestamp("received_at").toInstant(), rs.getString("critical_markers")),
                t.getValue());
    }

    private static UUID currentUserIdOrNull() {
        try {
            return za.co.handyflow.platform.shared.UserContext.getCurrentUserId();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
