package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.model.HrEmployeePortalAccessGrant;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeePortalAccessGrantRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TerminationFollowUpTest {

    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final UUID actor = UUID.randomUUID();
    private final LocalDate last = LocalDate.of(2026, 9, 15);

    LeaveService leave;
    HrEmployeePortalAccessGrantRepository grants;
    EmployeeAuditService audit;
    TerminationFollowUp followUp;
    HrEmployee emp;

    @BeforeEach
    void setUp() {
        leave = mock(LeaveService.class);
        grants = mock(HrEmployeePortalAccessGrantRepository.class);
        audit = mock(EmployeeAuditService.class);
        followUp = new TerminationFollowUp(leave, grants, audit);
        emp = HrEmployee.create(tenant, "EMP-1", "Jane", "Dlamini", LocalDate.of(2024, 3, 1), "PERMANENT", new BigDecimal("26000"), "MONTHLY");
        when(leave.cancelAfterEmploymentEnds(any(), any(), any(), any())).thenReturn(new LeaveService.EndOfEmployment(0, 0));
        when(grants.findByTenantAndEmployee(any(), any())).thenReturn(List.of());
    }

    @Test
    void leaveAfterTheLastDayIsCancelledAndSaid() {
        emp.terminate(last, "Resigned", actor, "RESIGNATION", null);
        when(leave.cancelAfterEmploymentEnds(tenant, emp.getId(), last, actor)).thenReturn(new LeaveService.EndOfEmployment(2, 1));

        List<String> done = followUp.run(tenant, emp, actor);

        assertThat(done).anyMatch(s -> s.contains("2 leave requests after the last day were cancelled"));
        assertThat(done).anyMatch(s -> s.contains("runs past the last day"));
        verify(audit).record(eq(tenant), eq(emp.getId()), eq("LEAVE_CANCELLED"), any(), eq(actor));
    }

    @Test
    void portalAccessIsClosedForActiveAndPendingGrantsOnly() {
        emp.terminate(last, "Resigned", actor, "RESIGNATION", null);
        HrEmployeePortalAccessGrant active = HrEmployeePortalAccessGrant.createInvite(tenant.getValue(), emp.getId(), "jane@example.com", actor);
        HrEmployeePortalAccessGrant revoked = HrEmployeePortalAccessGrant.createInvite(tenant.getValue(), emp.getId(), "jane@example.com", actor);
        revoked.revoke(actor);
        when(grants.findByTenantAndEmployee(tenant.getValue(), emp.getId())).thenReturn(List.of(active, revoked));

        List<String> done = followUp.run(tenant, emp, actor);

        assertThat(active.getStatus()).isEqualTo("REVOKED");
        assertThat(done).anyMatch(s -> s.contains("portal access was closed"));
        verify(grants, times(1)).save(any());
    }

    @Test
    void nothingIsSaidWhenThereWasNothingToDoAndNoticeDoesNotApply() {
        emp.terminate(last, "Resigned", actor, "RESIGNATION", null);

        assertThat(followUp.run(tenant, emp, actor)).isEmpty();
    }

    @Test
    void noticePayIsWorkedOutWhenTheEmployerEndedItWithTooLittleNotice() {
        emp.terminate(last, "Retrenched", actor, "EMPLOYER_NOTICE", LocalDate.of(2026, 9, 1));

        assertThat(followUp.run(tenant, emp, actor)).anyMatch(s -> s.contains("12000.00"));
    }

    @Test
    void anUnrecordedEndingSaysNoticePayWasNotWorkedOut() {
        emp.terminate(last, "Left", actor);

        assertThat(followUp.run(tenant, emp, actor)).anyMatch(s -> s.contains("was not recorded"));
    }
}
