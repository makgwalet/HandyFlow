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
import za.co.handyflow.platform.trainingprovider.dto.ModerationDtos.*;

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
class TrainProvModerationServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID ENTERER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID DECIDER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID PROGRAMME = UUID.randomUUID();
    private static final UUID VERSION = UUID.randomUUID();
    private static final UUID COHORT = UUID.randomUUID();
    private static final UUID ASSESSOR = UUID.randomUUID();
    private static final UUID MODERATOR = UUID.randomUUID();
    private static final UUID APPOINTMENT = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2027, 3, 10);
    private static final String BASIS = "Sampled the evidence and checked it against the criteria";

    @Mock TrainProvAssessmentModerationRepository moderations;
    @Mock TrainProvAssessmentAttemptRepository attempts;
    @Mock TrainProvAssessmentDecisionRepository decisions;
    @Mock TrainProvAssessmentInstrumentRepository instruments;
    @Mock TrainProvCohortRepository cohorts;
    @Mock TrainProvLearnerEnrolmentRepository enrolments;
    @Mock TrainProvLearnerRepository learners;
    @Mock TrainProvPractitionerRepository practitioners;
    @Mock TrainProvPractitionerAppointmentRepository appointments;
    @Mock TrainProvNumberGenerator numbers;
    @InjectMocks TrainProvModerationService service;

    private TrainProvAssessmentAttempt attempt;
    private TrainProvPractitioner moderator;
    private TrainProvPractitionerAppointment appointment;
    private TrainProvAssessmentInstrument instrument;

    @BeforeEach
    void setUp() {
        instrument = TrainProvAssessmentInstrument.create(TENANT, "ASI-00001", VERSION, null, "Safety theory test", "KNOWLEDGE", new BigDecimal("50"), null, true, ENTERER);
        attempt = TrainProvAssessmentAttempt.create(TENANT, "ATT-00001", instrument.getId(), UUID.randomUUID(), COHORT, UUID.randomUUID(), 1, LocalDate.of(2027, 3, 4), ASSESSOR,
                UUID.randomUUID(), new BigDecimal("30"), null, ENTERER);
        TrainProvAssessmentDecision decision = TrainProvAssessmentDecision.of(TENANT, attempt.getId(), "COMPETENT", "Met every criterion on the checklist", DECIDER, LocalDate.of(2027, 3, 5));
        TrainProvCohort cohort = mock(TrainProvCohort.class);
        lenient().when(cohort.getProgrammeId()).thenReturn(PROGRAMME);
        moderator = mock(TrainProvPractitioner.class);
        lenient().when(moderator.getId()).thenReturn(MODERATOR);
        lenient().when(moderator.getStatus()).thenReturn("ACTIVE");
        lenient().when(moderator.getUserId()).thenReturn(null);
        appointment = mock(TrainProvPractitionerAppointment.class);
        lenient().when(appointment.getId()).thenReturn(APPOINTMENT);
        lenient().when(appointment.getPractitionerId()).thenReturn(MODERATOR);
        lenient().when(appointment.standingOn(any())).thenReturn(PractitionerRules.Standing.VERIFIED_IN_FORCE);
        lenient().when(attempts.findForTenant(TENANT, attempt.getId())).thenReturn(Optional.of(attempt));
        lenient().when(instruments.findForTenant(TENANT, instrument.getId())).thenReturn(Optional.of(instrument));
        lenient().when(cohorts.findForTenant(TENANT, COHORT)).thenReturn(Optional.of(cohort));
        lenient().when(practitioners.findForTenant(TENANT, MODERATOR)).thenReturn(Optional.of(moderator));
        lenient().when(practitioners.findForTenant(TENANT, ASSESSOR)).thenReturn(Optional.empty());
        lenient().when(appointments.findLiveForProgramme(TENANT, PROGRAMME, "MODERATOR")).thenReturn(List.of(appointment));
        lenient().when(decisions.findForAttempt(TENANT, attempt.getId())).thenReturn(Optional.of(decision));
        lenient().when(moderations.findForAttempt(any(), any())).thenReturn(Optional.empty());
        lenient().when(enrolments.findForTenant(any(), any())).thenReturn(Optional.empty());
        lenient().when(numbers.nextModerationNumber(TENANT)).thenReturn("MOD-00001");
    }

    private RecordModeration request(String outcome) {
        return new RecordModeration(MODERATOR, TODAY, outcome, BASIS);
    }

    @Test
    void confirmsADecisionWithoutChangingIt() {
        ModerationView v = service.record(TENANT, attempt.getId(), request("CONFIRMED"), ENTERER, TODAY);
        assertThat(v.outcome()).isEqualTo("CONFIRMED");
        assertThat(v.decision()).isEqualTo("COMPETENT");
        verify(moderations).save(any(TrainProvAssessmentModeration.class));
        verify(decisions, never()).save(any());
    }

    @Test
    void refusesTheAssessorAsModerator() {
        TrainProvPractitioner same = mock(TrainProvPractitioner.class);
        when(same.getId()).thenReturn(ASSESSOR);
        when(same.getStatus()).thenReturn("ACTIVE");
        when(same.getUserId()).thenReturn(null);
        when(practitioners.findForTenant(TENANT, ASSESSOR)).thenReturn(Optional.of(same));
        TrainProvPractitionerAppointment own = mock(TrainProvPractitionerAppointment.class);
        when(own.getPractitionerId()).thenReturn(ASSESSOR);
        when(own.standingOn(any())).thenReturn(PractitionerRules.Standing.VERIFIED_IN_FORCE);
        when(appointments.findLiveForProgramme(TENANT, PROGRAMME, "MODERATOR")).thenReturn(List.of(own));
        assertThatThrownBy(() -> service.record(TENANT, attempt.getId(), new RecordModeration(ASSESSOR, TODAY, "CONFIRMED", BASIS), ENTERER, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("assessor cannot moderate");
        verify(moderations, never()).save(any());
    }

    @Test
    void refusesAModeratorLinkedToTheUserWhoDecided() {
        when(moderator.getUserId()).thenReturn(DECIDER);
        assertThatThrownBy(() -> service.record(TENANT, attempt.getId(), request("CONFIRMED"), ENTERER, TODAY)).isInstanceOf(HandyFlowException.class).hasMessageContaining("took no part");
    }

    @Test
    void refusesAModeratorLinkedToTheUserWhoRecordedTheAttempt() {
        when(moderator.getUserId()).thenReturn(ENTERER);
        assertThatThrownBy(() -> service.record(TENANT, attempt.getId(), request("CONFIRMED"), DECIDER, TODAY)).isInstanceOf(HandyFlowException.class);
    }

    @Test
    void theDeciderCannotEnterTheModeration() {
        assertThatThrownBy(() -> service.record(TENANT, attempt.getId(), request("CONFIRMED"), DECIDER, TODAY)).isInstanceOf(HandyFlowException.class).hasMessageContaining("cannot enter");
    }

    @Test
    void refusesAModeratorWhoseAppointmentIsNotCheckedInForce() {
        when(appointment.standingOn(any())).thenReturn(PractitionerRules.Standing.NOT_VERIFIED);
        assertThatThrownBy(() -> service.record(TENANT, attempt.getId(), request("CONFIRMED"), ENTERER, TODAY)).isInstanceOf(HandyFlowException.class);
    }

    @Test
    void refusesWhenThereIsNoDecisionOrAlreadyModerated() {
        when(decisions.findForAttempt(TENANT, attempt.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.record(TENANT, attempt.getId(), request("CONFIRMED"), ENTERER, TODAY)).isInstanceOf(HandyFlowException.class).hasMessageContaining("no decision");
    }

    @Test
    void refusesAnInstrumentThatIsNotModerated() {
        TrainProvAssessmentInstrument plain = TrainProvAssessmentInstrument.create(TENANT, "ASI-00002", VERSION, null, "Practical", "PRACTICAL", null, null, false, ENTERER);
        TrainProvAssessmentAttempt a = TrainProvAssessmentAttempt.create(TENANT, "ATT-00002", plain.getId(), UUID.randomUUID(), COHORT, UUID.randomUUID(), 1, LocalDate.of(2027, 3, 4),
                ASSESSOR, UUID.randomUUID(), null, null, ENTERER);
        when(attempts.findForTenant(TENANT, a.getId())).thenReturn(Optional.of(a));
        when(instruments.findForTenant(TENANT, plain.getId())).thenReturn(Optional.of(plain));
        when(decisions.findForAttempt(TENANT, a.getId())).thenReturn(Optional.of(TrainProvAssessmentDecision.of(TENANT, a.getId(), "COMPETENT", "Met every criterion", DECIDER, LocalDate.of(2027, 3, 5))));
        assertThatThrownBy(() -> service.record(TENANT, a.getId(), request("CONFIRMED"), ENTERER, TODAY)).isInstanceOf(HandyFlowException.class).hasMessageContaining("not set to be moderated");
    }

    @Test
    void aQueryIsResolvedOnceInWriting() {
        TrainProvAssessmentModeration m = TrainProvAssessmentModeration.create(TENANT, "MOD-00001", attempt.getId(), MODERATOR, APPOINTMENT, TODAY, "QUERIED", BASIS, ENTERER);
        when(moderations.findForTenant(TENANT, m.getId())).thenReturn(Optional.of(m));
        assertThatThrownBy(() -> service.resolve(TENANT, m.getId(), "short", ENTERER)).isInstanceOf(HandyFlowException.class);
        ModerationView v = service.resolve(TENANT, m.getId(), "Attempt voided and the learner reassessed on a new day", ENTERER);
        assertThat(v.resolutionNote()).contains("reassessed");
        assertThat(v.resolvedAt()).isNotNull();
        assertThatThrownBy(() -> service.resolve(TENANT, m.getId(), "Trying to resolve it a second time", ENTERER)).isInstanceOf(HandyFlowException.class);
    }

    @Test
    void aConfirmationNeedsNoResolution() {
        TrainProvAssessmentModeration m = TrainProvAssessmentModeration.create(TENANT, "MOD-00001", attempt.getId(), MODERATOR, APPOINTMENT, TODAY, "CONFIRMED", BASIS, ENTERER);
        when(moderations.findForTenant(TENANT, m.getId())).thenReturn(Optional.of(m));
        assertThatThrownBy(() -> service.resolve(TENANT, m.getId(), "There is nothing to resolve here", ENTERER)).isInstanceOf(HandyFlowException.class);
    }
}
