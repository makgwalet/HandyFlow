package za.co.handyflow.platform.projects.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.projects.domain.model.ProjectTask;
import za.co.handyflow.platform.projects.domain.repository.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.projects.domain.model.TimeEntry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Approving and rejecting logged time: once only, never your own, and rejected hours leave the task's total. */
class TimeApprovalTest {

    TenantId tenant = TenantId.generate();
    UUID worker = UUID.randomUUID(), approver = UUID.randomUUID(), project = UUID.randomUUID(), taskId = UUID.randomUUID();

    ProjectTaskRepository tasks = mock(ProjectTaskRepository.class);
    ProjectTimeEntryRepository time = mock(ProjectTimeEntryRepository.class);
    ProjectAccessGuard guard = mock(ProjectAccessGuard.class);

    ProjectService service() {
        return new ProjectService(mock(ProjectRepository.class), tasks, mock(ProjectRiskRepository.class), time,
                null, null, guard, null, null, mock(ProjectBudgetLineRepository.class));
    }

    TimeEntry entry() {
        TimeEntry t = TimeEntry.create(tenant.getValue(), project, taskId, worker, "Thabo", LocalDate.now(), new BigDecimal("3.5"), "Shuttering", null, null);
        when(time.findByTenantAndId(tenant.getValue(), t.getId())).thenReturn(Optional.of(t));
        when(time.save(any(TimeEntry.class))).thenAnswer(i -> i.getArgument(0));
        return t;
    }

    @Test
    void anApproverApprovesSomeoneElsesTimeOnce() {
        TimeEntry t = entry();
        TimeEntry out = service().approveTime(tenant, t.getId(), approver, true);
        assertEquals("APPROVED", out.getStatus());
        assertEquals(approver, out.getApprovedBy());
        verify(guard).requireView(tenant.getValue(), project);
        HandyFlowException again = assertThrows(HandyFlowException.class, () -> service().approveTime(tenant, t.getId(), approver, false));
        assertEquals("TIME_ALREADY_DECIDED", again.getErrorCode());
    }

    @Test
    void nobodyDecidesTheirOwnTime() {
        TimeEntry t = entry();
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service().approveTime(tenant, t.getId(), worker, true));
        assertEquals("TIME_SELF_APPROVAL", e.getErrorCode());
        assertThrows(HandyFlowException.class, () -> service().approveTime(tenant, t.getId(), worker, false));
        assertEquals("SUBMITTED", t.getStatus());
    }

    @Test
    void rejectingTakesTheHoursOffTheTask() {
        TimeEntry t = entry();
        ProjectTask task = mock(ProjectTask.class);
        when(task.getId()).thenReturn(taskId);
        when(tasks.findByTenantAndId(tenant.getValue(), taskId)).thenReturn(Optional.of(task));
        when(time.sumHoursByTask(taskId)).thenReturn(new BigDecimal("1.0"));
        service().approveTime(tenant, t.getId(), approver, false);
        assertEquals("REJECTED", t.getStatus());
        verify(task).setActualHours(new BigDecimal("1.0"));
        verify(tasks).save(task);
    }

    @Test
    void approvingLeavesTheTaskAlone() {
        TimeEntry t = entry();
        service().approveTime(tenant, t.getId(), approver, true);
        verifyNoInteractions(tasks);
    }
}
