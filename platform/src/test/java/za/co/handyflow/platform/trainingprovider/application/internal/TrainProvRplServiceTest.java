package za.co.handyflow.platform.trainingprovider.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.*;
import za.co.handyflow.platform.trainingprovider.domain.repository.*;
import za.co.handyflow.platform.trainingprovider.domain.rules.PractitionerRules;
import za.co.handyflow.platform.trainingprovider.dto.RplDtos.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainProvRplServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final LocalDate TODAY = LocalDate.of(2027, 6, 10);
    private static final LocalDate START = LocalDate.of(2027, 6, 1);
    private static final UUID ASSESSOR = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID PROGRAMME = UUID.fromString("00000000-0000-0000-0000-0000000000d1");

    @Mock TrainProvRplApplicationRepository applications;
    @Mock TrainProvRplEvidenceRepository evidence;
    @Mock TrainProvLearnerEnrolmentRepository enrolments;
    @Mock TrainProvLearnerRepository learners;
    @Mock TrainProvProgrammeRepository programmes;
    @Mock TrainProvProgrammeModuleRepository modules;
    @Mock TrainProvPractitionerRepository practitioners;
    @Mock TrainProvPractitionerAppointmentRepository appointments;
    @Mock TrainProvAppointmentSnapshotRepository snapshots;
    @Mock TrainProvAuthorityRepository authorities;
    @Mock TrainProvNumberGenerator numbers;
    @InjectMocks TrainProvRplService service;

    private TrainProvPractitioner assessor;
    private TrainProvPractitionerAppointment rplAppointment;
    private TrainProvLearnerEnrolment enrolment;
    private TrainProvRplApplication app;

    @BeforeEach
    void setUp() {
        enrolment = mock(TrainProvLearnerEnrolment.class);
        lenient().when(enrolment.getId()).thenReturn(UUID.randomUUID());
        lenient().when(enrolment.getStatus()).thenReturn("IN_PROGRESS");
        lenient().when(enrolment.getStartDate()).thenReturn(START);
        lenient().when(enrolment.getLearnerId()).thenReturn(UUID.randomUUID());
        lenient().when(enrolment.getProgrammeId()).thenReturn(PROGRAMME);
        lenient().when(enrolments.findForTenant(any(), any())).thenReturn(Optional.of(enrolment));
        assessor = mock(TrainProvPractitioner.class);
        lenient().when(assessor.getId()).thenReturn(ASSESSOR);
        lenient().when(assessor.getStatus()).thenReturn("ACTIVE");
        lenient().when(assessor.getUserId()).thenReturn(USER);
        lenient().when(assessor.fullName()).thenReturn("Ms Assessor");
        lenient().when(practitioners.findForTenant(TENANT, ASSESSOR)).thenReturn(Optional.of(assessor));
        rplAppointment = mock(TrainProvPractitionerAppointment.class);
        lenient().when(rplAppointment.getId()).thenReturn(UUID.randomUUID());
        lenient().when(rplAppointment.getPractitionerId()).thenReturn(ASSESSOR);
        lenient().when(rplAppointment.getRole()).thenReturn("RPL_ASSESSOR");
        lenient().when(rplAppointment.standingOn(any())).thenReturn(PractitionerRules.Standing.VERIFIED_IN_FORCE);
        lenient().when(appointments.findLiveForProgramme(TENANT, PROGRAMME, "RPL_ASSESSOR")).thenReturn(List.of(rplAppointment));
        lenient().when(numbers.nextRplNumber(any())).thenReturn("RPL-00001");
        lenient().when(evidence.forApplication(any(), any())).thenReturn(List.of());
        app = TrainProvRplApplication.open(TENANT, "RPL-00001", enrolment.getId(), null, "Ten years running stores", START, USER);
        lenient().when(applications.findForTenant(any(), any())).thenReturn(Optional.of(app));
    }

    private TrainProvRplEvidence reviewed(String review) {
        TrainProvRplEvidence e = TrainProvRplEvidence.add(TENANT, app.getId(), "DOCUMENT", "Reference letter from employer", null, START, USER);
        e.review("OPEN", review, "Checked against the employer", TODAY, USER, TODAY);
        return e;
    }

    @Test
    void createsAnApplicationWhenNothingLiveExists() {
        when(applications.liveExists(any(), any(), any())).thenReturn(false);
        ApplicationView v = service.create(TENANT, new ApplicationRequest(enrolment.getId(), null, "Ten years running stores", START), USER, TODAY);
        assertThat(v.rplNo()).isEqualTo("RPL-00001");
        assertThat(v.status()).isEqualTo("OPEN");
        verify(applications).save(any());
    }

    @Test
    void refusesASecondLiveApplication() {
        when(applications.liveExists(any(), any(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.create(TENANT, new ApplicationRequest(enrolment.getId(), null, "Ten years running stores", START), USER, TODAY))
                .isInstanceOf(HandyFlowException.class);
        verify(applications, never()).save(any());
    }

    @Test
    void cannotDecideWithoutEvidenceOrWithUnreviewedEvidence() {
        DecisionRequest r = new DecisionRequest("RECOGNISED", "Evidence matches the outcomes", ASSESSOR, TODAY, false, null);
        assertThatThrownBy(() -> service.decide(TENANT, app.getId(), r, USER, TODAY)).isInstanceOf(HandyFlowException.class);
        TrainProvRplEvidence open = TrainProvRplEvidence.add(TENANT, app.getId(), "DOCUMENT", "Reference letter from employer", null, START, USER);
        when(evidence.forApplication(any(), any())).thenReturn(List.of(open));
        assertThatThrownBy(() -> service.decide(TENANT, app.getId(), r, USER, TODAY)).isInstanceOf(HandyFlowException.class);
        verify(applications, never()).save(any());
    }

    @Test
    void recognitionNeedsAcceptedEvidenceButNotRecognisedDoesNot() {
        when(evidence.forApplication(any(), any())).thenReturn(List.of(reviewed("QUERIED")));
        assertThatThrownBy(() -> service.decide(TENANT, app.getId(), new DecisionRequest("RECOGNISED", "Evidence matches the outcomes", ASSESSOR, TODAY, false, null), USER, TODAY))
                .isInstanceOf(HandyFlowException.class);
        ApplicationView v = service.decide(TENANT, app.getId(), new DecisionRequest("NOT_RECOGNISED", "Evidence did not match", ASSESSOR, TODAY, false, null), USER, TODAY);
        assertThat(v.status()).isEqualTo("DECIDED");
    }

    @Test
    void decisionWaitsForASecondPersonWhoMustDiffer() {
        when(evidence.forApplication(any(), any())).thenReturn(List.of(reviewed("ACCEPTED")));
        ApplicationView v = service.decide(TENANT, app.getId(), new DecisionRequest("RECOGNISED", "Evidence matches the outcomes", ASSESSOR, TODAY, false, null), USER, TODAY);
        assertThat(v.status()).isEqualTo("DECIDED");
        assertThat(v.stateLabel()).contains("Waiting for a second person");
        assertThatThrownBy(() -> service.confirm(TENANT, app.getId(), TODAY, USER, TODAY)).isInstanceOf(HandyFlowException.class);
        assertThat(service.confirm(TENANT, app.getId(), TODAY, OTHER, TODAY).status()).isEqualTo("CONFIRMED");
    }

    @Test
    void confirmingAloneNeedsAWrittenReason() {
        when(evidence.forApplication(any(), any())).thenReturn(List.of(reviewed("ACCEPTED")));
        assertThatThrownBy(() -> service.decide(TENANT, app.getId(), new DecisionRequest("RECOGNISED", "Evidence matches the outcomes", ASSESSOR, TODAY, true, "short"), USER, TODAY))
                .isInstanceOf(HandyFlowException.class);
        ApplicationView v = service.decide(TENANT, app.getId(), new DecisionRequest("RECOGNISED", "Evidence matches the outcomes", ASSESSOR, TODAY, true, "Only one qualified assessor on site"), USER, TODAY);
        assertThat(v.status()).isEqualTo("CONFIRMED");
        assertThat(v.stateLabel()).contains("written reason").contains("not a completion or a certificate");
    }

    @Test
    void evidenceCannotBeAddedOnceDecidedAndWithdrawNeedsAReason() {
        when(evidence.forApplication(any(), any())).thenReturn(List.of(reviewed("ACCEPTED")));
        service.decide(TENANT, app.getId(), new DecisionRequest("RECOGNISED", "Evidence matches the outcomes", ASSESSOR, TODAY, false, null), USER, TODAY);
        assertThatThrownBy(() -> service.addEvidence(TENANT, app.getId(), new EvidenceRequest("DOCUMENT", "Another reference letter", null, TODAY), USER, TODAY))
                .isInstanceOf(HandyFlowException.class);
        assertThatThrownBy(() -> service.withdraw(TENANT, app.getId(), "short", USER)).isInstanceOf(HandyFlowException.class);
        assertThat(service.withdraw(TENANT, app.getId(), "Entered against the wrong learner", USER).status()).isEqualTo("WITHDRAWN");
    }

    private void withAcceptedEvidence() {
        when(evidence.forApplication(any(), any())).thenReturn(List.of(reviewed("ACCEPTED")));
    }

    @Test
    void anOrdinaryAssessorAppointmentDoesNotAuthoriseRpl() {
        withAcceptedEvidence();
        when(appointments.findLiveForProgramme(TENANT, PROGRAMME, "RPL_ASSESSOR")).thenReturn(List.of());
        assertThatThrownBy(() -> service.decide(TENANT, app.getId(), new DecisionRequest("RECOGNISED", "Evidence matches the outcomes", ASSESSOR, TODAY, false, null), USER, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("no RPL assessor appointment").hasMessageContaining("ordinary assessor");
        verify(applications, never()).save(any());
        verify(snapshots, never()).save(any());
    }

    @Test
    void anRplAppointmentThatIsNotCheckedAndInForceIsRefused() {
        withAcceptedEvidence();
        when(rplAppointment.standingOn(any())).thenReturn(PractitionerRules.Standing.NOT_VERIFIED);
        assertThatThrownBy(() -> service.decide(TENANT, app.getId(), new DecisionRequest("RECOGNISED", "Evidence matches the outcomes", ASSESSOR, TODAY, false, null), USER, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("not checked and in force");
    }

    @Test
    void anInactiveAssessorIsRefused() {
        withAcceptedEvidence();
        when(assessor.getStatus()).thenReturn("INACTIVE");
        assertThatThrownBy(() -> service.decide(TENANT, app.getId(), new DecisionRequest("RECOGNISED", "Evidence matches the outcomes", ASSESSOR, TODAY, false, null), USER, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("inactive");
    }

    @Test
    void onlyTheLinkedAssessorCanRecordTheirOwnDecision() {
        withAcceptedEvidence();
        when(assessor.getUserId()).thenReturn(OTHER);
        assertThatThrownBy(() -> service.decide(TENANT, app.getId(), new DecisionRequest("RECOGNISED", "Evidence matches the outcomes", ASSESSOR, TODAY, false, null), USER, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("Only the assessor");
    }

    @Test
    void anAssessorWithNoLoginNeedsANoteAndItIsKept() {
        withAcceptedEvidence();
        when(assessor.getUserId()).thenReturn(null);
        assertThatThrownBy(() -> service.decide(TENANT, app.getId(), new DecisionRequest("RECOGNISED", "Evidence matches the outcomes", ASSESSOR, TODAY, false, null), USER, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("no login");
        ApplicationView v = service.decide(TENANT, app.getId(), new DecisionRequest("RECOGNISED", "Evidence matches the outcomes", ASSESSOR, TODAY, false, null,
                "Signed decision form handed over on 10 June"), USER, TODAY);
        assertThat(v.recordedForAssessor()).contains("Signed decision form");
        assertThat(v.assessorId()).isEqualTo(ASSESSOR);
        assertThat(v.legacyAssessorText()).isFalse();
    }

    @Test
    void aSnapshotOfTheAssessorAndTheirRplAppointmentIsKeptWithTheDecision() {
        withAcceptedEvidence();
        service.decide(TENANT, app.getId(), new DecisionRequest("RECOGNISED", "Evidence matches the outcomes", ASSESSOR, TODAY, false, null), USER, TODAY);
        org.mockito.ArgumentCaptor<TrainProvAppointmentSnapshot> c = org.mockito.ArgumentCaptor.forClass(TrainProvAppointmentSnapshot.class);
        verify(snapshots).save(c.capture());
        assertThat(c.getValue().getSubjectType()).isEqualTo("RPL");
        assertThat(c.getValue().getRole()).isEqualTo("RPL_ASSESSOR");
        assertThat(c.getValue().getStanding()).isEqualTo("VERIFIED_IN_FORCE");
    }
}
