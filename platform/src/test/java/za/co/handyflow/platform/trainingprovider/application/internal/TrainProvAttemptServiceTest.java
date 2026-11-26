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
import za.co.handyflow.platform.trainingprovider.dto.AttemptDtos.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainProvAttemptServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID AUTHOR = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID VERSION = UUID.randomUUID();
    private static final UUID PROGRAMME = UUID.randomUUID();
    private static final UUID COHORT = UUID.randomUUID();
    private static final UUID ENROLMENT = UUID.randomUUID();
    private static final UUID ASSESSOR = UUID.randomUUID();
    private static final UUID APPOINTMENT = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2027, 3, 10);

    @Mock TrainProvAssessmentAttemptRepository attempts;
    @Mock TrainProvAssessmentDecisionRepository decisions;
    @Mock TrainProvAssessmentAttemptEventRepository events;
    @Mock TrainProvAssessmentInstrumentRepository instruments;
    @Mock TrainProvCohortRepository cohorts;
    @Mock TrainProvCohortMemberRepository members;
    @Mock TrainProvLearnerEnrolmentRepository enrolments;
    @Mock TrainProvLearnerRepository learners;
    @Mock TrainProvPractitionerRepository practitioners;
    @Mock TrainProvPractitionerAppointmentRepository appointments;
    @Mock TrainProvAppointmentSnapshotRepository snapshots;
    @Mock TrainProvAuthorityRepository authorities;
    @Mock TrainProvNumberGenerator numbers;
    @InjectMocks TrainProvAttemptService service;

    private TrainProvAssessmentInstrument instrument;
    private TrainProvCohortMember member;
    private TrainProvPractitioner assessor;
    private TrainProvPractitionerAppointment appointment;

    @BeforeEach
    void setUp() {
        instrument = TrainProvAssessmentInstrument.create(TENANT, "ASI-00001", VERSION, null, "Safety theory test", "KNOWLEDGE", new BigDecimal("50"), null, true, AUTHOR);
        instrument.approve(USER, null);
        member = mock(TrainProvCohortMember.class);
        lenient().when(member.getId()).thenReturn(UUID.randomUUID());
        lenient().when(member.getCohortId()).thenReturn(COHORT);
        lenient().when(member.getEnrolmentId()).thenReturn(ENROLMENT);
        lenient().when(member.getStatus()).thenReturn("ACTIVE");
        TrainProvCohort cohort = mock(TrainProvCohort.class);
        lenient().when(cohort.getId()).thenReturn(COHORT);
        lenient().when(cohort.getProgrammeId()).thenReturn(PROGRAMME);
        lenient().when(cohort.getProgrammeVersionId()).thenReturn(VERSION);
        lenient().when(cohort.getStatus()).thenReturn("RUNNING");
        lenient().when(cohort.getStartDate()).thenReturn(LocalDate.of(2027, 2, 1));
        assessor = mock(TrainProvPractitioner.class);
        lenient().when(assessor.getId()).thenReturn(ASSESSOR);
        lenient().when(assessor.getStatus()).thenReturn("ACTIVE");
        lenient().when(assessor.getUserId()).thenReturn(USER);
        lenient().when(assessor.fullName()).thenReturn("Probe Person");
        appointment = mock(TrainProvPractitionerAppointment.class);
        lenient().when(appointment.getId()).thenReturn(APPOINTMENT);
        lenient().when(appointment.getPractitionerId()).thenReturn(ASSESSOR);
        lenient().when(appointment.getRole()).thenReturn("ASSESSOR");
        lenient().when(appointment.getProgrammeId()).thenReturn(PROGRAMME);
        lenient().when(appointments.findForTenant(TENANT, APPOINTMENT)).thenReturn(Optional.of(appointment));
        lenient().when(appointment.standingOn(any())).thenReturn(PractitionerRules.Standing.VERIFIED_IN_FORCE);
        lenient().when(instruments.findForTenant(TENANT, instrument.getId())).thenReturn(Optional.of(instrument));
        lenient().when(members.findForTenant(TENANT, member.getId())).thenReturn(Optional.of(member));
        lenient().when(cohorts.findForTenant(TENANT, COHORT)).thenReturn(Optional.of(cohort));
        lenient().when(practitioners.findForTenant(TENANT, ASSESSOR)).thenReturn(Optional.of(assessor));
        lenient().when(appointments.findLiveForProgramme(TENANT, PROGRAMME, "ASSESSOR")).thenReturn(List.of(appointment));
        lenient().when(attempts.findForMemberInstrument(any(), any(), any())).thenReturn(List.of());
        lenient().when(decisions.findForAttempt(any(), any())).thenReturn(Optional.empty());
        lenient().when(enrolments.findForTenant(any(), any())).thenReturn(Optional.empty());
        lenient().when(events.findForAttempt(any(), any())).thenReturn(List.of());
        lenient().when(numbers.nextAttemptNumber(TENANT)).thenReturn("ATT-00001");
        lenient().when(decisions.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private RecordAttempt request(BigDecimal mark) {
        return new RecordAttempt(instrument.getId(), member.getId(), TODAY, ASSESSOR, mark, null);
    }

    private TrainProvAssessmentAttempt stored() {
        TrainProvAssessmentAttempt a = TrainProvAssessmentAttempt.create(TENANT, "ATT-00001", instrument.getId(), member.getId(), COHORT, ENROLMENT, 1, TODAY, ASSESSOR,
                APPOINTMENT, new BigDecimal("30"), null, USER);
        when(attempts.findForTenant(TENANT, a.getId())).thenReturn(Optional.of(a));
        return a;
    }

    @Test
    void recordsAnAttemptWithoutADecision() {
        AttemptView v = service.record(TENANT, request(new BigDecimal("32")), USER, TODAY);
        assertThat(v.status()).isEqualTo("RECORDED");
        assertThat(v.attemptSeq()).isEqualTo(1);
        assertThat(v.decision()).isNull();
        verify(decisions, never()).save(any());
        verify(events).save(any(TrainProvAssessmentAttemptEvent.class));
    }

    @Test
    void refusesAMarkAboveTheMaximumAsABadRequest() {
        assertThatThrownBy(() -> service.record(TENANT, request(new BigDecimal("51")), USER, TODAY)).isInstanceOf(HandyFlowException.class).hasMessageContaining("maximum");
        verify(attempts, never()).save(any());
    }

    @Test
    void refusesAnAssessorWithoutACheckedAppointmentOnTheDay() {
        when(appointment.standingOn(any())).thenReturn(PractitionerRules.Standing.NOT_VERIFIED);
        assertThatThrownBy(() -> service.record(TENANT, request(new BigDecimal("32")), USER, TODAY)).isInstanceOf(HandyFlowException.class);
        verify(attempts, never()).save(any());
    }

    @Test
    void refusesAPersonWhoIsNotAnAssessorForTheProgramme() {
        when(appointments.findLiveForProgramme(TENANT, PROGRAMME, "ASSESSOR")).thenReturn(List.of());
        assertThatThrownBy(() -> service.record(TENANT, request(new BigDecimal("32")), USER, TODAY)).isInstanceOf(HandyFlowException.class).hasMessageContaining("no assessor appointment");
    }

    @Test
    void refusesASecondAttemptWhileOneWaitsForADecision() {
        TrainProvAssessmentAttempt open = stored();
        when(attempts.findForMemberInstrument(TENANT, instrument.getId(), member.getId())).thenReturn(List.of(open));
        assertThatThrownBy(() -> service.record(TENANT, request(new BigDecimal("32")), USER, TODAY)).isInstanceOf(HandyFlowException.class).hasMessageContaining("waiting for a decision");
    }

    @Test
    void allowsARetakeAfterNotYetCompetentAndNumbersItNext() {
        TrainProvAssessmentAttempt first = stored();
        when(attempts.findForMemberInstrument(TENANT, instrument.getId(), member.getId())).thenReturn(List.of(first));
        when(decisions.findForAttempt(TENANT, first.getId())).thenReturn(Optional.of(TrainProvAssessmentDecision.of(TENANT, first.getId(), "NOT_YET_COMPETENT", "Missed two steps", USER, TODAY)));
        assertThat(service.record(TENANT, request(new BigDecimal("40")), USER, TODAY).attemptSeq()).isEqualTo(2);
    }

    @Test
    void refusesAnotherAttemptAfterCompetent() {
        TrainProvAssessmentAttempt first = stored();
        when(attempts.findForMemberInstrument(TENANT, instrument.getId(), member.getId())).thenReturn(List.of(first));
        when(decisions.findForAttempt(TENANT, first.getId())).thenReturn(Optional.of(TrainProvAssessmentDecision.of(TENANT, first.getId(), "COMPETENT", "Met every criterion", USER, TODAY)));
        assertThatThrownBy(() -> service.record(TENANT, request(new BigDecimal("40")), USER, TODAY)).isInstanceOf(HandyFlowException.class).hasMessageContaining("already been found competent");
    }

    @Test
    void decisionIsSeparateFromTheMarkAndKeepsItsBasis() {
        TrainProvAssessmentAttempt a = stored();
        AttemptView v = service.decide(TENANT, a.getId(), new DecisionRequest("NOT_YET_COMPETENT", "Missed two critical steps in the practical"), USER, TODAY);
        assertThat(v.decision().decision()).isEqualTo("NOT_YET_COMPETENT");
        assertThat(v.mark()).isEqualByComparingTo("30");
        verify(decisions).save(any(TrainProvAssessmentDecision.class));
    }

    @Test
    void decisionNeedsABasis() {
        TrainProvAssessmentAttempt a = stored();
        assertThatThrownBy(() -> service.decide(TENANT, a.getId(), new DecisionRequest("COMPETENT", "ok"), USER, TODAY)).isInstanceOf(HandyFlowException.class);
        verify(decisions, never()).save(any());
    }

    @Test
    void onlyTheLinkedAssessorDecides() {
        when(assessor.getUserId()).thenReturn(AUTHOR);
        TrainProvAssessmentAttempt a = stored();
        assertThatThrownBy(() -> service.decide(TENANT, a.getId(), new DecisionRequest("COMPETENT", "Met every criterion on the checklist"), USER, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("Only the assessor");
        assertThat(service.decide(TENANT, a.getId(), new DecisionRequest("COMPETENT", "Met every criterion on the checklist"), AUTHOR, TODAY).decision()).isNotNull();
    }

    @Test
    void aDecisionIsWrittenOnce() {
        TrainProvAssessmentAttempt a = stored();
        when(decisions.findForAttempt(TENANT, a.getId())).thenReturn(Optional.of(TrainProvAssessmentDecision.of(TENANT, a.getId(), "COMPETENT", "Met every criterion", USER, TODAY)));
        assertThatThrownBy(() -> service.decide(TENANT, a.getId(), new DecisionRequest("NOT_YET_COMPETENT", "Changed my mind about this"), USER, TODAY)).isInstanceOf(HandyFlowException.class);
    }

    @Test
    void markCannotChangeAfterADecision() {
        TrainProvAssessmentAttempt a = stored();
        when(decisions.findForAttempt(TENANT, a.getId())).thenReturn(Optional.of(TrainProvAssessmentDecision.of(TENANT, a.getId(), "COMPETENT", "Met every criterion", USER, TODAY)));
        assertThatThrownBy(() -> service.correctMark(TENANT, a.getId(), new CorrectMark(new BigDecimal("45"), "Added up wrongly the first time"), USER)).isInstanceOf(HandyFlowException.class);
        assertThat(a.getMark()).isEqualByComparingTo("30");
    }

    @Test
    void markCorrectionIsRecordedWithItsReason() {
        TrainProvAssessmentAttempt a = stored();
        service.correctMark(TENANT, a.getId(), new CorrectMark(new BigDecimal("35"), "Added up wrongly the first time"), USER);
        assertThat(a.getMark()).isEqualByComparingTo("35");
        verify(events).save(any(TrainProvAssessmentAttemptEvent.class));
    }

    @Test
    void voidingKeepsTheRowAndNeedsAReason() {
        TrainProvAssessmentAttempt a = stored();
        assertThatThrownBy(() -> service.voidAttempt(TENANT, a.getId(), "short", USER)).isInstanceOf(HandyFlowException.class);
        assertThat(service.voidAttempt(TENANT, a.getId(), "Recorded against the wrong learner", USER).status()).isEqualTo("VOID");
        verify(attempts, never()).delete(any());
    }

    @Test
    void recordingKeepsACopyOfTheAssessorAndTheirAppointmentAsTheyStoodOnTheDay() {
        service.record(TENANT, request(new BigDecimal("32")), USER, TODAY);
        org.mockito.ArgumentCaptor<TrainProvAppointmentSnapshot> c = org.mockito.ArgumentCaptor.forClass(TrainProvAppointmentSnapshot.class);
        verify(snapshots).save(c.capture());
        assertThat(c.getValue().getSubjectType()).isEqualTo("ATTEMPT");
        assertThat(c.getValue().getFullName()).isEqualTo("Probe Person");
        assertThat(c.getValue().getAppointmentId()).isEqualTo(APPOINTMENT);
        assertThat(c.getValue().getStanding()).isEqualTo("VERIFIED_IN_FORCE");
        assertThat(c.getValue().getStandingOn()).isEqualTo(TODAY);
        assertThat(c.getValue().isBackfilled()).isFalse();
    }

    @Test
    void aDecisionAlsoKeepsACopyAsTheyStoodOnTheDecisionDay() {
        TrainProvAssessmentAttempt a = stored();
        service.decide(TENANT, a.getId(), new DecisionRequest("COMPETENT", "Met every criterion on the checklist"), USER, TODAY);
        org.mockito.ArgumentCaptor<TrainProvAppointmentSnapshot> c = org.mockito.ArgumentCaptor.forClass(TrainProvAppointmentSnapshot.class);
        verify(snapshots).save(c.capture());
        assertThat(c.getValue().getSubjectType()).isEqualTo("DECISION");
        assertThat(c.getValue().getSubjectId()).isEqualTo(a.getId());
    }

    @Test
    void anAssessorWithNoLoginNeedsANoteOnHowTheirDecisionReachedUs() {
        when(assessor.getUserId()).thenReturn(null);
        TrainProvAssessmentAttempt a = stored();
        assertThatThrownBy(() -> service.decide(TENANT, a.getId(), new DecisionRequest("COMPETENT", "Met every criterion on the checklist"), USER, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("no login");
        verify(decisions, never()).save(any());
        AttemptView v = service.decide(TENANT, a.getId(), new DecisionRequest("COMPETENT", "Met every criterion on the checklist", "Signed result sheet handed over on 10 March"), USER, TODAY);
        assertThat(v.decision()).isNotNull();
        org.mockito.ArgumentCaptor<TrainProvAssessmentDecision> c = org.mockito.ArgumentCaptor.forClass(TrainProvAssessmentDecision.class);
        verify(decisions).save(c.capture());
        assertThat(c.getValue().getRecordedForAssessor()).contains("Signed result sheet");
    }

    @Test
    void aDecisionIsRefusedWhenTheAppointmentIsNoLongerInForceOnTheDecisionDay() {
        when(appointment.standingOn(any())).thenReturn(PractitionerRules.Standing.WITHDRAWN);
        TrainProvAssessmentAttempt a = stored();
        assertThatThrownBy(() -> service.decide(TENANT, a.getId(), new DecisionRequest("COMPETENT", "Met every criterion on the checklist"), USER, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("not checked and in force today");
        verify(decisions, never()).save(any());
        verify(snapshots, never()).save(any());
    }

    @Test
    void aRenewedAppointmentLetsTheDecisionBeMade() {
        TrainProvPractitionerAppointment renewed = mock(TrainProvPractitionerAppointment.class);
        when(renewed.getId()).thenReturn(UUID.randomUUID());
        when(renewed.getPractitionerId()).thenReturn(ASSESSOR);
        when(renewed.getRole()).thenReturn("ASSESSOR");
        when(renewed.getProgrammeId()).thenReturn(PROGRAMME);
        when(renewed.standingOn(any())).thenReturn(PractitionerRules.Standing.VERIFIED_IN_FORCE);
        when(appointment.standingOn(any())).thenReturn(PractitionerRules.Standing.EXPIRED);
        when(appointments.findLiveForProgramme(TENANT, PROGRAMME, "ASSESSOR")).thenReturn(List.of(renewed));
        TrainProvAssessmentAttempt a = stored();
        assertThat(service.decide(TENANT, a.getId(), new DecisionRequest("COMPETENT", "Met every criterion on the checklist"), USER, TODAY).decision()).isNotNull();
    }

    @Test
    void anAssessorsHistoryListsTheirAttempts() {
        TrainProvAssessmentAttempt a = stored();
        when(attempts.findForAssessor(TENANT, ASSESSOR)).thenReturn(List.of(a));
        assertThat(service.forAssessor(TENANT, ASSESSOR)).hasSize(1);
    }
}
