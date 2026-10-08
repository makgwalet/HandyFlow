package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.clinic.domain.model.*;
import za.co.handyflow.platform.clinic.domain.repository.*;
import za.co.handyflow.platform.clinic.dto.PatientHistoryDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClinicPatientHistoryServiceTest {

    @Mock ClinicPatientRepository              patientRepo;
    @Mock ClinicPatientFamilyHistoryRepository familyRepo;
    @Mock ClinicPatientSocialHistoryRepository socialRepo;
    @Mock ClinicMedicalAidRepository           aidRepo;

    @InjectMocks ClinicPatientHistoryService service;

    static final TenantId TENANT = TenantId.of(UUID.fromString("9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f"));

    private ClinicPatient patient(String first) {
        return ClinicPatient.create(TENANT, first, "Dlamini", null, null, null, "+27820000000", null, null, null);
    }

    private void given(ClinicPatient p) { when(patientRepo.findActiveById(TENANT, p.getId())).thenReturn(Optional.of(p)); }

    @Test
    @DisplayName("a family entry is normalised, and the same condition for the same relative is not recorded twice")
    void addFamilyNormalisesAndRefusesDuplicates() {
        var p = patient("Jane"); given(p);
        when(familyRepo.findByPatient(TENANT, p.getId())).thenReturn(List.of());
        when(familyRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        var r = service.addFamily(TENANT, p.getId(), new FamilyHistoryRequest(" mother ", " Type 2 diabetes ", 55, null, "", null));
        assertThat(r.relative()).isEqualTo("MOTHER");
        assertThat(r.conditionName()).isEqualTo("Type 2 diabetes");
        assertThat(r.ageAtOnset()).isEqualTo(55);
        assertThat(r.status()).isEqualTo("ACTIVE");

        var existing = ClinicPatientFamilyHistory.create(TENANT, p.getId(), "MOTHER", "Type 2 Diabetes", null, null, null, null);
        when(familyRepo.findByPatient(TENANT, p.getId())).thenReturn(List.of(existing));
        assertThatThrownBy(() -> service.addFamily(TENANT, p.getId(), new FamilyHistoryRequest("MOTHER", "type 2 diabetes", null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already recorded");
    }

    @Test
    @DisplayName("an entry marked as entered in error is hidden from the list unless asked for")
    void enteredInErrorIsHidden() {
        var p = patient("Jane"); given(p);
        var h = ClinicPatientFamilyHistory.create(TENANT, p.getId(), "FATHER", "Asthma", null, null, null, null);
        h.update("FATHER", "Asthma", null, null, null, "ENTERED_IN_ERROR");
        when(familyRepo.findByPatient(TENANT, p.getId())).thenReturn(List.of(h));
        assertThat(service.listFamily(TENANT, p.getId(), false)).isEmpty();
        assertThat(service.listFamily(TENANT, p.getId(), true)).hasSize(1);
    }

    @Test
    @DisplayName("social history says nothing is recorded yet instead of inventing values")
    void socialHistoryNotRecorded() {
        var p = patient("Jane"); given(p);
        when(socialRepo.findForPatient(TENANT, p.getId())).thenReturn(Optional.empty());
        var r = service.getSocial(TENANT, p.getId());
        assertThat(r.recorded()).isFalse();
        assertThat(r.smokingStatus()).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("saving social history refuses a value that is not one of the choices")
    void socialHistoryRefusesBadChoice() {
        var p = patient("Jane"); given(p);
        when(socialRepo.findForPatient(TENANT, p.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.putSocial(TENANT, p.getId(), new SocialHistoryRequest("sometimes", null, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Smoking");
        verify(socialRepo, never()).save(any());
    }

    @Test
    @DisplayName("a dependant with no medical aid of their own is shown the principal's, marked as inherited")
    void dependantInheritsPrincipalsAid() {
        var principal = patient("Mary");
        var dependant = patient("Liam"); dependant.setPrincipalId(principal.getId()); given(dependant);
        when(patientRepo.findActiveById(TENANT, principal.getId())).thenReturn(Optional.of(principal));
        when(aidRepo.findActiveByPatient(TENANT, dependant.getId())).thenReturn(List.of());
        var aid = ClinicMedicalAid.create(TENANT.getValue(), principal.getId(), "Discovery", null, "123", "00", null);
        when(aidRepo.findActiveByPatient(TENANT, principal.getId())).thenReturn(List.of(aid));
        var r = service.getMedicalAid(TENANT, dependant.getId());
        assertThat(r.inherited()).isTrue();
        assertThat(r.schemeName()).isEqualTo("Discovery");
        assertThat(r.inheritedFrom()).contains("Mary");
    }

    @Test
    @DisplayName("no medical aid anywhere gives null, not an empty record")
    void noAidIsNull() {
        var p = patient("Jane"); given(p);
        when(aidRepo.findActiveByPatient(TENANT, p.getId())).thenReturn(List.of());
        assertThat(service.getMedicalAid(TENANT, p.getId())).isNull();
    }

    @Test
    @DisplayName("saving medical aid updates the active record in place, and creates one when there is none")
    void putMedicalAidUpdatesOrCreates() {
        var p = patient("Jane"); given(p);
        when(aidRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(aidRepo.findActiveByPatient(TENANT, p.getId())).thenReturn(List.of());
        var created = service.putMedicalAid(TENANT, p.getId(), new MedicalAidRequest("Bonitas", "Standard", "998877", "01", null, null));
        assertThat(created.schemeName()).isEqualTo("Bonitas");
        assertThat(created.inherited()).isFalse();

        var active = ClinicMedicalAid.create(TENANT.getValue(), p.getId(), "Old", null, "1", null, null);
        when(aidRepo.findActiveByPatient(TENANT, p.getId())).thenReturn(List.of(active));
        var updated = service.putMedicalAid(TENANT, p.getId(), new MedicalAidRequest("Discovery", null, "42", null, null, null));
        assertThat(updated.id()).isEqualTo(active.getId());
        assertThat(updated.memberNumber()).isEqualTo("42");
    }

    @Test
    @DisplayName("removing medical aid switches the record off and keeps it")
    void removeKeepsTheRow() {
        var p = patient("Jane"); given(p);
        var active = ClinicMedicalAid.create(TENANT.getValue(), p.getId(), "Old", null, "1", null, null);
        when(aidRepo.findActiveByPatient(TENANT, p.getId())).thenReturn(List.of(active));
        service.removeMedicalAid(TENANT, p.getId());
        assertThat(active.isActive()).isFalse();
        verify(aidRepo, never()).delete(any());
    }
}
