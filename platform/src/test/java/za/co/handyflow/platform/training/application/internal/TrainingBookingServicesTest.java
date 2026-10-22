package za.co.handyflow.platform.training.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.hr.EmployeeSummary;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.training.domain.model.TrainingEnrollment;
import za.co.handyflow.platform.training.domain.model.TrainingSession;
import za.co.handyflow.platform.training.domain.repository.TrainingCourseRepository;
import za.co.handyflow.platform.training.domain.repository.TrainingEnrollmentRepository;
import za.co.handyflow.platform.training.domain.repository.TrainingSessionRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TrainingBookingServicesTest {

    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final UUID employeeId = UUID.randomUUID();
    private final TrainingEnrollmentRepository enrollments = mock(TrainingEnrollmentRepository.class);
    private final TrainingSessionRepository sessions = mock(TrainingSessionRepository.class);
    private final TrainingCourseRepository courses = mock(TrainingCourseRepository.class);
    private final HrFacade hr = mock(HrFacade.class);
    private final TrainingNotifier notifier = mock(TrainingNotifier.class);
    private final TrainingEnrollmentService enrolment = new TrainingEnrollmentService(enrollments, sessions, hr, notifier);
    private final TrainingSessionService sessionService = new TrainingSessionService(sessions, courses, enrollments, notifier);

    private TrainingSession session(Integer capacity, LocalDate start) {
        TrainingSession s = TrainingSession.create(tenant, UUID.randomUUID(), start, start.plusDays(1), "Head Office", "Trainer", capacity, null);
        when(sessions.findByTenantAndId(tenant, s.getId())).thenReturn(Optional.of(s));
        when(sessions.findByTenantAndIdForUpdate(tenant, s.getId())).thenReturn(Optional.of(s));
        return s;
    }

    private void employee(String status) {
        when(hr.findEmployeeById(tenant, employeeId)).thenReturn(Optional.of(
                new EmployeeSummary(employeeId, "E1", "Jane", "Dlamini", "Jane Dlamini", "jane@x.co.za", null, "Clerk", "Admin", status)));
    }

    private TrainingEnrollment booking(TrainingSession s) {
        TrainingEnrollment e = TrainingEnrollment.create(tenant, s.getId(), UUID.randomUUID(), "Sam", "E2", null);
        when(enrollments.findByTenantAndId(tenant, e.getId())).thenReturn(Optional.of(e));
        return e;
    }

    @Test
    void aTerminatedEmployeeCannotBeEnrolled() {
        TrainingSession s = session(10, LocalDate.now().plusDays(7));
        employee("TERMINATED");
        assertThatThrownBy(() -> enrolment.enroll(tenant, s.getId(), employeeId, null)).hasMessageContaining("terminated");
        verify(enrollments, never()).save(any());
    }

    @Test
    void enrollingLocksTheSessionAndTellsTheEmployee() {
        TrainingSession s = session(10, LocalDate.now().plusDays(7));
        employee("ACTIVE");
        when(enrollments.save(any())).thenAnswer(i -> i.getArgument(0));
        TrainingEnrollment e = enrolment.enroll(tenant, s.getId(), employeeId, null);
        verify(sessions).findByTenantAndIdForUpdate(tenant, s.getId());
        verify(notifier).enrolled(eq(tenant), eq(s), eq(e), any());
    }

    @Test
    void aFullSessionRefusesTheNextPerson() {
        TrainingSession s = session(1, LocalDate.now().plusDays(7));
        employee("ACTIVE");
        when(enrollments.countLiveBySession(tenant, s.getId())).thenReturn(1L);
        assertThatThrownBy(() -> enrolment.enroll(tenant, s.getId(), employeeId, null)).hasMessageContaining("capacity");
    }

    @Test
    void aNoShowCannotBePassed() {
        TrainingSession s = session(10, LocalDate.now().minusDays(1));
        TrainingEnrollment e = booking(s);
        e.markNoShow();
        assertThatThrownBy(() -> enrolment.complete(tenant, e.getId(), new BigDecimal("90"), true)).hasMessageContaining("enrolled or has attended");
    }

    @Test
    void attendanceCannotBeRecordedBeforeTheSessionStarts() {
        TrainingSession s = session(10, LocalDate.now().plusDays(5));
        TrainingEnrollment e = booking(s);
        assertThatThrownBy(() -> enrolment.markAttended(tenant, e.getId())).hasMessageContaining("has not started");
    }

    @Test
    void aSessionCannotBeCompletedWithSomeoneStillEnrolled() {
        TrainingSession s = session(10, LocalDate.now().minusDays(1));
        s.start();
        TrainingEnrollment e = booking(s);
        when(enrollments.findAllForSession(tenant, s.getId())).thenReturn(List.of(e));
        assertThatThrownBy(() -> sessionService.complete(tenant, s.getId())).hasMessageContaining("Record attendance");
        assertThat(s.getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void cancellingASessionReleasesThoseWaitingAndTellsThem() {
        TrainingSession s = session(10, LocalDate.now().plusDays(5));
        TrainingEnrollment waiting = booking(s);
        TrainingEnrollment attended = booking(s);
        attended.markAttended();
        when(enrollments.findAllForSession(tenant, s.getId())).thenReturn(List.of(waiting, attended));
        when(sessions.save(any())).thenAnswer(i -> i.getArgument(0));
        sessionService.cancel(tenant, s.getId(), "Trainer ill");
        assertThat(s.getStatus()).isEqualTo("CANCELLED");
        assertThat(waiting.getStatus()).isEqualTo("CANCELLED");
        assertThat(waiting.getCancelReason()).contains("Trainer ill");
        assertThat(attended.getStatus()).isEqualTo("ATTENDED");
        verify(notifier).cancelled(eq(tenant), eq(s), eq(List.of(waiting)));
    }

    @Test
    void capacityCannotBeCutBelowThePeopleAlreadyBooked() {
        TrainingSession s = session(10, LocalDate.now().plusDays(5));
        when(enrollments.countLiveBySession(tenant, s.getId())).thenReturn(6L);
        assertThatThrownBy(() -> sessionService.update(tenant, s.getId(), "Room", "T", 4, null)).hasMessageContaining("already 6");
    }

    @Test
    void movingASessionTellsThoseBooked() {
        TrainingSession s = session(10, LocalDate.now().plusDays(5));
        TrainingEnrollment e = booking(s);
        when(enrollments.findAllForSession(tenant, s.getId())).thenReturn(List.of(e));
        when(sessions.save(any())).thenAnswer(i -> i.getArgument(0));
        sessionService.reschedule(tenant, s.getId(), LocalDate.now().plusDays(9), LocalDate.now().plusDays(10));
        verify(notifier).moved(eq(tenant), eq(s), eq(List.of(e)));
    }
}
