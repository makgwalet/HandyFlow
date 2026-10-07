package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.clinic.domain.model.ClinicConsultation;
import za.co.handyflow.platform.clinic.domain.model.ClinicPatient;
import za.co.handyflow.platform.clinic.domain.repository.*;
import za.co.handyflow.platform.clinic.dto.PatientBriefing;
import za.co.handyflow.platform.clinic.dto.PatientClinicalDtos.AllergyResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClinicBriefingServiceTest {

    @Mock ClinicPatientRepository patientRepo;
    @Mock ClinicConsultationRepository consultationRepo;
    @Mock ClinicAppointmentRepository appointmentRepo;
    @Mock ClinicLabResultRepository labRepo;
    @Mock ClinicPractitionerRepository practitionerRepo;
    @Mock ClinicPatientClinicalService clinical;
    @InjectMocks ClinicBriefingService service;

    TenantId tenant;
    UUID patientId = UUID.randomUUID();

    @BeforeEach
    void setUp() { tenant = TenantId.of(UUID.randomUUID()); }

    private void patientExists() {
        when(patientRepo.findActiveById(tenant, patientId)).thenReturn(Optional.of(
                ClinicPatient.create(tenant, "Jane", "Dlamini", null, null, null, "+27820000000", null, null, null)));
    }

    private void nothingElseOnRecord() {
        when(consultationRepo.findByPatient(tenant, patientId)).thenReturn(List.of());
        when(appointmentRepo.findByPatient(tenant, patientId)).thenReturn(List.of());
        when(labRepo.findByPatient(tenant, patientId)).thenReturn(List.of());
        when(clinical.listAllergies(tenant, patientId, false)).thenReturn(List.of());
        when(clinical.listConditions(tenant, patientId, false)).thenReturn(List.of());
        when(clinical.listMedications(tenant, patientId, false)).thenReturn(List.of());
    }

    @Test
    @DisplayName("an unknown patient is a not-found, not an empty briefing")
    void unknownPatient() {
        when(patientRepo.findActiveById(tenant, patientId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.briefing(tenant, patientId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("a patient who has never been seen gets a first-visit note and no invented history")
    void neverSeen() {
        patientExists();
        nothingElseOnRecord();

        PatientBriefing b = service.briefing(tenant, patientId);

        assertThat(b.visitCount()).isZero();
        assertThat(b.lastVisit()).isNull();
        assertThat(b.daysSinceLastVisit()).isNull();
        assertThat(b.lastVitals()).isNull();
        assertThat(b.nextAppointment()).isNull();
        assertThat(b.recall()).isNull();
        assertThat(b.labs().unreviewed()).isZero();
        assertThat(b.alerts()).extracting(PatientBriefing.Alert::code).containsExactly("FIRST_VISIT");
    }

    @Test
    @DisplayName("the last finished visit, its vitals, an unbooked follow-up and a severe allergy come through")
    void seenBefore() {
        patientExists();
        var visit = ClinicConsultation.create(tenant, patientId, null, null, "Cough");
        visit.recordVitals(new BigDecimal("72"), new BigDecimal("170"), "128/82", 76, new BigDecimal("36.8"), new BigDecimal("98"));
        visit.recordClinical("hx", "ex", "Acute bronchitis", List.of("J20.9"), "plan", 7);
        visit.sign();
        var draft = ClinicConsultation.createDraft(tenant, patientId, null, null, "Back again");
        when(consultationRepo.findByPatient(tenant, patientId)).thenReturn(List.of(visit, draft));
        when(appointmentRepo.findByPatient(tenant, patientId)).thenReturn(List.of());
        when(labRepo.findByPatient(tenant, patientId)).thenReturn(List.of());
        when(clinical.listAllergies(tenant, patientId, false)).thenReturn(List.of(
                new AllergyResponse(UUID.randomUUID(), patientId, "Penicillin", "DRUG", "Rash", "SEVERE", "ACTIVE", null, Instant.now(), Instant.now()),
                new AllergyResponse(UUID.randomUUID(), patientId, "Dust", "ENVIRONMENT", null, "MILD", "ACTIVE", null, Instant.now(), Instant.now())));
        when(clinical.listConditions(tenant, patientId, false)).thenReturn(List.of());
        when(clinical.listMedications(tenant, patientId, false)).thenReturn(List.of());

        // Eight days later: the 7-day follow-up is a day overdue.
        PatientBriefing b = service.briefing(tenant, patientId, visit.getConsultedAt().plus(8, ChronoUnit.DAYS));

        assertThat(b.visitCount()).isEqualTo(1);
        assertThat(b.lastVisit().diagnosis()).isEqualTo("Acute bronchitis");
        assertThat(b.lastVisit().icd10Codes()).containsExactly("J20.9");
        assertThat(b.daysSinceLastVisit()).isEqualTo(8L);
        assertThat(b.lastVitals().bloodPressure()).isEqualTo("128/82");
        assertThat(b.recall().overdueDays()).isEqualTo(1);
        assertThat(b.openDraft().status()).isEqualTo("DRAFT");
        assertThat(b.alerts()).extracting(PatientBriefing.Alert::code)
                .containsExactly("SEVERE_ALLERGY", "RECALL_OVERDUE", "OPEN_DRAFT");
        assertThat(b.alerts().get(0).message()).isEqualTo("Severe allergy: Penicillin");
    }
}
