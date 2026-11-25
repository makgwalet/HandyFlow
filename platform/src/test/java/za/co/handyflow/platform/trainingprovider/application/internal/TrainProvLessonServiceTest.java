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
import za.co.handyflow.platform.trainingprovider.dto.LessonDtos.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainProvLessonServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID AUTHOR = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final LocalDate TODAY = LocalDate.of(2027, 6, 10);
    private static final String TEXT = "A reading that is long enough to count";

    @Mock TrainProvLessonRepository lessons;
    @Mock TrainProvLessonProgressRepository progress;
    @Mock TrainProvLearnerEnrolmentRepository enrolments;
    @Mock TrainProvLearnerRepository learners;
    @Mock TrainProvProgrammeRepository programmes;
    @Mock TrainProvProgrammeVersionRepository versions;
    @Mock TrainProvProgrammeModuleRepository modules;
    @Mock TrainProvNumberGenerator numbers;
    @InjectMocks TrainProvLessonService service;

    private TrainProvProgrammeVersion version;
    private UUID versionId;

    @BeforeEach
    void setUp() {
        versionId = UUID.randomUUID();
        version = mock(TrainProvProgrammeVersion.class);
        lenient().when(version.getId()).thenReturn(versionId);
        lenient().when(version.getStatus()).thenReturn("APPROVED");
        lenient().when(versions.findForTenant(any(), any())).thenReturn(Optional.of(version));
        lenient().when(numbers.nextLessonNumber(any())).thenReturn("LSN-00001");
    }

    private TrainProvLesson draft() {
        return TrainProvLesson.create(TENANT, "LSN-00001", versionId, null, "Intro", "READING", TEXT, null, 1, AUTHOR);
    }

    @Test
    void createsADraftLesson() {
        LessonView v = service.create(TENANT, new LessonRequest(versionId, null, "Intro", "READING", TEXT, null, 1), AUTHOR);
        assertThat(v.status()).isEqualTo("DRAFT");
        assertThat(v.lessonNo()).isEqualTo("LSN-00001");
        verify(lessons).save(any(TrainProvLesson.class));
    }

    @Test
    void refusesLessonOnSupersededVersionOrDuplicateTitle() {
        when(version.getStatus()).thenReturn("SUPERSEDED");
        assertThatThrownBy(() -> service.create(TENANT, new LessonRequest(versionId, null, "Intro", "READING", TEXT, null, 1), AUTHOR)).isInstanceOf(HandyFlowException.class);
        when(version.getStatus()).thenReturn("APPROVED");
        when(lessons.liveTitleTaken(any(), any(), any(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.create(TENANT, new LessonRequest(versionId, null, "Intro", "READING", TEXT, null, 1), AUTHOR))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("already has a live lesson");
    }

    @Test
    void approvalNeedsASecondPersonOrAReason() {
        TrainProvLesson l = draft();
        when(lessons.findForTenant(any(), any())).thenReturn(Optional.of(l));
        assertThatThrownBy(() -> service.approve(TENANT, l.getId(), null, AUTHOR)).isInstanceOf(HandyFlowException.class);
        assertThat(service.approve(TENANT, l.getId(), null, OTHER).status()).isEqualTo("APPROVED");
    }

    @Test
    void approvedLessonCannotBeEditedButCanBeRetiredWithReason() {
        TrainProvLesson l = draft();
        l.approve(OTHER, null);
        when(lessons.findForTenant(any(), any())).thenReturn(Optional.of(l));
        assertThatThrownBy(() -> service.update(TENANT, l.getId(), new LessonUpdate(null, "New", "READING", TEXT, null, 1), AUTHOR)).isInstanceOf(HandyFlowException.class);
        assertThatThrownBy(() -> service.retire(TENANT, l.getId(), "short", AUTHOR)).isInstanceOf(HandyFlowException.class);
        assertThat(service.retire(TENANT, l.getId(), "Replaced by a newer reading", AUTHOR).status()).isEqualTo("RETIRED");
    }

    private TrainProvLearnerEnrolment enrolment() {
        TrainProvLearnerEnrolment e = mock(TrainProvLearnerEnrolment.class);
        lenient().when(e.getId()).thenReturn(UUID.randomUUID());
        lenient().when(e.getStatus()).thenReturn("IN_PROGRESS");
        lenient().when(e.getStartDate()).thenReturn(LocalDate.of(2027, 6, 1));
        lenient().when(e.getProgrammeVersionId()).thenReturn(versionId);
        lenient().when(enrolments.findForTenant(any(), any())).thenReturn(Optional.of(e));
        return e;
    }

    @Test
    void finishesOnlyApprovedLessonsOfTheSameVersionOnce() {
        TrainProvLearnerEnrolment e = enrolment();
        TrainProvLesson l = draft();
        when(lessons.findForTenant(any(), any())).thenReturn(Optional.of(l));
        assertThatThrownBy(() -> service.finish(TENANT, new FinishRequest(e.getId(), l.getId(), TODAY, null), AUTHOR, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("approved lesson");
        l.approve(OTHER, null);
        ProgressRecord r = service.finish(TENANT, new FinishRequest(e.getId(), l.getId(), TODAY, "Read in class"), AUTHOR, TODAY);
        assertThat(r.status()).isEqualTo("FINISHED");
        when(progress.finishedExists(any(), any(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.finish(TENANT, new FinishRequest(e.getId(), l.getId(), TODAY, null), AUTHOR, TODAY)).hasMessageContaining("already marked");
    }

    @Test
    void refusesLessonFromAnotherVersion() {
        TrainProvLearnerEnrolment e = enrolment();
        TrainProvLesson other = TrainProvLesson.create(TENANT, "LSN-00009", UUID.randomUUID(), null, "Other", "READING", TEXT, null, 1, AUTHOR);
        other.approve(OTHER, null);
        when(lessons.findForTenant(any(), any())).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.finish(TENANT, new FinishRequest(e.getId(), other.getId(), TODAY, null), AUTHOR, TODAY)).hasMessageContaining("different programme version");
    }

    @Test
    void reversesWithAReasonAndCountsOnlyStandingFinishes() {
        TrainProvLearnerEnrolment e = enrolment();
        TrainProvLesson l = draft();
        l.approve(OTHER, null);
        TrainProvLessonProgress p = TrainProvLessonProgress.finish(TENANT, e.getId(), l.getId(), TODAY, null, AUTHOR);
        when(progress.findForTenant(any(), any())).thenReturn(Optional.of(p));
        assertThatThrownBy(() -> service.reverse(TENANT, p.getId(), "short", AUTHOR)).isInstanceOf(HandyFlowException.class);
        assertThat(service.reverse(TENANT, p.getId(), "Marked on the wrong enrolment", AUTHOR).status()).isEqualTo("REVERSED");
        when(lessons.search(any(), any(), any(), any())).thenReturn(List.of(l));
        when(progress.forEnrolment(any(), any())).thenReturn(List.of(p));
        assertThat(service.progress(TENANT, e.getId()).finished()).isZero();
    }
}
