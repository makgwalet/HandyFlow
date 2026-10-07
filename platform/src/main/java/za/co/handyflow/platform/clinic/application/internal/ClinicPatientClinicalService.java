package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.domain.model.*;
import za.co.handyflow.platform.clinic.domain.repository.*;
import za.co.handyflow.platform.clinic.dto.PatientClinicalDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.UserContext;

import java.util.*;

/**
 * Structured allergies, conditions and medications for a patient (Sprint 1, V339).
 *
 * These tables are the source of truth. The text[] columns on clinic_patients are kept
 * as a denormalised mirror (names of current rows) so existing screens and PDFs keep working.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicPatientClinicalService {

    static final Set<String> ALLERGEN_TYPES = Set.of("DRUG", "FOOD", "ENVIRONMENT", "OTHER", "UNKNOWN");
    static final Set<String> SEVERITIES = Set.of("MILD", "MODERATE", "SEVERE", "LIFE_THREATENING");
    static final Set<String> ALLERGY_STATUSES = Set.of("ACTIVE", "RESOLVED", "ENTERED_IN_ERROR");
    static final Set<String> CONDITION_STATUSES = Set.of("ACTIVE", "CONTROLLED", "RESOLVED", "ENTERED_IN_ERROR");
    static final Set<String> MED_STATUSES = Set.of("ACTIVE", "STOPPED", "COMPLETED", "ENTERED_IN_ERROR");
    static final Set<String> MED_SOURCES = Set.of("PATIENT_REPORTED", "PRESCRIBED_HERE", "EXTERNAL");

    private final ClinicPatientRepository           patientRepo;
    private final ClinicPatientAllergyRepository    allergyRepo;
    private final ClinicPatientConditionRepository  conditionRepo;
    private final ClinicPatientMedicationRepository medicationRepo;

    // ── Allergies ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AllergyResponse> listAllergies(TenantId t, UUID patientId, boolean includeInactive) {
        requirePatient(t, patientId);
        return allergyRepo.findByPatient(t, patientId).stream()
                .filter(a -> includeInactive || a.isActive())
                .map(ClinicPatientClinicalService::toResponse).toList();
    }

    @Transactional
    public AllergyResponse addAllergy(TenantId t, UUID patientId, AllergyRequest req) {
        ClinicPatient patient = requirePatient(t, patientId);
        String allergen = requireText(req.allergen(), "allergen");
        String type = oneOf(req.allergenType(), ALLERGEN_TYPES, "allergenType", "UNKNOWN");
        String severity = oneOf(req.severity(), SEVERITIES, "severity", null);
        boolean dup = allergyRepo.findByPatient(t, patientId).stream()
                .anyMatch(a -> a.isActive() && a.getAllergen().equalsIgnoreCase(allergen));
        if (dup) throw new IllegalArgumentException("Allergy '" + allergen + "' is already recorded as active");
        var a = ClinicPatientAllergy.create(t, patientId, allergen, type, clean(req.reaction()),
                severity, clean(req.notes()), currentUserOrNull());
        allergyRepo.save(a);
        refreshMirror(t, patient);
        return toResponse(a);
    }

    @Transactional
    public AllergyResponse updateAllergy(TenantId t, UUID patientId, UUID id, AllergyRequest req) {
        ClinicPatient patient = requirePatient(t, patientId);
        var a = allergyRepo.findOne(t, patientId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Allergy", id.toString()));
        a.update(oneOf(req.allergenType(), ALLERGEN_TYPES, "allergenType", null),
                clean(req.reaction()),
                oneOf(req.severity(), SEVERITIES, "severity", null),
                oneOf(req.status(), ALLERGY_STATUSES, "status", null),
                clean(req.notes()));
        allergyRepo.save(a);
        refreshMirror(t, patient);
        return toResponse(a);
    }

    // ── Conditions ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ConditionResponse> listConditions(TenantId t, UUID patientId, boolean includeInactive) {
        requirePatient(t, patientId);
        return conditionRepo.findByPatient(t, patientId).stream()
                .filter(c -> includeInactive || c.isCurrent())
                .map(ClinicPatientClinicalService::toResponse).toList();
    }

    @Transactional
    public ConditionResponse addCondition(TenantId t, UUID patientId, ConditionRequest req) {
        ClinicPatient patient = requirePatient(t, patientId);
        String name = requireText(req.conditionName(), "conditionName");
        String status = oneOf(req.status(), CONDITION_STATUSES, "status", "ACTIVE");
        boolean dup = conditionRepo.findByPatient(t, patientId).stream()
                .anyMatch(c -> c.isCurrent() && c.getConditionName().equalsIgnoreCase(name));
        if (dup) throw new IllegalArgumentException("Condition '" + name + "' is already recorded");
        var c = ClinicPatientCondition.create(t, patientId, name, clean(req.icd10Code()),
                req.onsetDate(), clean(req.notes()), currentUserOrNull());
        if (!"ACTIVE".equals(status)) c.update(null, null, status, null);
        conditionRepo.save(c);
        refreshMirror(t, patient);
        return toResponse(c);
    }

    @Transactional
    public ConditionResponse updateCondition(TenantId t, UUID patientId, UUID id, ConditionRequest req) {
        ClinicPatient patient = requirePatient(t, patientId);
        var c = conditionRepo.findOne(t, patientId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Condition", id.toString()));
        c.update(clean(req.icd10Code()), req.onsetDate(),
                oneOf(req.status(), CONDITION_STATUSES, "status", null), clean(req.notes()));
        conditionRepo.save(c);
        refreshMirror(t, patient);
        return toResponse(c);
    }

    // ── Medications ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MedicationResponse> listMedications(TenantId t, UUID patientId, boolean includeInactive) {
        requirePatient(t, patientId);
        return medicationRepo.findByPatient(t, patientId).stream()
                .filter(m -> includeInactive || m.isActive())
                .map(ClinicPatientClinicalService::toResponse).toList();
    }

    @Transactional
    public MedicationResponse addMedication(TenantId t, UUID patientId, MedicationRequest req) {
        requirePatient(t, patientId);
        String name = requireText(req.medicineName(), "medicineName");
        String source = oneOf(req.source(), MED_SOURCES, "source", "PATIENT_REPORTED");
        var m = ClinicPatientMedication.create(t, patientId, name, clean(req.nappiCode()),
                clean(req.dose()), clean(req.frequency()), source, req.startedOn(), null,
                clean(req.notes()), currentUserOrNull());
        medicationRepo.save(m);
        return toResponse(m);
    }

    @Transactional
    public MedicationResponse updateMedication(TenantId t, UUID patientId, UUID id, MedicationRequest req) {
        requirePatient(t, patientId);
        var m = medicationRepo.findOne(t, patientId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Medication", id.toString()));
        m.update(clean(req.dose()), clean(req.frequency()),
                oneOf(req.status(), MED_STATUSES, "status", null),
                req.stoppedOn(), clean(req.stopReason()), clean(req.notes()));
        medicationRepo.save(m);
        return toResponse(m);
    }

    // ── Legacy list sync (used by PATCH /patients/{id} with allergies/chronicConditions) ──

    /**
     * Reconciles structured rows to match a plain list of names: adds missing ones as
     * active and marks removed ones RESOLVED. Either list may be null (= leave alone).
     */
    @Transactional
    public void syncFromLegacyLists(TenantId t, ClinicPatient patient,
                                    List<String> allergyNames, List<String> conditionNames) {
        UUID pid = patient.getId();
        if (allergyNames != null) {
            var wanted = lowerSet(allergyNames);
            for (var a : allergyRepo.findByPatient(t, pid)) {
                if (a.isActive() && !wanted.contains(a.getAllergen().toLowerCase(Locale.ROOT))) {
                    a.update(null, null, null, "RESOLVED", null);
                    allergyRepo.save(a);
                }
            }
            var have = lowerSet(allergyRepo.findByPatient(t, pid).stream()
                    .filter(ClinicPatientAllergy::isActive).map(ClinicPatientAllergy::getAllergen).toList());
            for (String n : allergyNames) {
                if (!have.contains(n.toLowerCase(Locale.ROOT))) {
                    allergyRepo.save(ClinicPatientAllergy.create(t, pid, n, "UNKNOWN", null, null, null,
                            currentUserOrNull()));
                    have.add(n.toLowerCase(Locale.ROOT));
                }
            }
        }
        if (conditionNames != null) {
            var wanted = lowerSet(conditionNames);
            for (var c : conditionRepo.findByPatient(t, pid)) {
                if (c.isCurrent() && !wanted.contains(c.getConditionName().toLowerCase(Locale.ROOT))) {
                    c.update(null, null, "RESOLVED", null);
                    conditionRepo.save(c);
                }
            }
            var have = lowerSet(conditionRepo.findByPatient(t, pid).stream()
                    .filter(ClinicPatientCondition::isCurrent).map(ClinicPatientCondition::getConditionName).toList());
            for (String n : conditionNames) {
                if (!have.contains(n.toLowerCase(Locale.ROOT))) {
                    conditionRepo.save(ClinicPatientCondition.create(t, pid, n, null, null, null,
                            currentUserOrNull()));
                    have.add(n.toLowerCase(Locale.ROOT));
                }
            }
        }
        refreshMirror(t, patient);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Rewrites the legacy arrays on the patient from the current structured rows. */
    private void refreshMirror(TenantId t, ClinicPatient patient) {
        List<String> allergies = allergyRepo.findByPatient(t, patient.getId()).stream()
                .filter(ClinicPatientAllergy::isActive).map(ClinicPatientAllergy::getAllergen)
                .distinct().toList();
        List<String> conditions = conditionRepo.findByPatient(t, patient.getId()).stream()
                .filter(ClinicPatientCondition::isCurrent).map(ClinicPatientCondition::getConditionName)
                .distinct().toList();
        patient.update(null, null, null, null, null, new ArrayList<>(allergies),
                new ArrayList<>(conditions), null);
        patientRepo.save(patient);
    }

    private ClinicPatient requirePatient(TenantId t, UUID patientId) {
        return patientRepo.findActiveById(t, patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId.toString()));
    }

    private static String requireText(String v, String field) {
        if (v == null || v.isBlank()) throw new IllegalArgumentException(field + " is required");
        return v.trim();
    }

    private static String clean(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    /** Upper-cases and validates against an allowed set; blank returns the default (which may be null). */
    private static String oneOf(String v, Set<String> allowed, String field, String dflt) {
        if (v == null || v.isBlank()) return dflt;
        String u = v.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(u)) {
            throw new IllegalArgumentException(field + " must be one of " + new TreeSet<>(allowed));
        }
        return u;
    }

    private static Set<String> lowerSet(Collection<String> in) {
        Set<String> out = new HashSet<>();
        for (String s : in) if (s != null) out.add(s.trim().toLowerCase(Locale.ROOT));
        return out;
    }

    private static UUID currentUserOrNull() {
        try { return UserContext.getCurrentUserId(); } catch (RuntimeException e) { return null; }
    }

    static AllergyResponse toResponse(ClinicPatientAllergy a) {
        return new AllergyResponse(a.getId(), a.getPatientId(), a.getAllergen(), a.getAllergenType(),
                a.getReaction(), a.getSeverity(), a.getStatus(), a.getNotes(),
                a.getCreatedAt(), a.getUpdatedAt());
    }

    static ConditionResponse toResponse(ClinicPatientCondition c) {
        return new ConditionResponse(c.getId(), c.getPatientId(), c.getConditionName(), c.getIcd10Code(),
                c.getStatus(), c.getOnsetDate(), c.getNotes(), c.getCreatedAt(), c.getUpdatedAt());
    }

    static MedicationResponse toResponse(ClinicPatientMedication m) {
        return new MedicationResponse(m.getId(), m.getPatientId(), m.getMedicineName(), m.getNappiCode(),
                m.getDose(), m.getFrequency(), m.getStatus(), m.getSource(), m.getStartedOn(),
                m.getStoppedOn(), m.getStopReason(), m.getPrescriptionId(), m.getNotes(),
                m.getCreatedAt(), m.getUpdatedAt());
    }
}
