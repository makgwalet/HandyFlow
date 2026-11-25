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
import za.co.handyflow.platform.trainingprovider.dto.DocumentDtos.*;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainProvDocumentServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final LocalDate TODAY = LocalDate.of(2027, 6, 10);

    @Mock TrainProvLearnerDocumentRepository documents;
    @Mock TrainProvLearnerEnrolmentRepository enrolments;
    @Mock TrainProvLearnerRepository learners;
    @Mock TrainProvProgrammeRepository programmes;
    @Mock TrainProvProgrammeVersionRepository versions;
    @Mock TrainProvCompletionRepository completions;
    @Mock TrainProvNumberGenerator numbers;
    @InjectMocks TrainProvDocumentService service;

    private TrainProvLearnerEnrolment enrolment;
    private TrainProvLearner learner;
    private TrainProvProgramme programme;
    private TrainProvCompletion completion;

    @BeforeEach
    void setUp() {
        enrolment = mock(TrainProvLearnerEnrolment.class);
        learner = mock(TrainProvLearner.class);
        programme = mock(TrainProvProgramme.class);
        completion = mock(TrainProvCompletion.class);
        UUID e = UUID.randomUUID();
        lenient().when(enrolment.getId()).thenReturn(e);
        lenient().when(enrolment.getLearnerId()).thenReturn(UUID.randomUUID());
        lenient().when(enrolment.getProgrammeId()).thenReturn(UUID.randomUUID());
        lenient().when(enrolment.getProgrammeVersionId()).thenReturn(UUID.randomUUID());
        lenient().when(enrolment.getStatus()).thenReturn("IN_PROGRESS");
        lenient().when(enrolment.getEnrolmentNo()).thenReturn("ENR-00001");
        lenient().when(learner.getFirstNames()).thenReturn("Thandi");
        lenient().when(learner.getSurname()).thenReturn("Dlamini");
        lenient().when(programme.getTitle()).thenReturn("Working at Heights");
        lenient().when(completion.getId()).thenReturn(UUID.randomUUID());
        lenient().when(completion.getOutcome()).thenReturn("COMPLETED");
        lenient().when(completion.getStatus()).thenReturn("RECORDED");
        lenient().when(completion.getOutcomeOn()).thenReturn(LocalDate.of(2027, 6, 5));
        lenient().when(enrolments.findForTenant(any(), any())).thenReturn(Optional.of(enrolment));
        lenient().when(learners.findForTenant(any(), any())).thenReturn(Optional.of(learner));
        lenient().when(programmes.findForTenant(any(), any())).thenReturn(Optional.of(programme));
        lenient().when(versions.findForTenant(any(), any())).thenReturn(Optional.empty());
        lenient().when(numbers.nextDocumentNumber(any())).thenReturn("DOC-00001");
    }

    @Test
    void issuesCompletionLetterFromStandingOutcome() {
        when(completions.findLiveForEnrolment(any(), any())).thenReturn(Optional.of(completion));
        DocumentView v = service.issue(TENANT, new IssueRequest(enrolment.getId(), "COMPLETION_LETTER"), USER, TODAY);
        assertThat(v.documentNo()).isEqualTo("DOC-00001");
        assertThat(v.body()).contains("Thandi Dlamini");
        assertThat(v.disclaimer()).contains("QCTO");
        assertThat(v.completionStillStands()).isTrue();
        verify(documents).save(any(TrainProvLearnerDocument.class));
    }

    @Test
    void refusesLetterWithoutCompletion() {
        when(completions.findLiveForEnrolment(any(), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.issue(TENANT, new IssueRequest(enrolment.getId(), "COMPLETION_LETTER"), USER, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("standing completed outcome");
        verify(documents, never()).save(any());
    }

    @Test
    void issuesStatementWithoutCompletion() {
        when(completions.findLiveForEnrolment(any(), any())).thenReturn(Optional.empty());
        DocumentView v = service.issue(TENANT, new IssueRequest(enrolment.getId(), "PARTICIPATION_STATEMENT"), USER, TODAY);
        assertThat(v.body()).contains("participation only");
    }

    @Test
    void refusesSecondLiveDocumentOfSameType() {
        when(completions.findLiveForEnrolment(any(), any())).thenReturn(Optional.empty());
        when(documents.liveExists(any(), any(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.issue(TENANT, new IssueRequest(enrolment.getId(), "PARTICIPATION_STATEMENT"), USER, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("already has");
    }

    @Test
    void refusesUnknownType() {
        when(completions.findLiveForEnrolment(any(), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.issue(TENANT, new IssueRequest(enrolment.getId(), "CERTIFICATE"), USER, TODAY))
                .isInstanceOf(HandyFlowException.class);
    }

    @Test
    void handsOverOnceAndVoidsWithReason() {
        TrainProvLearnerDocument d = TrainProvLearnerDocument.issue(TENANT, "DOC-00002", "PARTICIPATION_STATEMENT", enrolment.getId(), UUID.randomUUID(), null,
                "Thandi Dlamini", "Working at Heights", 1, TODAY.minusDays(2), null, USER);
        when(documents.findForTenant(any(), any())).thenReturn(Optional.of(d));
        DocumentView v = service.handOver(TENANT, d.getId(), new HandOverRequest(TODAY, "The learner", "IN_PERSON"), USER, TODAY);
        assertThat(v.handedOverTo()).isEqualTo("The learner");
        assertThatThrownBy(() -> service.handOver(TENANT, d.getId(), new HandOverRequest(TODAY, "The learner", "IN_PERSON"), USER, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("already been handed over");
        assertThatThrownBy(() -> service.voidDocument(TENANT, d.getId(), "short", USER)).isInstanceOf(HandyFlowException.class);
        assertThat(service.voidDocument(TENANT, d.getId(), "Issued to the wrong enrolment", USER).status()).isEqualTo("VOID");
    }
}
