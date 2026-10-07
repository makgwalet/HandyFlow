package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.clinic.domain.model.ClinicConsultation;
import za.co.handyflow.platform.clinic.domain.model.ClinicConsultationTransition;
import za.co.handyflow.platform.clinic.domain.repository.ClinicConsultationRepository;
import za.co.handyflow.platform.clinic.domain.repository.ClinicConsultationTransitionRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClinicHandoffServiceTest {

    @Mock ClinicConsultationRepository           consultationRepo;
    @Mock ClinicConsultationTransitionRepository transitionRepo;

    @InjectMocks ClinicHandoffService service;

    static final UUID TENANT_UUID = UUID.fromString("9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f");
    static final TenantId TENANT;
    static {
        TENANT = TenantId.of(TENANT_UUID);
    }

    /** A draft with the nurse's portion filled in, registered with the repository mock. */
    private ClinicConsultation completeDraft() {
        ClinicConsultation c = ClinicConsultation.createDraft(TENANT, UUID.randomUUID(), null, null, "Cough");
        c.recordVitals(new BigDecimal("70"), null, null, null, null, null);
        lenient().when(consultationRepo.findActiveById(TENANT, c.getId())).thenReturn(Optional.of(c));
        return c;
    }

    private ClinicConsultation inStatus(String status) {
        ClinicConsultation c = completeDraft();
        if (!"DRAFT".equals(status)) {
            c.transitionTo("READY_FOR_DOCTOR");
            if (!"READY_FOR_DOCTOR".equals(status)) {
                c.transitionTo("DOCTOR_REVIEWING");
                if ("RETURNED_TO_NURSE".equals(status)) c.transitionTo("RETURNED_TO_NURSE");
                if ("DOCTOR_COMPLETED".equals(status)) c.transitionTo("DOCTOR_COMPLETED");
            }
        }
        return c;
    }

    @Test
    @DisplayName("sendToDoctor moves a complete draft to READY_FOR_DOCTOR and logs the move")
    void sendToDoctorLogsTransition() {
        ClinicConsultation c = completeDraft();

        service.sendToDoctor(TENANT, c.getId(), "  BP to recheck  ");

        assertThat(c.getStatus()).isEqualTo("READY_FOR_DOCTOR");
        ArgumentCaptor<ClinicConsultationTransition> cap = ArgumentCaptor.forClass(ClinicConsultationTransition.class);
        verify(transitionRepo).save(cap.capture());
        assertThat(cap.getValue().getFromStatus()).isEqualTo("DRAFT");
        assertThat(cap.getValue().getToStatus()).isEqualTo("READY_FOR_DOCTOR");
        assertThat(cap.getValue().getComment()).isEqualTo("BP to recheck");
    }

    @Test
    @DisplayName("sendToDoctor is refused while the nurse's portion is incomplete")
    void sendToDoctorRequiresNursePortion() {
        ClinicConsultation c = ClinicConsultation.createDraft(TENANT, UUID.randomUUID(), null, null, " ");
        when(consultationRepo.findActiveById(TENANT, c.getId())).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.sendToDoctor(TENANT, c.getId(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chief complaint")
                .hasMessageContaining("vital sign");
        assertThat(c.getStatus()).isEqualTo("DRAFT");
        verify(transitionRepo, never()).save(any());
    }

    @Test
    @DisplayName("accept moves READY_FOR_DOCTOR to DOCTOR_REVIEWING and records the reviewer")
    void acceptAssignsReviewer() {
        ClinicConsultation c = inStatus("READY_FOR_DOCTOR");
        UUID doctor = UUID.randomUUID();

        service.accept(TENANT, c.getId(), doctor);

        assertThat(c.getStatus()).isEqualTo("DOCTOR_REVIEWING");
        assertThat(c.getReviewingPractitionerId()).isEqualTo(doctor);
    }

    @Test
    @DisplayName("accept is refused unless the consultation is READY_FOR_DOCTOR")
    void acceptOnlyFromReady() {
        ClinicConsultation c = completeDraft();

        assertThatThrownBy(() -> service.accept(TENANT, c.getId(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DRAFT");
    }

    @Test
    @DisplayName("returnToNurse needs a known reason and a comment, then logs both")
    void returnToNurseRecordsReasonAndComment() {
        ClinicConsultation c = inStatus("DOCTOR_REVIEWING");

        service.returnToNurse(TENANT, c.getId(), "missing_observations", " please add pulse ");

        assertThat(c.getStatus()).isEqualTo("RETURNED_TO_NURSE");
        ArgumentCaptor<ClinicConsultationTransition> cap = ArgumentCaptor.forClass(ClinicConsultationTransition.class);
        verify(transitionRepo, atLeastOnce()).save(cap.capture());
        ClinicConsultationTransition last = cap.getValue();
        assertThat(last.getReasonCode()).isEqualTo("MISSING_OBSERVATIONS");
        assertThat(last.getComment()).isEqualTo("please add pulse");
    }

    @Test
    @DisplayName("returnToNurse rejects a missing comment or unknown reason before touching anything")
    void returnToNurseValidatesInput() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> service.returnToNurse(TENANT, id, "OTHER", " "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("comment");
        assertThatThrownBy(() -> service.returnToNurse(TENANT, id, "NOPE", "x"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unknown return reason");
        assertThatThrownBy(() -> service.returnToNurse(TENANT, id, null, "x"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reason is required");
        verifyNoInteractions(consultationRepo, transitionRepo);
    }

    @Test
    @DisplayName("a returned consultation can be resumed and handed over again")
    void returnedCanBeResumedAndResent() {
        ClinicConsultation c = inStatus("RETURNED_TO_NURSE");

        service.resumeNurseWork(TENANT, c.getId());
        assertThat(c.getStatus()).isEqualTo("NURSE_IN_PROGRESS");

        service.sendToDoctor(TENANT, c.getId(), null);
        assertThat(c.getStatus()).isEqualTo("READY_FOR_DOCTOR");
    }

    @Test
    @DisplayName("resume is refused unless the consultation was returned")
    void resumeOnlyFromReturned() {
        ClinicConsultation c = completeDraft();

        assertThatThrownBy(() -> service.resumeNurseWork(TENANT, c.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("doctorComplete moves DOCTOR_REVIEWING to DOCTOR_COMPLETED, which is signable but not editable")
    void doctorCompleteThenSignable() {
        ClinicConsultation c = inStatus("DOCTOR_REVIEWING");

        service.doctorComplete(TENANT, c.getId());

        assertThat(c.getStatus()).isEqualTo("DOCTOR_COMPLETED");
        assertThat(c.isSignable()).isTrue();
        assertThat(c.isAwaitingHandoff()).isTrue();
    }

    @Test
    @DisplayName("unknown consultation is a 404")
    void unknownConsultation() {
        UUID id = UUID.randomUUID();
        when(consultationRepo.findActiveById(TENANT, id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.doctorComplete(TENANT, id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("states that wait on the other clinician are not editable, signed ones never re-enter the flow")
    void stateHelpers() {
        ClinicConsultation ready = inStatus("READY_FOR_DOCTOR");
        assertThat(ready.isAwaitingHandoff()).isTrue();
        assertThat(ready.isSignable()).isFalse();
        assertThat(ready.isAbandonable()).isFalse();

        ClinicConsultation signed = completeDraft();
        signed.sign();
        assertThatThrownBy(() -> signed.transitionTo("READY_FOR_DOCTOR"))
                .isInstanceOf(IllegalStateException.class);
    }
}
