package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.domain.model.ClinicPrescription;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPrescriptionRepository;
import za.co.handyflow.platform.clinic.dto.FillDtos.FillRequest;
import za.co.handyflow.platform.clinic.dto.FillDtos.FillResponse;
import za.co.handyflow.platform.clinic.dto.PrescriptionResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.UserContext;

import java.util.List;
import java.util.UUID;

/**
 * Fills of a prescription: the original supply and each authorised repeat. Every fill is logged with who recorded it.
 * The prescription row is locked while a fill is recorded, so the authorised number cannot be exceeded by two people
 * at once. Recording a fill does not change what was prescribed.
 */
@Service
@RequiredArgsConstructor
public class ClinicDispensingService {

    private final ClinicPrescriptionRepository prescriptionRepo;
    private final JdbcTemplate jdbc;

    @Transactional
    public FillResponse recordFill(TenantId tenantId, UUID prescriptionId, FillRequest req) {
        ClinicPrescription p = prescriptionRepo.findForUpdate(tenantId, prescriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription", prescriptionId.toString()));
        Integer quantity = req == null ? null : req.quantity();
        if (quantity != null && quantity <= 0) throw new IllegalArgumentException("Quantity must be above zero.");
        String note = req == null || req.note() == null || req.note().isBlank() ? null : req.note().trim();

        int number = p.recordFill();          // throws when every authorised fill is used
        prescriptionRepo.save(p);
        UUID id = UUID.randomUUID();
        UUID by = currentUserOrNull();
        jdbc.update("INSERT INTO clinic_prescription_fills (id, tenant_id, prescription_id, fill_number, quantity, dispensed_by, note) VALUES (?,?,?,?,?,?,?)",
                id, tenantId.getValue(), prescriptionId, number, quantity != null ? quantity : p.getQuantity(), by, note);
        return new FillResponse(id, prescriptionId, number, quantity != null ? quantity : p.getQuantity(), by, note, java.time.Instant.now());
    }

    @Transactional(readOnly = true)
    public List<FillResponse> fills(TenantId tenantId, UUID prescriptionId) {
        prescriptionRepo.findById(prescriptionId).filter(p -> p.getTenantId().equals(tenantId.getValue()))
                .orElseThrow(() -> new ResourceNotFoundException("Prescription", prescriptionId.toString()));
        return jdbc.query("SELECT id, fill_number, quantity, dispensed_by, note, created_at FROM clinic_prescription_fills WHERE prescription_id = ? AND tenant_id = ? ORDER BY fill_number",
                (rs, i) -> new FillResponse((UUID) rs.getObject(1), prescriptionId, rs.getInt(2), (Integer) rs.getObject(3),
                        (UUID) rs.getObject(4), rs.getString(5), rs.getTimestamp(6).toInstant()), prescriptionId, tenantId.getValue());
    }

    private static UUID currentUserOrNull() {
        try { return UserContext.getCurrentUserId(); } catch (RuntimeException e) { return null; }
    }
}
