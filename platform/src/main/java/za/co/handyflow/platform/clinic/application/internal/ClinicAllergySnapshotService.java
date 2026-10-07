package za.co.handyflow.platform.clinic.application.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.domain.repository.ClinicConsultationRepository;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPatientAllergyRepository;
import za.co.handyflow.platform.clinic.dto.AllergySnapshotResponse;
import za.co.handyflow.platform.clinic.dto.AllergySnapshotResponse.Item;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Keeps a copy of the patient's active allergies with the consultation when it is signed. The first capture wins. */
@Service
@RequiredArgsConstructor
public class ClinicAllergySnapshotService {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final ClinicPatientAllergyRepository allergyRepo;
    private final ClinicConsultationRepository consultationRepo;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    @Transactional
    public void capture(TenantId tenantId, UUID consultationId, UUID patientId) {
        List<Item> items = allergyRepo.findByPatient(tenantId, patientId).stream()
                .filter(a -> a.isActive())
                .map(a -> new Item(a.getAllergen(), a.getAllergenType(), a.getSeverity(), a.getReaction())).toList();
        try {
            String doc = json.writeValueAsString(Map.of("capturedAt", Instant.now().toString(), "items", items));
            jdbc.update("UPDATE clinic_consultations SET allergy_snapshot = ?::jsonb WHERE id = ? AND tenant_id = ? AND allergy_snapshot IS NULL",
                    doc, consultationId, tenantId.getValue());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not store the allergy snapshot.", e);
        }
    }

    @Transactional(readOnly = true)
    public AllergySnapshotResponse get(TenantId tenantId, UUID consultationId) {
        consultationRepo.findActiveById(tenantId, consultationId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", consultationId.toString()));
        List<String> rows = jdbc.query("SELECT allergy_snapshot::text FROM clinic_consultations WHERE id = ? AND tenant_id = ?",
                (rs, i) -> rs.getString(1), consultationId, tenantId.getValue());
        if (rows.isEmpty() || rows.get(0) == null) return AllergySnapshotResponse.notCaptured();
        try {
            Map<String, Object> doc = json.readValue(rows.get(0), MAP);
            List<Item> items = json.convertValue(doc.get("items"), new TypeReference<List<Item>>() {});
            return new AllergySnapshotResponse(true, (String) doc.get("capturedAt"), items == null ? List.of() : items);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored allergy snapshot is not valid JSON.", e);
        }
    }
}
