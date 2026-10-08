package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.clinic.domain.model.ClinicPatient;
import za.co.handyflow.platform.clinic.domain.repository.*;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClinicLetterValuesTest {

    @Mock ClinicConsultationRepository   consultationRepo;
    @Mock ClinicPatientRepository        patientRepo;
    @Mock ClinicPatientProfileRepository profileRepo;
    @Mock ClinicPractitionerRepository   practitionerRepo;
    @Mock TenantFacade                   tenantFacade;
    @InjectMocks ClinicLetterValues values;

    final TenantId t = TenantId.of(UUID.randomUUID());

    @Test
    void aLetterWithNoVisitStillFillsPatientAndRecipientFields() {
        var p = ClinicPatient.create(t, "Liam", "Botha", "1903125000087", LocalDate.of(2019, 3, 12), null, "0821234567", null, null, null);
        when(patientRepo.findActiveById(t, p.getId())).thenReturn(Optional.of(p));
        when(profileRepo.findOne(t, p.getId())).thenReturn(Optional.empty());
        when(tenantFacade.findTenantDetails(t)).thenReturn(Optional.empty());
        var v = values.load(t, p.getId(), null, "HR", "Acme").values();
        assertThat(v.get("patient.name")).isEqualTo("Liam Botha");
        assertThat(v.get("patient.idNumber")).isEqualTo("1903125000087");
        assertThat(v.get("recipient.company")).isEqualTo("Acme");
        assertThat(v.get("visit.diagnosis")).isNull();
    }

    @Test
    void theChosenPractitionerSignsAVisitlessLetter() {
        var p = ClinicPatient.create(t, "Liam", "Botha", null, LocalDate.of(2019, 3, 12), null, null, null, null, null);
        var dr = ClinicPractitioner.create(t, "Priya", "Govender", "GP", "MP0123456", "PR998", null, null);
        when(patientRepo.findActiveById(t, p.getId())).thenReturn(Optional.of(p));
        when(profileRepo.findOne(t, p.getId())).thenReturn(Optional.empty());
        when(tenantFacade.findTenantDetails(t)).thenReturn(Optional.empty());
        when(practitionerRepo.findActiveById(t, dr.getId())).thenReturn(Optional.of(dr));
        var loaded = values.load(t, p.getId(), null, null, null, dr.getId());
        assertThat(loaded.doctor()).isSameAs(dr);
        assertThat(loaded.values().get("doctor.name")).contains("Govender");
        assertThat(loaded.values().get("doctor.hpcsa")).isEqualTo("MP0123456");
    }

    @Test
    void aSignerWhoDoesNotExistIsRefusedNotIgnored() {
        var p = ClinicPatient.create(t, "Liam", "Botha", null, LocalDate.of(2019, 3, 12), null, null, null, null, null);
        var ghost = UUID.randomUUID();
        when(patientRepo.findActiveById(t, p.getId())).thenReturn(Optional.of(p));
        when(practitionerRepo.findActiveById(t, ghost)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> values.load(t, p.getId(), null, null, null, ghost)).isInstanceOf(za.co.handyflow.platform.shared.ResourceNotFoundException.class);
    }

    @Test
    void neitherPatientNorVisitIsRefused() {
        assertThatThrownBy(() -> values.load(t, null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
