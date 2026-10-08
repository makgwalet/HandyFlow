package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.clinic.domain.model.ClinicPatient;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPatientProfileRepository;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPatientRepository;
import za.co.handyflow.platform.clinic.dto.ConsentStatusResponse;
import za.co.handyflow.platform.clinic.dto.ProfileDtos.*;
import za.co.handyflow.platform.shared.ConflictException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClinicPatientProfileServiceTest {

    @Mock ClinicPatientProfileRepository profileRepo;
    @Mock ClinicPatientRepository        patientRepo;
    @Mock ClinicPatientHistoryService    historyService;
    @Mock ClinicConsentService           consentService;

    @InjectMocks ClinicPatientProfileService service;

    static final TenantId T = TenantId.of(UUID.fromString("9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f"));

    private static ClinicPatient patient() {
        return ClinicPatient.create(T, "Sipho", "Nkosi", null, LocalDate.of(1980, 1, 1), "Male", "0821234567", null, null, null);
    }

    @Test
    void aSavedProfileIsReturnedWithItsCompleteness() {
        ClinicPatient p = patient();
        when(patientRepo.findActiveById(T, p.getId())).thenReturn(Optional.of(p));
        when(profileRepo.findOne(T, p.getId())).thenReturn(Optional.empty());
        when(historyService.getMedicalAid(T, p.getId())).thenReturn(null);
        when(consentService.getConsentStatus(T, p.getId())).thenReturn(List.of(new ConsentStatusResponse("TREATMENT", "GRANTED", null, null, null)));
        ProfileResponse r = service.get(T, p.getId());
        assertThat(r.completeness().total()).isEqualTo(9);
        assertThat(r.completeness().items()).anyMatch(i -> i.key().equals("consent") && i.done());
        assertThat(r.completeness().items()).anyMatch(i -> i.key().equals("payer") && !i.done());
    }

    @Test
    void anIdHeldByAnotherPatientIsRefusedButOwnIdIsAllowed() {
        ClinicPatient p = patient();
        ClinicPatient other = patient();
        when(patientRepo.findActiveById(T, p.getId())).thenReturn(Optional.of(p));
        when(patientRepo.findActiveByIdNumber(T, "8001015009087")).thenReturn(List.of(other));
        var req = new DemographicsRequest("Sipho", "Nkosi", "8001015009087", null, "Male", "MALE");
        assertThatThrownBy(() -> service.updateDemographics(T, p.getId(), req)).isInstanceOf(ConflictException.class);
        verify(patientRepo, never()).save(any());

        when(patientRepo.findActiveByIdNumber(T, "8001015009087")).thenReturn(List.of(p));
        when(patientRepo.save(p)).thenReturn(p);
        PatientCore core = service.updateDemographics(T, p.getId(), req);
        assertThat(core.idNumber()).isEqualTo("8001015009087");
        assertThat(core.sexAtBirth()).isEqualTo("MALE");
    }

    @Test
    void contactDetailsReplaceTheOldOnes() {
        ClinicPatient p = patient();
        when(patientRepo.findActiveById(T, p.getId())).thenReturn(Optional.of(p));
        when(patientRepo.save(p)).thenReturn(p);
        PatientCore core = service.updateContact(T, p.getId(), new ContactRequest("0839999999", null, "Mum", "0821111111"));
        assertThat(core.phone()).isEqualTo("0839999999");
        assertThat(core.emergencyContactName()).isEqualTo("Mum");
    }
}
