package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.clinic.domain.model.*;
import za.co.handyflow.platform.clinic.domain.repository.*;
import za.co.handyflow.platform.clinic.dto.PatientClinicalDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClinicPatientClinicalServiceTest {

    @Mock ClinicPatientRepository           patientRepo;
    @Mock ClinicPatientAllergyRepository    allergyRepo;
    @Mock ClinicPatientConditionRepository  conditionRepo;
    @Mock ClinicPatientMedicationRepository medicationRepo;

    @InjectMocks ClinicPatientClinicalService service;

    static final UUID TENANT_UUID = UUID.fromString("9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f");
    static final TenantId TENANT;
    static {
        TENANT = Mockito.mock(TenantId.class);
        Mockito.when(TENANT.getValue()).thenReturn(TENANT_UUID);
    }

    private ClinicPatient patient() {
        return ClinicPatient.create(TENANT, "Jane", "Dlamini", null, null, null,
                "+27820000000", null, null, null);
    }

    private void givenPatient(ClinicPatient p) {
        when(patientRepo.findActiveById(TENANT, p.getId())).thenReturn(Optional.of(p));
    }

    @Test
    @DisplayName("addAllergy stores a normalised allergy and mirrors it onto the patient")
    void addAllergyMirrorsToPatient() {
        var p = patient(); givenPatient(p);
        var saved = new ArrayList<ClinicPatientAllergy>();
        when(allergyRepo.findByPatient(TENANT, p.getId())).thenAnswer(i -> new ArrayList<>(saved));
        when(allergyRepo.save(any())).thenAnswer(i -> { saved.add(i.getArgument(0)); return i.getArgument(0); });

        var r = service.addAllergy(TENANT, p.getId(),
                new AllergyRequest(" Penicillin ", "drug", "Rash", "severe", null, null));

        assertThat(r.allergen()).isEqualTo("Penicillin");
        assertThat(r.allergenType()).isEqualTo("DRUG");
        assertThat(r.severity()).isEqualTo("SEVERE");
        assertThat(p.getAllergies()).containsExactly("Penicillin");
        verify(patientRepo).save(p);
    }

    @Test
    @DisplayName("addAllergy rejects an unknown severity and a blank allergen")
    void addAllergyValidates() {
        var p = patient(); givenPatient(p);

        assertThatThrownBy(() -> service.addAllergy(TENANT, p.getId(),
                new AllergyRequest("Peanuts", null, null, "HUGE", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.addAllergy(TENANT, p.getId(),
                new AllergyRequest("  ", null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(allergyRepo, never()).save(any());
    }

    @Test
    @DisplayName("addAllergy rejects a duplicate active allergy (case-insensitive)")
    void addAllergyRejectsDuplicate() {
        var p = patient(); givenPatient(p);
        var existing = ClinicPatientAllergy.create(TENANT, p.getId(), "Penicillin", "DRUG",
                null, null, null, null);
        when(allergyRepo.findByPatient(TENANT, p.getId())).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.addAllergy(TENANT, p.getId(),
                new AllergyRequest("penicillin", null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("resolving an allergy removes it from the patient's mirrored list")
    void resolvingAllergyUpdatesMirror() {
        var p = patient(); givenPatient(p);
        var a = ClinicPatientAllergy.create(TENANT, p.getId(), "Latex", "ENVIRONMENT", null, null, null, null);
        when(allergyRepo.findOne(TENANT, p.getId(), a.getId())).thenReturn(Optional.of(a));
        when(allergyRepo.findByPatient(TENANT, p.getId())).thenReturn(List.of(a));

        service.updateAllergy(TENANT, p.getId(), a.getId(),
                new AllergyRequest(null, null, null, null, "resolved", null));

        assertThat(a.getStatus()).isEqualTo("RESOLVED");
        assertThat(p.getAllergies()).isEmpty();
    }

    @Test
    @DisplayName("updateAllergy for a missing id is a not-found")
    void updateMissingAllergy() {
        var p = patient(); givenPatient(p);
        var id = UUID.randomUUID();
        when(allergyRepo.findOne(TENANT, p.getId(), id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateAllergy(TENANT, p.getId(), id,
                new AllergyRequest(null, null, null, null, "RESOLVED", null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("controlled conditions still count as current; resolved ones drop out")
    void conditionMirrorUsesCurrentStatuses() {
        var p = patient(); givenPatient(p);
        var controlled = ClinicPatientCondition.create(TENANT, p.getId(), "Hypertension", "I10", null, null, null);
        controlled.update(null, null, "CONTROLLED", null);
        var resolved = ClinicPatientCondition.create(TENANT, p.getId(), "Pneumonia", null, null, null, null);
        resolved.update(null, null, "RESOLVED", null);
        when(conditionRepo.findByPatient(TENANT, p.getId())).thenReturn(List.of(controlled, resolved));
        when(conditionRepo.findOne(TENANT, p.getId(), resolved.getId())).thenReturn(Optional.of(resolved));

        service.updateCondition(TENANT, p.getId(), resolved.getId(),
                new ConditionRequest(null, null, null, "RESOLVED", null));

        assertThat(p.getChronicConditions()).containsExactly("Hypertension");
    }

    @Test
    @DisplayName("stopping a medication stamps stoppedOn")
    void stoppingMedicationStampsDate() {
        var p = patient(); givenPatient(p);
        var m = ClinicPatientMedication.create(TENANT, p.getId(), "Amlodipine", null, "5mg", "daily",
                "PATIENT_REPORTED", null, null, null, null);
        when(medicationRepo.findOne(TENANT, p.getId(), m.getId())).thenReturn(Optional.of(m));

        var r = service.updateMedication(TENANT, p.getId(), m.getId(),
                new MedicationRequest(null, null, null, null, "stopped", null, null, null, "Side effects", null));

        assertThat(r.status()).isEqualTo("STOPPED");
        assertThat(r.stoppedOn()).isNotNull();
        assertThat(r.stopReason()).isEqualTo("Side effects");
    }

    @Test
    @DisplayName("syncFromLegacyLists adds new names and resolves removed ones")
    void syncReconcilesLists() {
        var p = patient();
        var keep = ClinicPatientAllergy.create(TENANT, p.getId(), "Penicillin", "DRUG", null, null, null, null);
        var drop = ClinicPatientAllergy.create(TENANT, p.getId(), "Latex", "OTHER", null, null, null, null);
        var rows = new ArrayList<>(List.of(keep, drop));
        when(allergyRepo.findByPatient(TENANT, p.getId())).thenAnswer(i -> new ArrayList<>(rows));
        when(allergyRepo.save(any())).thenAnswer(i -> {
            ClinicPatientAllergy a = i.getArgument(0);
            if (!rows.contains(a)) rows.add(a);
            return a;
        });

        service.syncFromLegacyLists(TENANT, p, List.of("Penicillin", "Peanuts"), null);

        assertThat(drop.getStatus()).isEqualTo("RESOLVED");
        assertThat(keep.getStatus()).isEqualTo("ACTIVE");
        assertThat(rows).anyMatch(a -> a.getAllergen().equals("Peanuts") && a.isActive());
        assertThat(p.getAllergies()).containsExactlyInAnyOrder("Penicillin", "Peanuts");
    }

    @Test
    @DisplayName("recordPrescribed lists a prescribed medicine as PRESCRIBED_HERE with the prescription id")
    void recordPrescribedAdds() {
        var pid = UUID.randomUUID(); var rxId = UUID.randomUUID();
        when(medicationRepo.findByPatient(TENANT, pid)).thenReturn(List.of());

        service.recordPrescribed(TENANT, pid, rxId, " Amoxicillin 500mg ", " 700000 ", "500mg", " ");

        verify(medicationRepo).save(argThat(m -> "Amoxicillin 500mg".equals(m.getMedicineName())
                && "PRESCRIBED_HERE".equals(m.getSource()) && rxId.equals(m.getPrescriptionId())
                && "700000".equals(m.getNappiCode()) && m.getFrequency() == null && m.isActive()));
    }

    @Test
    @DisplayName("recordPrescribed does not duplicate: same prescription, or the same medicine already active")
    void recordPrescribedSkipsDuplicates() {
        var pid = UUID.randomUUID(); var rxId = UUID.randomUUID();
        var existing = ClinicPatientMedication.create(TENANT, pid, "Amoxicillin 500mg", null, null, null,
                "PATIENT_REPORTED", null, null, null, null);
        when(medicationRepo.findByPatient(TENANT, pid)).thenReturn(List.of(existing));

        service.recordPrescribed(TENANT, pid, rxId, "amoxicillin 500MG", null, null, null);

        verify(medicationRepo, never()).save(any());
    }

    @Test
    @DisplayName("recordPrescribed lists the medicine again once the earlier entry was stopped")
    void recordPrescribedAfterStopped() {
        var pid = UUID.randomUUID();
        var stopped = ClinicPatientMedication.create(TENANT, pid, "Amoxicillin", null, null, null,
                "PATIENT_REPORTED", null, null, null, null);
        stopped.update(null, null, "STOPPED", null, null, null);
        when(medicationRepo.findByPatient(TENANT, pid)).thenReturn(List.of(stopped));

        service.recordPrescribed(TENANT, pid, UUID.randomUUID(), "Amoxicillin", null, null, null);

        verify(medicationRepo).save(any());
    }

    @Test
    @DisplayName("recordPrescribed ignores a blank medicine name")
    void recordPrescribedBlank() {
        service.recordPrescribed(TENANT, UUID.randomUUID(), UUID.randomUUID(), "  ", null, null, null);
        verifyNoInteractions(medicationRepo);
    }
}
