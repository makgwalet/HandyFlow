package za.co.handyflow.platform.trainingprovider.application.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.*;
import za.co.handyflow.platform.trainingprovider.domain.repository.*;
import za.co.handyflow.platform.trainingprovider.dto.AttendanceDtos.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainProvAttendanceServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final LocalDate DAY = LocalDate.of(2027, 2, 10);

    @Mock TrainProvAttendanceRepository attendance;
    @Mock TrainProvAttendanceEventRepository history;
    @Mock TrainProvTimetableSlotRepository slots;
    @Mock TrainProvCohortRepository cohorts;
    @Mock TrainProvCohortMemberRepository members;
    @Mock TrainProvLearnerEnrolmentRepository enrolments;
    @Mock TrainProvLearnerRepository learners;
    @InjectMocks TrainProvAttendanceService service;

    private final UUID cohortId = UUID.randomUUID();

    private TrainProvCohort cohort() {
        TrainProvCohort c = mock(TrainProvCohort.class);
        when(c.getId()).thenReturn(cohortId);
        return c;
    }

    private TrainProvTimetableSlot slot() {
        return TrainProvTimetableSlot.create(TENANT, cohortId, "Module 1", DAY, LocalTime.of(9, 0), LocalTime.of(11, 0), null, null, null, USER);
    }

    private TrainProvCohortMember member() {
        return TrainProvCohortMember.create(TENANT, cohortId, UUID.randomUUID(), USER);
    }

    private void wire(TrainProvTimetableSlot s, TrainProvCohortMember... ms) {
        when(slots.findForTenant(TENANT, s.getId())).thenReturn(Optional.of(s));
        for (TrainProvCohortMember m : ms) {
            when(members.findForTenant(TENANT, m.getId())).thenReturn(Optional.of(m));
        }
    }

    @Test
    void attendanceIsRecordedForEveryLearnerInOneGo() {
        TrainProvTimetableSlot s = slot();
        TrainProvCohortMember a = member();
        TrainProvCohortMember b = member();
        wire(s, a, b);
        when(cohorts.findForTenant(TENANT, cohortId)).thenReturn(Optional.of(cohort()));
        service.record(TENANT, s.getId(), new RecordRequest(List.of(new Entry(a.getId(), "PRESENT", null), new Entry(b.getId(), "ABSENT", null))), USER, DAY);
        verify(attendance, times(2)).save(any(TrainProvAttendance.class));
        verify(history, times(2)).save(any(TrainProvAttendanceEvent.class));
    }

    @Test
    void oneBadEntryMeansNothingIsSaved() {
        TrainProvTimetableSlot s = slot();
        TrainProvCohortMember a = member();
        TrainProvCohortMember b = member();
        wire(s, a, b);
        assertThatThrownBy(() -> service.record(TENANT, s.getId(), new RecordRequest(List.of(new Entry(a.getId(), "PRESENT", null), new Entry(b.getId(), "EXCUSED", "sick"))), USER, DAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("excused");
        verify(attendance, never()).save(any());
    }

    @Test
    void attendanceCannotBeRecordedBeforeTheDayOrTwice() {
        TrainProvTimetableSlot s = slot();
        TrainProvCohortMember a = member();
        wire(s, a);
        assertThatThrownBy(() -> service.record(TENANT, s.getId(), new RecordRequest(List.of(new Entry(a.getId(), "PRESENT", null))), USER, DAY.minusDays(1)))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("on or after the day");
        when(attendance.findForSlotAndMember(TENANT, s.getId(), a.getId())).thenReturn(Optional.of(TrainProvAttendance.create(TENANT, s.getId(), a.getId(), "PRESENT", null, USER)));
        assertThatThrownBy(() -> service.record(TENANT, s.getId(), new RecordRequest(List.of(new Entry(a.getId(), "PRESENT", null))), USER, DAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("Correct it instead");
    }

    @Test
    void aLearnerOfAnotherCohortOrARemovedLearnerIsRefused() {
        TrainProvTimetableSlot s = slot();
        TrainProvCohortMember other = TrainProvCohortMember.create(TENANT, UUID.randomUUID(), UUID.randomUUID(), USER);
        TrainProvCohortMember removed = member();
        removed.remove("Moved to the next intake", USER);
        wire(s, other, removed);
        assertThatThrownBy(() -> service.record(TENANT, s.getId(), new RecordRequest(List.of(new Entry(other.getId(), "PRESENT", null))), USER, DAY))
                .hasMessageContaining("not in this slot's cohort");
        assertThatThrownBy(() -> service.record(TENANT, s.getId(), new RecordRequest(List.of(new Entry(removed.getId(), "PRESENT", null))), USER, DAY))
                .hasMessageContaining("removed");
    }

    @Test
    void aCancelledSlotTakesNoAttendanceAndTheSameLearnerTwiceInARequestIsRefused() {
        TrainProvTimetableSlot s = slot();
        TrainProvCohortMember a = member();
        wire(s, a);
        assertThatThrownBy(() -> service.record(TENANT, s.getId(), new RecordRequest(List.of(new Entry(a.getId(), "PRESENT", null), new Entry(a.getId(), "ABSENT", null))), USER, DAY))
                .hasMessageContaining("twice");
        s.cancel("Facilitator is ill", USER);
        assertThatThrownBy(() -> service.record(TENANT, s.getId(), new RecordRequest(List.of(new Entry(a.getId(), "PRESENT", null))), USER, DAY))
                .hasMessageContaining("cancelled");
    }

    @Test
    void aCorrectionNeedsAReasonAndLeavesAHistoryLine() {
        TrainProvTimetableSlot s = slot();
        TrainProvAttendance a = TrainProvAttendance.create(TENANT, s.getId(), UUID.randomUUID(), "ABSENT", null, USER);
        when(attendance.findForTenant(TENANT, a.getId())).thenReturn(Optional.of(a));
        assertThatThrownBy(() -> service.correct(TENANT, a.getId(), new CorrectRequest("PRESENT", null, "short"), USER, DAY)).isInstanceOf(HandyFlowException.class);
        assertThat(a.getStatus()).isEqualTo("ABSENT");
        when(slots.findForTenant(TENANT, s.getId())).thenReturn(Optional.of(s));
        when(cohorts.findForTenant(TENANT, cohortId)).thenReturn(Optional.of(cohort()));
        service.correct(TENANT, a.getId(), new CorrectRequest("PRESENT", null, "Learner arrived after the register closed"), USER, DAY);
        assertThat(a.getStatus()).isEqualTo("PRESENT");
        verify(history).save(any(TrainProvAttendanceEvent.class));
    }

    @Test
    void theSummaryCountsOnlySlotsAlreadyHeldAndNotCancelled() {
        LocalDate far = LocalDate.of(2099, 2, 10); // well after the learner joined (now), so every held slot is expected of them
        TrainProvCohort c = cohort();
        TrainProvCohortMember m = member();
        TrainProvTimetableSlot past1 = TrainProvTimetableSlot.create(TENANT, cohortId, "Day 1", far.minusDays(2), LocalTime.of(9, 0), LocalTime.of(11, 0), null, null, null, USER);
        TrainProvTimetableSlot past2 = TrainProvTimetableSlot.create(TENANT, cohortId, "Day 2", far.minusDays(1), LocalTime.of(9, 0), LocalTime.of(11, 0), null, null, null, USER);
        TrainProvTimetableSlot future = TrainProvTimetableSlot.create(TENANT, cohortId, "Day 3", far.plusDays(5), LocalTime.of(9, 0), LocalTime.of(11, 0), null, null, null, USER);
        TrainProvTimetableSlot cancelled = TrainProvTimetableSlot.create(TENANT, cohortId, "Day 4", far.minusDays(3), LocalTime.of(9, 0), LocalTime.of(11, 0), null, null, null, USER);
        cancelled.cancel("Public holiday declared", USER);
        when(cohorts.findForTenant(TENANT, cohortId)).thenReturn(Optional.of(c));
        when(slots.findForCohort(TENANT, cohortId)).thenReturn(List.of(cancelled, past1, past2, future));
        when(members.findForCohort(TENANT, cohortId)).thenReturn(List.of(m));
        when(attendance.findForCohort(TENANT, cohortId)).thenReturn(List.of(
                TrainProvAttendance.create(TENANT, past1.getId(), m.getId(), "PRESENT", null, USER),
                TrainProvAttendance.create(TENANT, cancelled.getId(), m.getId(), "ABSENT", null, USER)));
        CohortSummary sum = service.summary(TENANT, cohortId, far);
        assertThat(sum.rows()).hasSize(1);
        SummaryRow row = sum.rows().get(0);
        assertThat(row.expected()).isEqualTo(2);
        assertThat(row.attended()).isEqualTo(1);
        assertThat(row.absent()).isZero();
        assertThat(row.unrecorded()).isEqualTo(1);
        assertThat(row.ratePercent()).isEqualTo(100);
        assertThat(sum.note()).contains("not a result");
    }
}
