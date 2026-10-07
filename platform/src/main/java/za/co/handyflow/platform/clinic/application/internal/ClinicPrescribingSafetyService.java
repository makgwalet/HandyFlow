package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPatientAllergyRepository;
import za.co.handyflow.platform.clinic.dto.AllergyCheckResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Safety prompts shown while prescribing. Prompts only: the prescriber decides. */
@Service
@RequiredArgsConstructor
public class ClinicPrescribingSafetyService {

    private final ClinicPatientAllergyRepository allergyRepo;

    /** Active recorded allergies whose name matches the medicine name (see {@link AllergyMatcher} for the limits). */
    @Transactional(readOnly = true)
    public List<AllergyCheckResponse.Alert> allergyAlerts(TenantId tenantId, UUID patientId, String medicineName) {
        List<AllergyMatcher.Fact> facts = allergyRepo.findByPatient(tenantId, patientId).stream()
                .filter(a -> a.isActive())
                .map(a -> new AllergyMatcher.Fact(a.getAllergen(), a.getAllergenType(), a.getSeverity(), a.getReaction()))
                .toList();
        return AllergyMatcher.match(medicineName, facts).stream()
                .map(m -> new AllergyCheckResponse.Alert(m.allergen(), m.severity(), m.reaction())).toList();
    }

    public static String summary(List<AllergyCheckResponse.Alert> alerts) {
        return String.join("; ", alerts.stream().map(a ->
                a.allergen() + (a.severity() == null || a.severity().isBlank() ? "" : " (" + a.severity().toLowerCase(java.util.Locale.ROOT) + ")")).toList());
    }
}
