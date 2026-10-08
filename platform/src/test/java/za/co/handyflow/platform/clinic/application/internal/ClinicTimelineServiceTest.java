package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.clinic.domain.model.*;
import za.co.handyflow.platform.clinic.domain.repository.*;
import za.co.handyflow.platform.clinic.dto.TimelineEvent;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClinicTimelineServiceTest {

    @Mock ClinicPatientRepository patientRepo;
    @Mock ClinicAppointmentRepository appointmentRepo;
    @Mock ClinicConsultationRepository consultationRepo;
    @Mock ClinicPrescriptionRepository prescriptionRepo;
    @Mock ClinicLabResultRepository labRepo;
    @Mock ClinicClaimRepository claimRepo;
    @Mock ClinicPaymentRepository paymentRepo;
    @Mock ClinicVisitService visitService;
    @InjectMocks ClinicTimelineService service;

    TenantId tenant;
    UUID patientId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tenant = TenantId.of(UUID.randomUUID());
    }

    private void patientExists() {
        when(patientRepo.findActiveById(tenant, patientId)).thenReturn(Optional.of(
                ClinicPatient.create(tenant, "Jane", "Dlamini", null, null, null, "+27820000000", null, null, null)));
    }

    @Test
    @DisplayName("combines visits and prescriptions, skips abandoned drafts, and never reads billing for a non-billing caller")
    void combinesAndHidesBilling() {
        patientExists();
        var signed = ClinicConsultation.create(tenant, patientId, null, null, "Cough");
        var abandoned = ClinicConsultation.createDraft(tenant, patientId, null, null, "Discarded");
        abandoned.abandon();
        when(consultationRepo.findByPatient(tenant, patientId)).thenReturn(List.of(signed, abandoned));
        when(prescriptionRepo.findByPatient(tenant, patientId)).thenReturn(List.of(
                ClinicPrescription.create(tenant, signed.getId(), patientId, null, "Amoxicillin", "500mg", "TDS", "7 days", 21, 0, null)));
        when(appointmentRepo.findByPatient(tenant, patientId)).thenReturn(List.of());
        when(labRepo.findByPatient(tenant, patientId)).thenReturn(List.of());

        List<TimelineEvent> r = service.timeline(tenant, patientId, null, null, null, false, null);

        assertThat(r).extracting(TimelineEvent::kind).containsExactlyInAnyOrder("CONSULTATION", "PRESCRIPTION");
        assertThat(r).extracting(TimelineEvent::title).contains("Consultation: Cough", "Prescribed: Amoxicillin");
        verifyNoInteractions(claimRepo, paymentRepo);
    }

    @Test
    @DisplayName("a consultation event carries the visit summary and the people involved")
    void consultationCarriesSummaryAndPeople() {
        patientExists();
        var signed = ClinicConsultation.create(tenant, patientId, null, null, "Cough");
        when(consultationRepo.findByPatient(tenant, patientId)).thenReturn(List.of(signed));
        when(visitService.visits(tenant, patientId)).thenReturn(List.of(new za.co.handyflow.platform.clinic.dto.VisitDtos.VisitResponse(
                signed.getId(), null, "SIGNED", null, null, null,
                List.of(new za.co.handyflow.platform.clinic.dto.VisitDtos.TeamMember("Prepared by", "Sister Zodwa Nkosi", null)),
                "Cough", null, null, "Acute bronchitis", List.of(), null, null,
                null, null, null, null, null, null, false, null, List.of(), List.of())));
        when(appointmentRepo.findByPatient(tenant, patientId)).thenReturn(List.of());
        when(prescriptionRepo.findByPatient(tenant, patientId)).thenReturn(List.of());
        when(labRepo.findByPatient(tenant, patientId)).thenReturn(List.of());

        TimelineEvent e = service.timeline(tenant, patientId, null, null, null, false, null).get(0);

        assertThat(e.summary()).containsExactly("Reason: Cough", "Diagnosis: Acute bronchitis");
        assertThat(e.people()).containsExactly("Prepared by Sister Zodwa Nkosi");
    }

    @Test
    @DisplayName("an unknown patient is not found")
    void unknownPatient() {
        when(patientRepo.findActiveById(tenant, patientId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.timeline(tenant, patientId, null, null, null, true, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
