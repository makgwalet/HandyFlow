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
import za.co.handyflow.platform.trainingprovider.dto.CompletionDtos.*;

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
class TrainProvCompletionServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID ENROLMENT = UUID.randomUUID();
    private static final UUID VERSION = UUID.randomUUID();
    private static final UUID PROGRAMME = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2027, 6, 1);
    private static final String BASIS = "All required assessments were completed and moderated";
    private static final String WHY = "Learner moved abroad; remaining task waived by the programme lead";

    @Mock TrainProvCompletionRepository completions;
    @Mock TrainProvLearnerEnrolmentRepository enrolments;
    @Mock TrainProvLearnerRepository learners;
    @Mock TrainProvProgrammeRepository programmes;
    @Mock TrainProvProgrammeVersionRepository versions;
    @Mock TrainProvAssessmentInstrumentRepository instruments;
    @Mock TrainProvAssessmentAttemptRepository attempts;
    @Mock TrainProvAssessmentDecisionRepository decisions;
    @Mock TrainProvAssessmentModerationRepository moderations;
    @Mock TrainProvNumberGenerator numbers;
    @InjectMocks TrainProvCompletionService service;

    private TrainProvAssessmentInstrument instrument;

    @BeforeEach
    void setUp() {
        TrainProvLearnerEnrolment e = mock(TrainProvLearnerEnrolment.class);
        lenient().when(e.getId()).thenReturn(ENROLMENT);
        lenient().when(e.getStatus()).thenReturn("IN_PROGRESS");
        lenient().when(e.getStartDate()).thenReturn(LocalDate.of(2027, 2, 1));
        lenient().when(e.getProgrammeVersionId()).thenReturn(VERSION);
        lenient().when(e.getProgrammeId()).thenReturn(PROGRAMME);
        lenient().when(e.getLearnerId()).thenReturn(UUID.randomUUID());
        lenient().when(enrolments.findForTenant(TENANT, ENROLMENT)).thenReturn(Optional.of(e));
        lenient().when(learners.findForTenant(any(), any())).thenReturn(Optional.empty());
        lenient().when(programmes.findForTenant(any(), any())).thenReturn(Optional.empty());
        lenient().when(versions.findForTenant(any(), any())).thenReturn(Optional.empty());
        instrument = TrainProvAssessmentInstrument.create(TENANT, "ASI-00001", VERSION, null, "Safety theory test", "KNOWLEDGE", new BigDecimal("50"), null, true, USER);
        lenient().when(instruments.search(TENANT, VERSION, null, "APPROVED")).thenReturn(List.of(instrument));
        lenient().when(attempts.search(any(), any(), any(), any())).thenReturn(List.of());
        lenient().when(completions.findLiveForEnrolment(any(), any())).thenReturn(Optional.empty());
        lenient().when(numbers.nextCompletionNumber(TENANT)).thenReturn("CMP-00001");
    }

    private TrainProvAssessmentAttempt attemptWith(String decision, String moderationOutcome) {
        TrainProvAssessmentAttempt a = TrainProvAssessmentAttempt.create(TENANT, "ATT-00001", instrument.getId(), UUID.randomUUID(), UUID.randomUUID(), ENROLMENT, 1,
                LocalDate.of(2027, 3, 1), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("40"), null, USER);
        when(attempts.search(TENANT, null, null, ENROLMENT)).thenReturn(List.of(a));
        if (decision != null) {
            when(decisions.findForAttempts(any(), any())).thenReturn(List.of(TrainProvAssessmentDecision.of(TENANT, a.getId(), decision, "Met every criterion", USER, LocalDate.of(2027, 3, 2))));
            if (moderationOutcome != null) {
                when(moderations.findForAttempt(TENANT, a.getId())).thenReturn(Optional.of(TrainProvAssessmentModeration.create(TENANT, "MOD-00001", a.getId(), UUID.randomUUID(),
                        UUID.randomUUID(), LocalDate.of(2027, 3, 3), moderationOutcome, "Sampled the evidence fully", USER)));
            } else {
                when(moderations.findForAttempt(TENANT, a.getId())).thenReturn(Optional.empty());
            }
        }
        return a;
    }

    @Test
    void readinessShowsNoAttemptAsAGapAndNeverCompletesAnyone() {
        Readiness r = service.readiness(TENANT, ENROLMENT);
        assertThat(r.openGaps()).isEqualTo(1);
        assertThat(r.rows().get(0).state()).isEqualTo("NO_ATTEMPT");
        verify(completions, never()).save(any());
    }

    @Test
    void readinessIsClearOnceDecidedAndModerated() {
        attemptWith("COMPETENT", "CONFIRMED");
        assertThat(service.readiness(TENANT, ENROLMENT).openGaps()).isZero();
    }

    @Test
    void aDecisionAwaitingModerationIsStillAGap() {
        attemptWith("COMPETENT", null);
        Readiness r = service.readiness(TENANT, ENROLMENT);
        assertThat(r.openGaps()).isEqualTo(1);
        assertThat(r.rows().get(0).state()).isEqualTo("WAITING_MODERATION");
    }

    @Test
    void completesWhenNothingIsOpen() {
        attemptWith("COMPETENT", "CONFIRMED");
        CompletionView v = service.record(TENANT, new RecordCompletion(ENROLMENT, "COMPLETED", TODAY, BASIS, null), USER, TODAY);
        assertThat(v.outcome()).isEqualTo("COMPLETED");
        assertThat(v.openGaps()).isZero();
        assertThat(v.gapsOverrideReason()).isNull();
    }

    @Test
    void refusesCompletionWithGapsAndNoReason() {
        assertThatThrownBy(() -> service.record(TENANT, new RecordCompletion(ENROLMENT, "COMPLETED", TODAY, BASIS, null), USER, TODAY)).isInstanceOf(HandyFlowException.class)
                .hasMessageContaining("not yet competent and moderated");
        verify(completions, never()).save(any());
    }

    @Test
    void completesWithGapsOnAWrittenReasonAndKeepsWhatWasOpen() {
        CompletionView v = service.record(TENANT, new RecordCompletion(ENROLMENT, "COMPLETED", TODAY, BASIS, WHY), USER, TODAY);
        assertThat(v.openGaps()).isEqualTo(1);
        assertThat(v.gapsOverrideReason()).isEqualTo(WHY);
        assertThat(v.gapsSnapshot()).contains("Safety theory test").contains("NO_ATTEMPT");
    }

    @Test
    void anUnneededReasonIsNotKept() {
        attemptWith("COMPETENT", "CONFIRMED");
        assertThat(service.record(TENANT, new RecordCompletion(ENROLMENT, "COMPLETED", TODAY, BASIS, WHY), USER, TODAY).gapsOverrideReason()).isNull();
    }

    @Test
    void didNotCompleteNeedsNoOverrideButKeepsTheSnapshot() {
        CompletionView v = service.record(TENANT, new RecordCompletion(ENROLMENT, "DID_NOT_COMPLETE", TODAY, "Learner left the programme in April", null), USER, TODAY);
        assertThat(v.outcome()).isEqualTo("DID_NOT_COMPLETE");
        assertThat(v.gapsSnapshot()).isNotBlank();
    }

    @Test
    void refusesASecondLiveOutcome() {
        when(completions.findLiveForEnrolment(TENANT, ENROLMENT)).thenReturn(Optional.of(mock(TrainProvCompletion.class)));
        assertThatThrownBy(() -> service.record(TENANT, new RecordCompletion(ENROLMENT, "DID_NOT_COMPLETE", TODAY, BASIS, null), USER, TODAY)).isInstanceOf(HandyFlowException.class);
    }

    @Test
    void withdrawalKeepsTheRowAndNeedsAReason() {
        TrainProvCompletion c = TrainProvCompletion.create(TENANT, "CMP-00001", ENROLMENT, "COMPLETED", TODAY, BASIS, 0, null, null, USER);
        when(completions.findForTenant(TENANT, c.getId())).thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.withdraw(TENANT, c.getId(), "short", USER)).isInstanceOf(HandyFlowException.class);
        assertThat(service.withdraw(TENANT, c.getId(), "Recorded against the wrong enrolment", USER).status()).isEqualTo("WITHDRAWN");
        verify(completions, never()).delete(any());
    }
}
